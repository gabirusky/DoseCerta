package com.dosecerta.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.ui.MainActivity
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AlarmSafetyInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val database by lazy { DoseCertaDatabase.getDatabase(context) }
    private val repository by lazy { MedicationRepository(database) }
    private val scheduler by lazy { AlarmScheduler(context) }
    private val ids = mutableListOf<Long>()
    private var dedicated = false
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    @Before fun syntheticOnly() = runBlocking {
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        dedicated = true
        device.wakeUp(); device.pressMenu()
        if (Build.VERSION.SDK_INT >= 33) device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        if (Build.VERSION.SDK_INT >= 31) device.executeShellCommand("cmd appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        SettingsPreferences(context).setSetupCompleted()
    }
    @After fun cleanup() = runBlocking {
        if (!dedicated) return@runBlocking
        context.stopService(Intent(context, AlarmService::class.java))
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AlarmActivity>().forEach { it.finish() }
        }
        ids.forEach { id ->
            scheduler.cancelAlarmsForMedication(id, repository.getSchedulesForMedicationSync(id))
            repository.permanentlyDeleteMedication(id, true)
        }
    }
    private suspend fun occurrence(): MedicationLog {
        val now = System.currentTimeMillis()
        val due = now / 60_000 * 60_000
        val local = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
        val id = repository.insertMedication(Medication(name = "Synthetic stale safety ${System.nanoTime()}", dosage = "1", unit = "mg",
            pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.DAILY, createdAt = now - 3_600_000))
        ids += id
        val slot = repository.insertSchedule(Schedule(medicationId = id, timeInMinutes = local.hour * 60 + local.minute,
            daysOfWeek = emptyList(), validFrom = now - 3_600_000))
        return requireNotNull(repository.ensureOccurrence(slot, due))
    }
    private suspend fun await(timeout: Long = 15_000, condition: suspend () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < end) delay(50)
        assertTrue("Runtime condition did not become true", condition())
    }
    private fun since(start: Long) = AlarmDiagnostics.read(context).lines().filter {
        (it.substringBefore(' ').toLongOrNull() ?: 0) >= start
    }.joinToString("\n")
    private fun trail(name: String, start: Long) {
        File(context.filesDir, "qa-alarm-safety").apply { mkdirs() }
            .resolve("$name.txt").writeText(since(start))
    }
    @Test fun oldScheduledIntentDeliveredAfterArchiveCannotPlayOrReschedule() = runBlocking {
        val log = occurrence()
        val before = System.currentTimeMillis()
        val intent = AlarmIdentity.intent(context, MedicationAlarmReceiver::class.java, log.occurrenceId, "stale-fixture")
            .putExtra(AlarmIdentity.EXTRA_EXACT, true)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            // Real AlarmManager delivery intentionally retains an old handle.
            // The receiver must reject this persisted identity even after it arrives.
            context.getSystemService(AlarmManager::class.java).setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3000, pending)
            repository.archiveMedication(requireNotNull(log.medicationId))
            await { since(before).contains("stale_or_duplicate") }
            val current = requireNotNull(repository.getOccurrence(log.occurrenceId))
            assertEquals(DoseState.CANCELLED, current.state)
            assertNull(current.deliveredAt)
            assertFalse(manager.activeNotifications.any { it.tag == AlarmIdentity.uri(log.occurrenceId, "notification").toString() })
            assertFalse(since(before).contains("playing"))
            assertTrue(repository.getPendingOccurrences().none { it.medicationId == log.medicationId })
            trail("archived-handle", before)
        } finally { context.getSystemService(AlarmManager::class.java).cancel(pending); pending.cancel() }
    }
    @Test fun stoppedServiceCannotPlayWhenItsPendingDatabaseReadIsReleased() = runBlocking {
        val log = occurrence()
        assertTrue(DoseActionCoordinator(repository).deliver(log.occurrenceId) is DoseActionResult.Success)
        ActivityScenario.launch(MainActivity::class.java).use {
            // Main's foreground observer presents this delivered occurrence.
            // The real card is the foreground activity during the service test.
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "button_take")), 10_000))
            // Fill Room's four IO workers, then release them after real Service
            // destruction. No production delay or receiver invocation is added.
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            val before = System.currentTimeMillis()
            try {
                repeat(4) { database.queryExecutor.execute { entered.countDown(); release.await(8, TimeUnit.SECONDS) } }
                assertTrue("Room IO workers were not blocked", entered.await(3, TimeUnit.SECONDS))
                assertTrue(AlarmService.startOccurrence(context, log.occurrenceId))
                await(3000) { manager.activeNotifications.any { n -> n.id == AlarmService.FOREGROUND_NOTIFICATION_ID } }
                context.stopService(Intent(context, AlarmService::class.java))
                await(3000) { since(before).contains("service_destroy") }
            } finally { release.countDown() }
            delay(1500)
            assertFalse("Late IO restarted audio", since(before).contains("playing"))
            assertFalse(manager.activeNotifications.any { n -> n.id == AlarmService.FOREGROUND_NOTIFICATION_ID })
            assertEquals(DoseState.ALERTING, repository.getOccurrence(log.occurrenceId)?.state)
            trail("late-io-after-destroy", before)
        }
    }
}
