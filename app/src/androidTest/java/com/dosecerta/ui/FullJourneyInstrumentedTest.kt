package com.dosecerta.ui

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.dosecerta.R
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.alarm.AlarmDiagnostics
import com.dosecerta.alarm.ReminderCapabilityChecker
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseState
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Main evidence journey uses only visible UI grants and UI medicine creation. Database access is read-only. */
@RunWith(AndroidJUnit4::class)
class FullJourneyInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg get() = context.packageName
    private val arguments = InstrumentationRegistry.getArguments()
    private val evidenceRunId by lazy {
        (arguments.getString("evidenceRunId") ?: "manual-${System.currentTimeMillis()}").also {
            require(it.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Invalid evidenceRunId" }
        }
    }
    private val output by lazy { File(context.filesDir, "qa-journey/$evidenceRunId").apply { mkdirs() } }
    private val steps = JSONArray()
    private fun find(selector: BySelector, timeout: Long = 10_000): UiObject2 =
        device.wait(Until.findObject(selector), timeout) ?: throw AssertionError("Missing UI: $selector")
    private fun app(id: String) = By.res(pkg, id)
    private fun capture(stage: String) {
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val file = File(output, "$stage.png")
        assertTrue(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(output, "$stage.xml"))
        steps.put(JSONObject().put("stage", stage).put("timestampMillis", System.currentTimeMillis()).put("screenshot", file.name))
        File(output, "steps.json").writeText(steps.toString(2))
    }
    private fun preflight(flag: String) {
        assumeTrue("Opt-in only: -e $flag 1", arguments.getString(flag) == "1")
        assumeTrue("Only the dedicated synthetic QA AVD", SyntheticQaDevice.isDedicated(device))
        assertTrue("Set 24-hour format in the synthetic device fixture before recording", android.text.format.DateFormat.is24HourFormat(context))
        assertFalse("Start with a clean app data fixture through the guarded evidence script", runBlocking { SettingsPreferences(context).isSetupCompletedSync() })
    }
    private fun scrollTo(id: Int) {
        // Navigation follows asynchronous DataStore writes; Espresso's main-loop
        // idleness alone does not mean the destination has been attached yet.
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        var attached = false
        while (!attached && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                attached = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .any { activity -> activity.findViewById<android.view.View>(id)?.let { view ->
                        view.isAttachedToWindow && view.isShown && view.width > 0
                    } == true }
            }
            if (!attached) Thread.sleep(50)
        }
        assertTrue("Destination view did not attach: $id", attached)
        onView(withId(id)).perform(scrollTo())
    }
    private fun openApp() {
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
    private fun allowSpecialAccess(button: String, allowed: () -> Boolean) {
        if (allowed()) return
        scrollTo(if (button == "button_exact") R.id.button_exact else R.id.button_full_screen)
        find(app(button)).click()
        assertTrue("System access screen did not open", device.wait(Until.hasObject(By.pkg("com.android.settings")), 10_000))
        assertTrue("System access control did not load", device.wait(Until.hasObject(By.checkable(true)), 10_000))
        capture("settings-$button-before")
        val switches = device.findObjects(By.checkable(true))
        val toggle = switches.firstOrNull { !it.isChecked }
            ?: throw AssertionError("System access switch not found; inspect captured hierarchy")
        toggle.click()
        assertTrue(device.wait(Until.gone(By.res(pkg, button)), 1000) || allowed())
        val end = System.currentTimeMillis() + 5000
        while (!allowed() && System.currentTimeMillis() < end) Thread.sleep(100)
        assertTrue("Access was not granted by the visible system switch", allowed())
        capture("settings-$button-after")
        device.pressBack()
        scrollTo(R.id.button_skip)
        find(app("button_skip"))
    }
    private fun onboarding(denyNotification: Boolean = false) {
        openApp()
        scrollTo(R.id.checkbox_accept)
        find(app("checkbox_accept")).click()
        capture("01-terms")
        scrollTo(R.id.button_continue)
        find(app("button_continue")).click()
        scrollTo(R.id.button_allow)
        find(app("button_allow"))
        capture("02-access-before")
        val checker = ReminderCapabilityChecker(context)
        if (!checker.check().canNotifyAlarm) find(app("button_allow")).click()
        val permissionButton = if (denyNotification) "permission_deny_button" else "permission_allow_button"
        val permission = device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*permissioncontroller:id/$permissionButton"))), 4000)
        if (permission != null) { capture("03-system-notification-choice"); permission.click() }
        if (denyNotification) assertFalse(checker.check().canNotifyAlarm) else assertTrue(checker.check().canNotifyAlarm)
        allowSpecialAccess("button_exact") { checker.check().exactAlarms }
        if (!denyNotification) allowSpecialAccess("button_full_screen") { checker.check().fullScreenIntent }
        capture("04-access-effective")
        scrollTo(R.id.button_skip)
        find(app("button_skip")).click()
        repeat(3) { step -> scrollTo(R.id.button_action); find(app("button_action")).also { capture("05-tutorial-${step + 1}"); it.click() } }
        find(app("bottom_navigation"))
        assertTrue(runBlocking { SettingsPreferences(context).isSetupCompletedSync() })
        capture("06-home-empty")
    }
    private fun addFutureMedication(name: String, dates: List<Long>) {
        find(app("nav_medications")).click()
        find(app("fab_add")).click()
        onView(withId(R.id.edit_name)).perform(scrollTo(), replaceText(name), closeSoftKeyboard())
        onView(withId(R.id.edit_dosage)).perform(scrollTo(), replaceText("500"), closeSoftKeyboard())
        onView(withId(R.id.autoComplete_unit)).perform(scrollTo(), replaceText("mg"), closeSoftKeyboard())
        for (at in dates) {
            scrollTo(R.id.button_add_time)
            find(app("button_add_time")).click()
            find(By.res("android", "toggle_mode")).click()
            val local = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            find(By.res("android", "input_hour")).text = local.hour.toString()
            find(By.res("android", "input_minute")).text = local.minute.toString()
            find(By.res("android", "button1")).click()
        }
        scrollTo(R.id.button_preview)
        find(app("button_preview")).click()
        scrollTo(R.id.text_preview)
        capture("07-form-preview")
        find(app("button_save")).click()
        find(By.res("android", "button1"), 15_000).click()
        find(app("nav_home")).click()
        scrollTo(R.id.recycler_upcoming)
        assertTrue(find(app("text_name")).text.contains(name))
        capture("08-home-future-dose")
    }

    @Test fun firstUseFutureDoseRealAlarmAndPersistedOutcome() = runBlocking<Unit> {
        preflight("fullJourney")
        val action = arguments.getString("journeyAction") ?: "take"
        require(action in listOf("take", "skip", "snooze", "silence"))
        val count = (arguments.getString("journeyCount") ?: "1").toInt().also { require(it in 1..10) }
        require(count == 1 || action == "take")
        val name = "Medicamento fictício QA ${System.currentTimeMillis()}"
        val result = JSONObject().put("runId", evidenceRunId).put("scenario", action).put("count", count).put("medicine", name).put("passed", false)
            .put("secureKeyguard", context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure)
        val events = JSONArray()
        try {
            onboarding()
            val now = System.currentTimeMillis()
            val first = ((now + 150_000) / 60_000 + 1) * 60_000
            val dates = (0 until count).map { first + it * 60_000 }
            addFutureMedication(name, dates)
            val repo = MedicationRepository(DoseCertaDatabase.getDatabase(context))
            val med = repo.getAllActiveMedicationsSync().single { it.name == name }
            val schedules = repo.getSchedulesForMedicationSync(med.id)
            assertEquals(count, schedules.size)
            assertEquals(dates.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).let { local -> local.hour * 60 + local.minute } }.sorted(),
                schedules.map { it.timeInMinutes }.sorted())
            for ((index, due) in dates.withIndex()) {
                device.sleep()
                await(5000) { context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked }
                capture("09-locked-${index + 1}")
                // This assertion waits for actual scheduled delivery; it never starts card/receiver itself.
                await(due - System.currentTimeMillis() + 20_000) {
                    repo.getLogsForMedication(med.id).first().any { it.originalDueAt == due && it.deliveredAt != null }
                }
                // Home also has a legacy button_snooze ID for its skip action;
                // wait for the alarm-only control after confirmed receiver delivery.
                find(app("button_silence"), 15_000)
                capture("10-alarm-private-${index + 1}")
                assertEquals(context.getString(R.string.reminder_private_title), find(app("text_medication_name")).text)
                assertNull(device.findObject(app("button_take")))
                val original = repo.getLogsForMedication(med.id).first().single { it.originalDueAt == due }
                assertNotNull(original.deliveredAt)
                val delay = requireNotNull(original.deliveredAt) - due
                events.put(JSONObject().put("occurrenceId", original.occurrenceId).put("originalDueAt", due).put("deliveredAt", original.deliveredAt).put("delayMillis", delay))
                assertTrue("Nominal scheduled delivery exceeded 10 s: $delay", delay in 0..10_000)
                if (action in listOf("take", "skip")) {
                    find(app("button_unlock")).click()
                    arguments.getString("syntheticPin")?.let { pin ->
                        require(pin == "2468") { "Only the documented synthetic PIN is supported" }
                        find(By.res(java.util.regex.Pattern.compile(".*:id/(pinEntry|password_entry)")))
                        capture("11-credential-fixture-${index + 1}")
                        pin.forEach { digit -> device.pressKeyCode(android.view.KeyEvent.KEYCODE_0 + digit.digitToInt()) }
                        device.pressKeyCode(android.view.KeyEvent.KEYCODE_ENTER)
                    }
                    find(app("button_take"))
                    assertEquals(name, find(app("text_medication_name")).text)
                    capture("11-alarm-identified-${index + 1}")
                    find(app(if (action == "take") "button_take" else "button_skip")).click()
                    capture("12-explicit-confirmation-${index + 1}")
                    find(By.res("android", "button1")).click()
                    find(app("bottom_navigation"))
                    find(app("nav_history")).click()
                    find(app("recycler_logs"))
                    find(app("text_medication_name").textStartsWith(name))
                    capture("13-history-${index + 1}")
                    val log = repo.getOccurrence(original.occurrenceId)!!
                    assertEquals(if (action == "take") MedicationStatus.TAKEN else MedicationStatus.SKIPPED, log.status)
                    assertEquals(due, log.originalDueAt)
                    if (action == "take") assertTrue(requireNotNull(log.actualTime) >= requireNotNull(original.deliveredAt)) else assertNull(log.actualTime)
                    assertEquals(index + 1, repo.getLogsForMedication(med.id).first().count { it.status == log.status })
                    find(app("nav_home")).click()
                } else if (action == "snooze") {
                    find(app("button_snooze")).click()
                    capture("12-snooze-selection")
                    find(By.res("android", "button1")).click()
                    await(5000) { repo.getOccurrence(original.occurrenceId)?.state == DoseState.SNOOZED }
                    val log = repo.getOccurrence(original.occurrenceId)!!
                    assertEquals(due, log.originalDueAt); assertNotNull(log.snoozedUntil)
                    assertEquals(MedicationStatus.PENDING, log.status)
                } else {
                    find(app("button_silence")).click()
                    await(5000) { repo.getOccurrence(original.occurrenceId)?.state == DoseState.DISMISSED }
                    assertEquals(MedicationStatus.PENDING, repo.getOccurrence(original.occurrenceId)?.status)
                }
            }
            result.put("passed", true)
        } catch (error: Throwable) {
            result.put("failure", error.javaClass.simpleName).put("message", error.message)
            runCatching { capture("failure") }
            throw error
        } finally {
            result.put("events", events).put("completedAt", System.currentTimeMillis())
            File(output, "result.json").writeText(result.toString(2))
            File(output, "alarm-diagnostics.txt").writeText(AlarmDiagnostics.read(context))
        }
    }

    @Test fun deniedNotificationIsVisibleAndDoesNotClaimAlarmAudio() = runBlocking<Unit> {
        preflight("deniedJourney")
        val result = JSONObject().put("runId", evidenceRunId).put("scenario", "denied-notifications").put("passed", false)
        try {
            onboarding(denyNotification = true)
            val due = ((System.currentTimeMillis() + 90_000) / 60_000 + 1) * 60_000
            val name = "Medicamento fictício negativa QA ${System.currentTimeMillis()}"
            addFutureMedication(name, listOf(due))
            val repo = MedicationRepository(DoseCertaDatabase.getDatabase(context))
            val med = repo.getAllActiveMedicationsSync().single { it.name == name }
            device.sleep()
            await(due - System.currentTimeMillis() + 20_000) { repo.getLogsForMedication(med.id).first().any { it.deliveredAt != null } }
            assertFalse(ReminderCapabilityChecker(context).check().canNotifyAlarm)
            assertFalse(AlarmDiagnostics.read(context).contains("playing"))
            device.wakeUp(); device.pressMenu()
            find(app("nav_settings")).click()
            scrollTo(R.id.text_reminder_status)
            capture("denied-effective-settings")
            assertTrue(find(app("text_reminder_status")).text.contains(context.getString(R.string.reminder_notifications_blocked)))
            result.put("passed", true)
        } finally { File(output, "result.json").writeText(result.toString(2)); File(output, "alarm-diagnostics.txt").writeText(AlarmDiagnostics.read(context)) }
    }
    private suspend fun await(timeout: Long, condition: suspend () -> Boolean) {
        val end = System.currentTimeMillis() + timeout.coerceAtLeast(1000)
        while (!condition() && System.currentTimeMillis() < end) delay(200)
        assertTrue(condition())
    }
}
