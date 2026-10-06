package com.dosecerta.alarm

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseState
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.ui.MainActivity
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** User denials are made through real Settings; initial grants are explicit QA setup. */
@RunWith(AndroidJUnit4::class)
class CapabilityUiInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val checker = ReminderCapabilityChecker(context)
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun find(id: String) = device.wait(Until.findObject(By.res(context.packageName, id)), 10_000)
        ?: throw AssertionError("Missing capability control: $id")
    private suspend fun setup() {
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        assumeTrue("This suite exercises modern real Settings", Build.VERSION.SDK_INT >= 34)
        device.wakeUp(); device.pressMenu()
        SettingsPreferences(context).setSetupCompleted()
        device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("cmd appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        device.executeShellCommand("cmd appops set ${context.packageName} USE_FULL_SCREEN_INTENT allow")
        assertTrue(checker.check().canNotifyAlarm && checker.check().exactAlarms && checker.check().fullScreenIntent)
    }
    private fun open(intent: Intent) { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); device.waitForIdle() }
    private fun toggle(value: Boolean) {
        val control = device.wait(Until.findObject(By.res("android", "switch_widget")), 10_000)
            ?: device.wait(Until.findObject(By.res("com.android.settings", "switch_widget")), 1000)
            // Recent Settings pages use Compose semantics without resource IDs.
            ?: device.findObjects(By.checkable(true)).singleOrNull()
            ?: run { capture("missing-settings-switch"); throw AssertionError("Real Settings switch missing or ambiguous") }
        if (control.isChecked != value) {
            if (control.className == "android.view.View") {
                // Compose merges the label and switch into one accessibility
                // row; the actual switch is at the trailing end of that row.
                val bounds = control.visibleBounds
                device.click(bounds.right - bounds.height() / 2, bounds.centerY())
            } else control.click()
        }
        device.waitForIdle()
        val changed = device.wait(Until.hasObject(By.checkable(true).checked(value)), 5000)
        if (!changed) capture("settings-switch-did-not-become-$value")
        assertTrue("Real Settings switch did not become $value", changed)
    }
    private fun capture(name: String) {
        val output = File(context.filesDir, "qa-capabilities").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(output, "$name.png")))
        device.dumpWindowHierarchy(File(output, "$name.xml"))
    }
    private fun returnToApp() {
        repeat(4) {
            device.pressBack()
            if (device.wait(Until.hasObject(By.res(context.packageName, "bottom_navigation")), 2000)) return
        }
        capture("settings-return-failure")
        throw AssertionError("Back from real Settings did not return to the app")
    }
    private suspend fun await(timeout: Long, condition: suspend () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout.coerceAtLeast(1000)
        while (!condition() && SystemClock.elapsedRealtime() < end) delay(50)
        assertTrue("Effective capability/result not observed", condition())
    }
    @Test fun userChannelBlockSurvivesEnsureAndSettingsResume() = runBlocking<Unit> {
        setup()
        val original = requireNotNull(manager.getNotificationChannel(ReminderChannels.ALARM))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            find("nav_settings").click()
            onView(withId(R.id.text_reminder_status)).perform(scrollTo())
            var blockedLabel = ""
            scenario.onActivity { blockedLabel = it.getString(R.string.reminder_notifications_blocked) }
            try {
                open(checker.notificationsSettings(ReminderChannels.ALARM)); toggle(false)
                await(5000) { !checker.check().alarmChannelEnabled }
                capture("channel-disabled-by-user")
                repeat(10) { ReminderChannels.ensure(context) }
                assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(ReminderChannels.ALARM)!!.importance)
                assertFalse(checker.check().canNotifyAlarm)
                returnToApp()
                onView(withId(R.id.text_reminder_status)).perform(scrollTo()).check(matches(withText(containsString(blockedLabel))))
                capture("settings-refreshed-channel-block")
            } finally {
                open(checker.notificationsSettings(ReminderChannels.ALARM)); toggle(true)
                await(5000) { checker.check().canNotifyAlarm }
                val restored = manager.getNotificationChannel(ReminderChannels.ALARM)!!
                assertEquals(original.sound, restored.sound)
                assertEquals(original.shouldVibrate(), restored.shouldVibrate())
                returnToApp()
            }
        }
    }
    @Test fun realFullScreenDenialUsesNotificationAndForegroundRecovery() = runBlocking<Unit> {
        setup()
        val repo = MedicationRepository(DoseCertaDatabase.getDatabase(context))
        val scheduler = AlarmScheduler(context)
        var medicationId: Long? = null
        var occurrenceId: String? = null
        var failure: Throwable? = null
        ActivityScenario.launch(MainActivity::class.java).use {
            find("bottom_navigation")
            try {
                open(checker.fullScreenSettings())
                toggle(true)
                await(5000) { checker.check().fullScreenIntent }
                capture("full-screen-access-settings-before-denial")
                toggle(false)
                await(5000) { !checker.check().fullScreenIntent }
                capture("full-screen-disabled-by-user")
                returnToApp()
                val now = System.currentTimeMillis()
                val due = ((now + 20_000) / 60_000 + 1) * 60_000
                val local = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
                val id = repo.insertMedication(Medication(name = "Synthetic FSI denial", dosage = "1", unit = "mg",
                    pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.DAILY, createdAt = now))
                medicationId = id
                val slotId = repo.insertSchedule(Schedule(medicationId = id, timeInMinutes = local.hour * 60 + local.minute,
                    daysOfWeek = emptyList(), validFrom = now))
                val scheduled = scheduler.scheduleAlarm(id, requireNotNull(repo.getScheduleById(slotId)))
                assertTrue(scheduled is ScheduleResult.Scheduled && scheduled.exact)
                occurrenceId = (scheduled as ScheduleResult.Scheduled).occurrenceId
                device.pressHome()
                await(due + 10_000 - System.currentTimeMillis()) { repo.getOccurrence(occurrenceId!!)?.deliveredAt != null }
                await(5000) { manager.activeNotifications.any { it.tag == AlarmIdentity.uri(occurrenceId!!, "notification").toString() } }
                val notification = manager.activeNotifications.single { it.tag == AlarmIdentity.uri(occurrenceId!!, "notification").toString() }.notification
                assertNull("Denied FSI must not be attached", notification.fullScreenIntent)
                assertEquals(DoseState.ALERTING, repo.getOccurrence(occurrenceId!!)?.state)
                assertFalse(device.hasObject(By.res(context.packageName, "button_silence")))
                capture("notification-fallback-no-card")
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                find("button_silence"); capture("foreground-card-after-user-open")
                find("button_skip").click()
                device.wait(Until.findObject(By.res("android", "button1")), 5000)!!.click()
                await(5000) { repo.getOccurrence(occurrenceId!!)?.state == DoseState.SKIPPED }
                File(context.filesDir, "qa-capabilities").resolve("fsi-denial-result.txt")
                    .writeText("Real Settings FSI denial; real AlarmManager delivery; no notification FSI; no background card; user launch presents card; persisted SKIPPED\n")
            } catch (error: Throwable) {
                failure = error
                capture("fsi-denial-failure")
                throw error
            } finally {
                occurrenceId?.let { scheduler.cancelOccurrence(it) }
                medicationId?.let { id -> scheduler.cancelAlarmsForMedication(id, repo.getSchedulesForMedicationSync(id)); repo.permanentlyDeleteMedication(id, true) }
                try {
                    open(checker.fullScreenSettings()); toggle(true)
                    await(5000) { checker.check().fullScreenIntent }
                    returnToApp()
                } catch (restoreError: Throwable) {
                    if (failure == null) throw restoreError else failure!!.addSuppressed(restoreError)
                }
            }
        }
    }
}
