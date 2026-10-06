package com.dosecerta.ui

import android.graphics.Rect
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.*
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
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Real layouts, synthetic records only. Measures how many complete cards fit above navigation. */
@RunWith(AndroidJUnit4::class)
class CompactUiInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val output get() = File(context.filesDir, "qa-compact").apply { mkdirs() }
    private val measurements = JSONArray()

    private fun capture(name: String) {
        device.waitForIdle()
        // Let native navigation and Material ripples finish before reviewing pixels.
        android.os.SystemClock.sleep(400)
        assertTrue(device.takeScreenshot(File(output, "$name.png")))
        device.dumpWindowHierarchy(File(output, "$name.xml"))
    }

    private fun navigate(scenario: ActivityScenario<MainActivity>, destination: Int, marker: String) {
        scenario.onActivity {
            (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController.navigate(destination)
        }
        assertTrue(device.wait(Until.hasObject(By.res(context.packageName, marker)), 10000))
        device.waitForIdle()
    }

    private fun measureCards(scenario: ActivityScenario<MainActivity>, screen: String, compact: Boolean) {
        scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.recycler_medications)
            val density = activity.resources.displayMetrics.density
            val heights = JSONArray()
            var fullyVisible = 0
            for (index in 0 until list.childCount) {
                val child = list.getChildAt(index)
                val bounds = Rect()
                heights.put(child.height / density)
                if (child.getGlobalVisibleRect(bounds) && bounds.height() == child.height) fullyVisible++
            }
            measurements.put(JSONObject().put("screen", screen).put("cardHeightsDp", heights)
                .put("fullyVisibleCards", fullyVisible).put("fontScale", activity.resources.configuration.fontScale)
                .put("screenWidthDp", activity.resources.configuration.screenWidthDp))
            if (compact && activity.resources.configuration.screenWidthDp >= 360 &&
                activity.resources.configuration.screenHeightDp >= 550) {
                assertTrue("$screen should show at least four complete cards; got $fullyVisible", fullyVisible >= 4)
            }
        }
    }

    @Test fun populatedScreensRemainCompactAndEditingWorksAcrossThemesAndFontSizes() = runBlocking<Unit> {
        assumeTrue("Dedicated synthetic AVD required", SyntheticQaDevice.isDedicated(device))
        val repo = MedicationRepository(DoseCertaDatabase.getDatabase(context))
        val preferences = SettingsPreferences(context)
        val oldLanguage = preferences.selectedLanguage.first()
        val oldLocales = AppCompatDelegate.getApplicationLocales()
        val oldMode = AppCompatDelegate.getDefaultNightMode()
        val oldScale = device.executeShellCommand("settings get system font_scale").trim()
        val ids = mutableListOf<Long>()
        val now = LocalTime.now()
        val currentMinute = now.hour * 60 + now.minute
        assumeTrue("Visual fixtures need a three-minute window before midnight", currentMinute < 1437)
        val firstMinute = (currentMinute + 30).coerceAtMost(1439)
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        try {
            preferences.setSetupCompleted()
            listOf("Vitamina D", "Vitamina C", "Ômega 3", "Vitamina B12").forEachIndexed { index, name ->
                val medication = Medication(name = name, dosage = listOf("2000", "500", "1000", "1000")[index],
                    unit = if (index == 0) "UI" else "mg", pharmaceuticalForm = PharmaceuticalForm.TABLET,
                    frequency = Frequency.DAILY, notes = "Fixture sintética para revisão visual",
                    color = listOf(0xFF25685B.toInt(), 0xFF8A6430.toInt(), 0xFF486580.toInt(), 0xFF80648D.toInt())[index])
                val id = repo.insertMedication(medication); ids += id
                // Keep every fixture in the future, even late in the evening. An alarm
                // opening over the app invalidates both screenshots and visibility checks.
                val minute = (firstMinute + index * 5).coerceAtMost(1439)
                val schedule = Schedule(medicationId = id, timeInMinutes = minute, daysOfWeek = emptyList(),
                    validFrom = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val scheduleId = repo.insertSchedule(schedule)
                val due = today.minusDays(1).atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
                repo.insertLog(MedicationLog(medicationId = id, scheduleId = scheduleId, scheduledTime = due,
                    status = if (index == 2) MedicationStatus.SKIPPED else MedicationStatus.TAKEN,
                    actualTime = if (index == 2) null else due + 60_000))
            }
            ids += repo.insertMedication(Medication(name = "Paracetamol", dosage = "750", unit = "mg",
                pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.AS_NEEDED,
                notes = "Fixture sintética para revisão visual", color = 0xFF80648D.toInt()))
            data class Appearance(val name: String, val language: String, val night: Int, val scale: Float)
            val appearances = if (InstrumentationRegistry.getArguments().getString("fullUiMatrix") == "true") {
                listOf(1f, 1.3f, 2f).flatMap { scale -> listOf("pt-BR", "en").flatMap { language ->
                    listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES).map { night ->
                        Appearance("${if (night == AppCompatDelegate.MODE_NIGHT_YES) "dark" else "light"}-${language}-${(scale * 100).toInt()}",
                            language, night, scale)
                    }
                } }
            } else listOf(
                Appearance("light-pt", "pt-BR", AppCompatDelegate.MODE_NIGHT_NO, 1f),
                Appearance("dark-pt", "pt-BR", AppCompatDelegate.MODE_NIGHT_YES, 1f),
                Appearance("light-en", "en", AppCompatDelegate.MODE_NIGHT_NO, 1f),
                Appearance("large-pt", "pt-BR", AppCompatDelegate.MODE_NIGHT_NO, 2f)
            )
            for (appearance in appearances) {
                device.executeShellCommand("settings put system font_scale ${appearance.scale}")
                preferences.saveLanguage(if (appearance.language.startsWith("pt")) "pt" else "en")
                instrumentation.runOnMainSync {
                    AppCompatDelegate.setDefaultNightMode(appearance.night)
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appearance.language))
                }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "bottom_navigation")), 10000))
                    instrumentation.runOnMainSync {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(appearance.language))
                    }
                    assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "text_medication_name")), 10000))
                    scenario.onActivity {
                        assertEquals(appearance.scale, it.resources.configuration.fontScale, 0.05f)
                        assertEquals(appearance.language.substringBefore('-'), it.resources.configuration.locales[0].language)
                    }
                    capture("${appearance.name}-home")
                    measureCards(scenario, "${appearance.name}-home", appearance.scale == 1f)
                    navigate(scenario, R.id.nav_medications, "search_view")
                    assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "text_medication_name")), 10000))
                    capture("${appearance.name}-medications")
                    measureCards(scenario, "${appearance.name}-medications", appearance.scale == 1f)
                    // Opening a card is the compact list's primary edit action.
                    val name = device.findObjects(By.res(context.packageName, "text_medication_name")).first()
                    val expectedName = name.text
                    name.click()
                    assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "edit_name")), 10000))
                    onView(withId(R.id.edit_name)).check(matches(withText(expectedName)))
                    onView(withId(R.id.button_save)).check(matches(isCompletelyDisplayed()))
                    capture("${appearance.name}-edit")
                    navigate(scenario, R.id.nav_history, "recycler_logs"); capture("${appearance.name}-history")
                    navigate(scenario, R.id.nav_settings, "text_settings_title"); capture("${appearance.name}-settings")
                    navigate(scenario, R.id.nav_add_medication, "edit_name")
                    onView(withId(R.id.button_save)).check(matches(isCompletelyDisplayed()))
                    capture("${appearance.name}-form")
                    if (InstrumentationRegistry.getArguments().getString("fullUiMatrix") == "true") {
                        onView(withId(R.id.edit_name)).perform(scrollTo(), replaceText("Medicamento sintético de nome longo"), closeSoftKeyboard())
                        onView(withId(R.id.edit_dosage)).perform(scrollTo(), replaceText("500"), closeSoftKeyboard())
                        onView(withId(R.id.autoComplete_unit)).perform(scrollTo()).check(matches(isCompletelyDisplayed()))
                        onView(withId(R.id.button_details)).perform(scrollTo(), click())
                        onView(withId(R.id.autoComplete_form)).perform(scrollTo(), click())
                        capture("${appearance.name}-form-selector")
                        device.pressBack()
                        onView(withId(R.id.color_option_blue)).perform(scrollTo(), click()).check(matches(isChecked()))
                        onView(withId(R.id.color_option_green)).perform(scrollTo()).check(matches(isCompletelyDisplayed()))
                        capture("${appearance.name}-form-colors")
                        onView(withId(R.id.button_save)).check(matches(isCompletelyDisplayed()))
                        navigate(scenario, R.id.nav_settings, "text_settings_title")
                        onView(withId(R.id.button_diagnostics)).perform(scrollTo(), click())
                        capture("${appearance.name}-diagnostics")
                        device.wait(Until.findObject(By.res("android", "button1")), 5000)!!.click()
                        navigate(scenario, R.id.nav_privacy_policy, "text_privacy_policy")
                        capture("${appearance.name}-privacy")
                        onView(withId(R.id.text_privacy_policy)).check(matches(isDisplayed()))
                    }
                }
            }
        } finally {
            File(output, "measurements.json").writeText(measurements.toString(2))
            for (id in ids) {
                AlarmScheduler(context).cancelAlarmsForMedication(id, repo.getSchedulesForMedicationSync(id))
                repo.permanentlyDeleteMedication(id, true)
            }
            preferences.saveLanguage(oldLanguage)
            device.executeShellCommand(if (oldScale.toFloatOrNull() == null) "settings delete system font_scale" else "settings put system font_scale $oldScale")
            instrumentation.runOnMainSync {
                AppCompatDelegate.setDefaultNightMode(oldMode)
                AppCompatDelegate.setApplicationLocales(oldLocales)
            }
        }
    }
}
