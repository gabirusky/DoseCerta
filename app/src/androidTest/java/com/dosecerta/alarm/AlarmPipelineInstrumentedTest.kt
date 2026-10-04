package com.dosecerta.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.domain.DoseState
import com.dosecerta.util.SettingsPreferences
import com.dosecerta.qa.SyntheticQaDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Opt-in runtime test: uses real AlarmManager delivery on the disposable QA AVD only. */
@RunWith(AndroidJUnit4::class)
class AlarmPipelineInstrumentedTest {
    @Test fun actualAlarmDeliveryKeepsConcurrentOccurrencesIndependent() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue("Enable explicitly with -e alarmPipeline 1 on the QA image", InstrumentationRegistry.getArguments().getString("alarmPipeline") == "1")
        assumeTrue("Never changes grants or medicine data on a personal device", SyntheticQaDevice.isDedicated(device))
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        if (Build.VERSION.SDK_INT >= 31) device.executeShellCommand("cmd appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        if (Build.VERSION.SDK_INT >= 34) device.executeShellCommand("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT allow")
        assertTrue(ReminderCapabilityChecker(context).check().canNotifyAlarm)
        assertTrue(ReminderCapabilityChecker(context).check().exactAlarms)
        val database = DoseCertaDatabase.getDatabase(context)
        val repository = MedicationRepository(database)
        val scheduler = AlarmScheduler(context)
        val coordinator = DoseActionCoordinator(repository)
        val preferences = SettingsPreferences(context)
        preferences.saveShowMedicationOnLockScreen(false)
        val seed = UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        // Leave at least twenty seconds for setup before the next real wall-clock slot.
        val due = ((now + 20_000) / 60_000 + 1) * 60_000
        val local = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
        val minute = local.hour * 60 + local.minute
        val created = mutableListOf<Long>()
        val occurrences = mutableListOf<String>()
        try {
            repeat(2) { index ->
                val id = repository.insertMedication(Medication(name = "QA pipeline $seed $index", dosage = "1", unit = "un", pharmaceuticalForm = PharmaceuticalForm.TABLET,
                    frequency = Frequency.DAILY, color = 0xff136e87.toInt(), createdAt = now))
                created += id
                val scheduleId = repository.insertSchedule(Schedule(medicationId = id, timeInMinutes = minute, daysOfWeek = emptyList(), validFrom = now))
                val schedule = requireNotNull(repository.getScheduleById(scheduleId))
                val scheduled = scheduler.scheduleAlarm(id, schedule)
                assertTrue(scheduled is ScheduleResult.Scheduled && scheduled.exact)
                val occurrence = requireNotNull(repository.ensureOccurrence(scheduleId, due))
                occurrences += occurrence.occurrenceId
                assertNotNull(repository.getOccurrence(occurrence.occurrenceId)?.schedulerHandle)
            }
            assertNotEquals(AlarmIdentity.uri(occurrences[0], AlarmIdentity.RECURRENCE), AlarmIdentity.uri(occurrences[1], AlarmIdentity.RECURRENCE))
            device.pressHome()
            await(due + 15_000 - System.currentTimeMillis()) { occurrences.all { repository.getOccurrence(it)?.deliveredAt != null } }
            occurrences.forEach { assertEquals(DoseState.ALERTING, repository.getOccurrence(it)?.state) }
            val manager = context.getSystemService(NotificationManager::class.java)
            await(10_000) { occurrences.all { id -> manager.activeNotifications.any { it.tag == AlarmIdentity.uri(id, "notification").toString() } } }
            occurrences.forEach { id ->
                val notification = manager.activeNotifications.single { it.tag == AlarmIdentity.uri(id, "notification").toString() }.notification
                assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
                assertNotNull(notification.publicVersion)
                assertFalse(notification.publicVersion.extras.getString(android.app.Notification.EXTRA_TITLE).orEmpty().contains("QA pipeline"))
            }
            val take = coordinator.take(occurrences[0])
            assertTrue(take is DoseActionResult.Success)
            scheduler.cancelOccurrence(occurrences[0])
            await(5000) { manager.activeNotifications.none { it.tag == AlarmIdentity.uri(occurrences[0], "notification").toString() } }
            assertTrue(manager.activeNotifications.any { it.tag == AlarmIdentity.uri(occurrences[1], "notification").toString() })
            assertEquals(DoseState.ALERTING, repository.getOccurrence(occurrences[1])?.state)
            assertTrue(coordinator.skip(occurrences[0]) is DoseActionResult.Rejected)
            assertTrue(coordinator.take(occurrences[0]) is DoseActionResult.Success)
            // The second independent occurrence owns the remaining queue audio, then stops itself.
            await(com.dosecerta.domain.DosePolicy.MAX_SOUND_MILLIS + 15_000) { repository.getOccurrence(occurrences[1])?.state == DoseState.DISMISSED }
            await(5000) { manager.activeNotifications.none { it.id == AlarmService.FOREGROUND_NOTIFICATION_ID } }
            assertEquals(com.dosecerta.data.model.MedicationStatus.PENDING, repository.getOccurrence(occurrences[1])?.status)
            val snooze = scheduler.snoozeOccurrence(occurrences[1], 10)
            assertTrue(snooze is DoseActionResult.Success)
            val snoozed = requireNotNull(repository.getOccurrence(occurrences[1]))
            assertEquals(due, snoozed.originalDueAt)
            assertEquals(DoseState.SNOOZED, snoozed.state)
            assertNotNull(snoozed.snoozedUntil)
            assertTrue(repository.getPendingOccurrences().count { it.medicationId == created[1] } >= 2)
            scheduler.reconcile()
            assertEquals(snoozed.snoozedUntil, repository.getOccurrence(occurrences[1])?.snoozedUntil)
            assertTrue(repository.getOccurrence(occurrences[1])?.schedulerHandle?.contains("snooze:") == true)
            // Exact-access revocation may kill the target process. Drive it externally between runs.
            val trail = AlarmDiagnostics.read(context)
            assertFalse(trail.contains("QA pipeline"))
            assertTrue(trail.contains("receiver"))
            assertTrue(trail.contains("notification"))
            assertTrue(trail.contains("service_validate"))
        } finally {
            occurrences.forEach { scheduler.cancelOccurrence(it) }
            created.forEach { id ->
                val schedules = repository.getSchedulesForMedicationSync(id)
                scheduler.cancelAlarmsForMedication(id, schedules)
                repository.permanentlyDeleteMedication(id, true)
            }
            if (Build.VERSION.SDK_INT >= 31) device.executeShellCommand("cmd appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        }
    }

    @Test fun pendingIntentUrisKeepAllLongIdsAndKindsDistinct() {
        val ids = listOf("dose:9223372036854775807:1:2026-09-30T08:00", "dose:2147483647:1:2026-09-30T08:00", "dose:9223372036854775807:1:2026-10-01T08:00")
        val kinds = listOf(AlarmIdentity.RECURRENCE, AlarmIdentity.SNOOZE, AlarmIdentity.TIMEOUT, AlarmIdentity.FOLLOW_UP, "card", "take", "skip")
        assertEquals(ids.size * kinds.size, ids.flatMap { id -> kinds.map { AlarmIdentity.uri(id, it).toString() } }.toSet().size)
        ids.forEach { assertEquals(it, AlarmIdentity.uri(it, AlarmIdentity.RECURRENCE).pathSegments[0]) }
    }

    private suspend fun await(timeout: Long, condition: suspend () -> Boolean) {
        val end = System.currentTimeMillis() + timeout.coerceAtLeast(1000)
        while (!condition() && System.currentTimeMillis() < end) delay(200)
        assertTrue("Condition did not become true before the timeout", condition())
    }
}
