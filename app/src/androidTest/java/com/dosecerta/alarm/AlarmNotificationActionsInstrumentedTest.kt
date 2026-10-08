package com.dosecerta.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.domain.DoseState
import com.dosecerta.notification.NotificationHelper
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Exercises the actual notification PendingIntents, including persistence under keyguard. */
@RunWith(AndroidJUnit4::class)
class AlarmNotificationActionsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val repository by lazy { MedicationRepository(DoseCertaDatabase.getDatabase(context)) }
    private val scheduler by lazy { AlarmScheduler(context) }
    private val preferences by lazy { SettingsPreferences(context) }
    private val medications = mutableListOf<Long>()
    private var dedicated = false
    private var oldDetails = true

    @Before fun syntheticOnly() = runBlocking {
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        dedicated = true
        device.wakeUp()
        device.pressMenu()
        oldDetails = preferences.getShowMedicationOnLockScreenSync()
        // Even hiding notification details must not prevent an explicit dose action.
        preferences.saveShowMedicationOnLockScreen(false)
    }

    @After fun cleanup() = runBlocking {
        if (!dedicated) return@runBlocking
        device.wakeUp()
        device.pressMenu()
        medications.forEach { id ->
            scheduler.cancelAlarmsForMedication(id, repository.getSchedulesForMedicationSync(id))
            repository.permanentlyDeleteMedication(id, true)
        }
        preferences.saveShowMedicationOnLockScreen(oldDetails)
    }

    private suspend fun occurrence(): MedicationLog {
        val now = System.currentTimeMillis()
        val due = now / 60_000 * 60_000
        val local = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
        val medicationId = repository.insertMedication(Medication(
            name = "Synthetic notification ${UUID.randomUUID()}", dosage = "1", unit = "mg",
            pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.DAILY,
            color = 0xfff77f00.toInt(), createdAt = now - 3_600_000
        ))
        medications += medicationId
        val scheduleId = repository.insertSchedule(Schedule(
            medicationId = medicationId, timeInMinutes = local.hour * 60 + local.minute,
            daysOfWeek = emptyList(), validFrom = now - 3_600_000
        ))
        val log = requireNotNull(repository.ensureOccurrence(scheduleId, due))
        assertTrue(DoseActionCoordinator(repository).deliver(log.occurrenceId) is DoseActionResult.Success)
        return requireNotNull(repository.getOccurrence(log.occurrenceId))
    }

    @Test fun allThreeDoseActionsAreAvailableAndUseTheSelectedColor() = runBlocking {
        val log = occurrence()
        val notification = NotificationHelper(context).buildAlarm(log, true, false)
        assertEquals(log.snapshotColor, notification.color)
        assertEquals(listOf(
            context.getString(R.string.notification_action_take),
            context.getString(R.string.notification_action_skip),
            context.getString(R.string.reminder_snooze_ten)
        ), notification.actions.map { it.title.toString() })
        assertNotEquals(notification.actions[0].actionIntent, notification.actions[1].actionIntent)
        assertNotEquals(notification.actions[1].actionIntent, notification.actions[2].actionIntent)
    }

    @Test fun lockedNotificationActionsPersistWithoutAuthenticationOrChangingAnotherDose() = runBlocking {
        val logs = List(3) { occurrence() }
        val notifications = logs.map { NotificationHelper(context).buildAlarm(it, true, false) }
        device.pressHome()
        device.sleep()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        await { keyguard.isKeyguardLocked }

        notifications[0].actions[0].actionIntent.send()
        await { repository.getOccurrence(logs[0].occurrenceId)?.state == DoseState.TAKEN }
        assertEquals(DoseState.ALERTING, repository.getOccurrence(logs[1].occurrenceId)?.state)
        val actualTime = repository.getOccurrence(logs[0].occurrenceId)?.actualTime
        notifications[0].actions[0].actionIntent.send()
        delay(300)
        assertEquals(actualTime, repository.getOccurrence(logs[0].occurrenceId)?.actualTime)

        notifications[1].actions[1].actionIntent.send()
        await { repository.getOccurrence(logs[1].occurrenceId)?.state == DoseState.SKIPPED }
        assertEquals(DoseState.ALERTING, repository.getOccurrence(logs[2].occurrenceId)?.state)
        notifications[2].actions[2].actionIntent.send()
        await { repository.getOccurrence(logs[2].occurrenceId)?.state == DoseState.SNOOZED }
        val snoozed = requireNotNull(repository.getOccurrence(logs[2].occurrenceId))
        assertEquals(logs[2].originalDueAt, snoozed.originalDueAt)
        assertNotNull(snoozed.snoozedUntil)
        assertTrue("Dose actions must leave the keyguard locked", keyguard.isKeyguardLocked)
    }

    private suspend fun await(condition: suspend () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < end) delay(50)
        assertTrue("Notification action did not finish", condition())
    }
}
