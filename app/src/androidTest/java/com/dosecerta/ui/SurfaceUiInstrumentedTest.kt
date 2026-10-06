package com.dosecerta.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
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
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Complementary empty/error/dialog states, on an explicitly clean synthetic installation. */
@RunWith(AndroidJUnit4::class)
class SurfaceUiInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val repo by lazy { MedicationRepository(DoseCertaDatabase.getDatabase(context)) }
    private val prefs by lazy { SettingsPreferences(context) }
    private val ids = mutableListOf<Long>()
    private val customLogs = mutableListOf<MedicationLog>()
    private var oldScale = "1.0"
    private var oldLanguage = "pt"
    private var oldLocales = LocaleListCompat.getEmptyLocaleList()
    private var oldPrivacy = false
    private var oldSound: String? = null
    private var oldHours = 2
    private var oldHardwareIme = "null"
    private var originalRead = false
    private var currentStrings = context
    private fun find(id: String) = device.wait(Until.findObject(By.res(context.packageName, id)), 10_000)
        ?: run { capture("missing-control-$id"); throw AssertionError("Missing surface control: $id") }
    private fun text(res: Int) = currentStrings.getString(res)
    private fun label(res: Int) = device.wait(Until.findObject(By.text(text(res))), 10_000)
        ?: run { capture("missing-label-$res"); throw AssertionError("Missing surface label: ${text(res)}") }
    private fun editHistory() = device.wait(Until.findObject(By.desc(text(R.string.report_edit_record))), 10_000)
        ?: run { capture("missing-history-action"); throw AssertionError("History edit action must remain available") }
    private fun capture(name: String) {
        device.waitForIdle()
        val output = File(context.filesDir, "qa-surfaces").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(output, "$name.png")))
        device.dumpWindowHierarchy(File(output, "$name.xml"))
    }
    @Before fun syntheticOnly() = runBlocking {
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        assertTrue("Use --clean; existing medicines must never be deleted by this audit", repo.getAllActiveMedicationsSync().isEmpty())
        device.wakeUp(); device.pressMenu()
        oldScale = device.executeShellCommand("settings get system font_scale").trim()
        oldLanguage = prefs.selectedLanguage.first(); oldLocales = AppCompatDelegate.getApplicationLocales()
        oldPrivacy = prefs.getShowMedicationOnLockScreenSync(); oldSound = prefs.getAlarmSoundUriSync()
        oldHours = prefs.missedReminderHours.first()
        oldHardwareIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        originalRead = true
        prefs.setSetupCompleted(); prefs.saveLanguage("pt")
        device.executeShellCommand("settings put system font_scale 2.0")
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pt-BR")) }
    }
    @After fun restore() = runBlocking {
        if (!originalRead) return@runBlocking
        ids.forEach { id ->
            AlarmScheduler(context).cancelAlarmsForMedication(id, repo.getSchedulesForMedicationSync(id))
            repo.permanentlyDeleteMedication(id, true)
        }
        customLogs.forEach { repo.deleteLog(it) }
        prefs.saveLanguage(oldLanguage); prefs.saveShowMedicationOnLockScreen(oldPrivacy); prefs.saveAlarmSoundUri(oldSound)
        prefs.saveMissedReminderHours(oldHours)
        device.executeShellCommand(if (oldHardwareIme.toIntOrNull() == null) "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $oldHardwareIme")
        device.executeShellCommand(if (oldScale.toFloatOrNull() == null) "settings delete system font_scale" else "settings put system font_scale $oldScale")
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(oldLocales) }
    }
    private fun launch() = ActivityScenario.launch(MainActivity::class.java).also {
        find("bottom_navigation")
        // The first AppCompat activity can finish synchronizing framework
        // locales after test setup. Apply PT with a live delegate, then assert
        // the actual Activity resources used by all label lookups.
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pt-BR")) }
        find("bottom_navigation")
        it.onActivity { activity ->
            assertEquals("pt", activity.resources.configuration.locales[0].language)
            currentStrings = activity
        }
    }
    private fun navigate(scenario: ActivityScenario<MainActivity>, id: Int, marker: String) {
        scenario.onActivity { (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController.navigate(id) }
        find(marker); device.waitForIdle()
    }
    private suspend fun seed(name: String): Long = repo.insertMedication(Medication(name = name, dosage = "500", unit = "mg",
        pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.AS_NEEDED, notes = "Fixture sintética")).also { ids += it }
    private suspend fun await(condition: suspend () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < end) delay(50)
        assertTrue(condition())
    }
    @Test fun emptyAndLongExtraDoseListsHaveVisibleErrorsAndExplicitConfirmation() = runBlocking<Unit> {
        launch().use { scenario ->
            find("text_no_medications"); capture("home-empty")
            onView(withId(R.id.button_register_extra_dose)).perform(scrollTo(), click())
            onView(withId(R.id.text_empty)).perform(scrollTo()).check(matches(withText(text(R.string.extra_dose_no_medications))))
            capture("extra-empty")
            onView(withId(R.id.button_add_custom)).perform(click())
            label(R.string.extra_dose_error_empty); capture("extra-name-error")
            val customName = "Dose avulsa sintética ${System.nanoTime()}"
            onView(withId(R.id.edit_custom_medication)).perform(replaceText(customName), click())
            lateinit var input: android.view.View
            onView(withId(R.id.edit_custom_medication)).check { view, error ->
                if (error != null) throw error
                input = view
            }
            instrumentation.runOnMainSync {
                input.requestFocus()
                input.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            var imeVisible = false
            await {
                instrumentation.runOnMainSync {
                    imeVisible = ViewCompat.getRootWindowInsets(input)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                imeVisible
            }
            onView(withId(R.id.button_add_custom)).perform(scrollTo()).check(matches(isCompletelyDisplayed()))
            capture("extra-keyboard"); onView(withId(R.id.edit_custom_medication)).perform(closeSoftKeyboard())
            find("button_add_custom").click(); findSystem("button1"); capture("extra-explicit-confirmation")
            findSystem("button2").click()
            find("button_add_custom").click()
            val confirm = findSystem("button1").visibleBounds
            device.click(confirm.centerX(), confirm.centerY()); device.click(confirm.centerX(), confirm.centerY())
            await { repo.getAllLogs().first().count { it.customMedicationName == customName } == 1 }
            customLogs += repo.getAllLogs().first().filter { it.customMedicationName == customName }
            assertEquals(1, customLogs.size)
            assertTrue(customLogs.single().isExtraDose)
            assertEquals(MedicationStatus.TAKEN, customLogs.single().status)
            find("bottom_navigation"); capture("extra-double-tap-single-record")
            repeat(50) { seed("Medicamento sintético ${it.toString().padStart(2, '0')}") }
            onView(withId(R.id.button_register_extra_dose)).perform(scrollTo(), click())
            onView(withId(R.id.recycler_medications)).perform(scrollTo())
            find("recycler_medications"); capture("extra-long-list")
            onView(withId(R.id.button_add_custom)).perform(scrollTo()).check(matches(isCompletelyDisplayed()))
            findSystem("button2").click()
            navigate(scenario, R.id.nav_medications, "search_view"); capture("medications-long-list")
        }
    }
    private fun findSystem(id: String) = device.wait(Until.findObject(By.res("android", id)), 10_000)
        ?: throw AssertionError("Missing native control: $id")
    @Test fun searchManagementAndFormErrorsRemainDiscoverableAtLargeFont() = runBlocking<Unit> {
        launch().use { scenario ->
            navigate(scenario, R.id.nav_medications, "search_view")
            label(R.string.medications_empty); capture("medications-empty")
            // Search on an already empty list must change its explanation even
            // if the filtered StateFlow still contains the same empty list.
            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("nenhum resultado sintético"), closeSoftKeyboard())
            label(R.string.ui_no_results); capture("medications-no-results")
            seed("QA grande de medicamento sintético com nome bastante longo para quebra em várias linhas")
            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("QA grande"), closeSoftKeyboard())
            find("text_medication_name"); capture("medications-search-long-name")
            find("button_delete").click(); capture("medication-management")
            label(R.string.ui_archive).click(); findSystem("button1"); capture("medication-archive-confirmation")
            findSystem("button2").click()
            find("button_delete").click(); label(R.string.ui_delete_permanent).click()
            findSystem("button1"); capture("medication-delete-history-choice")
            findSystem("button1").click(); findSystem("button2"); capture("medication-delete-warning")
            findSystem("button2").click()
            navigate(scenario, R.id.nav_add_medication, "edit_name")
            find("button_save").click(); label(R.string.ui_name_required); capture("form-field-error")
            onView(withId(R.id.edit_name)).perform(closeSoftKeyboard())
        }
    }
    @Test fun historyOptionsSettingsAndPrivacyUseExplicitControls() = runBlocking<Unit> {
        val id = seed("QA histórico sintético")
        val now = System.currentTimeMillis()
        listOf(MedicationStatus.TAKEN, MedicationStatus.SKIPPED, MedicationStatus.MISSED).forEachIndexed { index, status ->
            repo.insertLog(MedicationLog(medicationId = id, scheduleId = null, occurrenceId = "qa-surface:${System.nanoTime()}",
                scheduledTime = now - index * 60_000, status = status, actualTime = if (status == MedicationStatus.TAKEN) now else null, isExtraDose = true))
        }
        launch().use { scenario ->
            navigate(scenario, R.id.nav_history, "recycler_logs")
            find("text_medication_name"); capture("history-all-statuses")
            onView(withId(R.id.chip_skipped)).perform(scrollTo(), click()); capture("history-filtered")
            editHistory().click(); capture("history-explicit-options")
            label(R.string.history_mark_taken).click(); findSystem("button1"); capture("history-taken-date")
            findSystem("button1").click(); findSystem("button2"); capture("history-taken-time")
            findSystem("button2").click()
            editHistory().click()
            label(R.string.history_delete_log).click(); findSystem("button2"); capture("history-delete-confirmation")
            findSystem("button2").click()
            navigate(scenario, R.id.nav_settings, "text_settings_title")
            onView(withId(R.id.switch_lock_details)).perform(scrollTo(), click())
            await { prefs.getShowMedicationOnLockScreenSync() != oldPrivacy }
            onView(withId(R.id.slider_missed_reminder_hours)).perform(scrollTo(), click())
            if (oldHours < 24) device.pressDPadRight() else device.pressDPadLeft()
            await { prefs.missedReminderHours.first() != oldHours }
            val updatedHours = prefs.missedReminderHours.first()
            capture("settings-privacy-choice")
            navigate(scenario, R.id.nav_home, "text_greeting")
            navigate(scenario, R.id.nav_settings, "text_settings_title")
            onView(withId(R.id.switch_lock_details)).perform(scrollTo()).check(matches(if (oldPrivacy) isNotChecked() else isChecked()))
            scenario.onActivity {
                assertEquals(updatedHours.toFloat(), it.findViewById<com.google.android.material.slider.Slider>(R.id.slider_missed_reminder_hours).value, 0.01f)
            }
            prefs.saveAlarmSoundUri("content://com.dosecerta.synthetic-missing/sound")
            await { prefs.getAlarmSoundUriSync() == null }
            onView(withId(R.id.card_alarm_sound)).perform(scrollTo()); capture("settings-invalid-sound-recovered")
            onView(withId(R.id.button_diagnostics)).perform(scrollTo(), click()); findSystem("button1"); capture("settings-diagnostics")
            findSystem("button1").click()
            onView(withId(R.id.text_app_version)).perform(scrollTo()); capture("settings-about")
            onView(withId(R.id.card_privacy_policy)).perform(scrollTo(), click())
            find("text_privacy_policy"); capture("privacy-policy")
        }
    }
}
