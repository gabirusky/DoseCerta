package com.dosecerta.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.navigation.fragment.NavHostFragment
import com.dosecerta.R
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run only on the dedicated synthetic QA emulator; does not reset an existing device. */
@RunWith(AndroidJUnit4::class)
class UiFlowInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before fun dedicatedEmulatorOnly() {
        assumeTrue("Synthetic UI tests require DoseCerta_QA", SyntheticQaDevice.isDedicated(device))
    }
    private fun capture(name: String) {
        val dir = File(context.filesDir, "qa-ui").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(dir, "$name.png")))
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
    }
    private fun launch(): ActivityScenario<MainActivity> {
        runBlocking { SettingsPreferences(context).setSetupCompleted() }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "bottom_navigation")), 10000))
        return scenario
    }
    private fun navigate(scenario: ActivityScenario<MainActivity>, id: Int) {
        scenario.onActivity { activity ->
            val nav = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
            nav.navigate(id)
        }
        val marker = when (id) {
            R.id.nav_home -> "text_greeting"
            R.id.nav_medications -> "search_view"
            R.id.nav_history -> "recycler_logs"
            R.id.nav_settings -> "text_settings_title"
            R.id.nav_privacy_policy -> "text_privacy_policy"
            R.id.nav_add_medication -> "edit_name"
            else -> error("No screen marker for $id")
        }
        assertTrue("Destination $marker did not become visible", device.wait(Until.hasObject(By.res(context.packageName, marker)), 10000))
        device.waitForIdle()
    }
    @Test fun eachRecurrenceCanBeEnteredPreviewedAndSaved() {
        val repo = MedicationRepository(DoseCertaDatabase.getDatabase(context))
        val names = mutableListOf<String>()
        launch().use { scenario ->
            try {
                Frequency.values().forEach { frequency ->
                    val name = "QA UI ${frequency.name} ${System.nanoTime()}"
                    names += name
                    navigate(scenario, R.id.nav_add_medication)
                    onView(withId(R.id.edit_name)).perform(scrollTo(), replaceText(name), closeSoftKeyboard())
                    onView(withId(R.id.edit_dosage)).perform(scrollTo(), replaceText("500"), closeSoftKeyboard())
                    onView(withId(R.id.autoComplete_frequency)).perform(scrollTo(), click())
                    val label = UiLabels.frequency(context, frequency)
                    onData(org.hamcrest.Matchers.equalTo(label)).inRoot(isPlatformPopup()).perform(click())
                    onView(withId(R.id.autoComplete_frequency)).check(matches(withText(label)))
                    if (frequency == Frequency.MONTHLY) onView(withId(R.id.edit_month_day)).perform(scrollTo(), replaceText("31"), closeSoftKeyboard())
                    if (frequency != Frequency.AS_NEEDED) {
                        onView(withId(R.id.button_add_time)).perform(scrollTo(), click())
                        assertTrue(device.wait(Until.hasObject(By.res("android", "button1")), 3000))
                        device.findObject(By.res("android", "button1")).click()
                        onView(withId(R.id.button_preview)).perform(scrollTo(), click())
                        onView(withId(R.id.text_preview)).perform(scrollTo()).check(matches(isDisplayed()))
                    }
                    capture("recurrence-${frequency.name.lowercase()}")
                    scenario.recreate()
                    onView(withId(R.id.edit_name)).perform(scrollTo()).check(matches(withText(name)))
                    onView(withId(R.id.autoComplete_frequency)).perform(scrollTo()).check(matches(withText(label)))
                    onView(withId(R.id.button_save)).perform(click())
                    assertTrue(device.wait(Until.hasObject(By.res("android", "button1")), 10000))
                    device.findObject(By.res("android", "button1")).click()
                    runBlocking {
                        val medication = repo.getAllActiveMedicationsSync().single { it.name == name }
                        assertEquals(frequency, medication.frequency)
                        val slots = repo.getSchedulesForMedicationSync(medication.id)
                        if (frequency == Frequency.AS_NEEDED) assertTrue(slots.isEmpty()) else assertEquals(1, slots.size)
                    }
                }
                navigate(scenario, R.id.nav_home); capture("home-populated")
                navigate(scenario, R.id.nav_medications); capture("medications-populated")
                navigate(scenario, R.id.nav_history); capture("history")
                navigate(scenario, R.id.nav_settings); capture("settings")
                navigate(scenario, R.id.nav_privacy_policy); capture("privacy")
            } finally {
                runBlocking {
                    repo.getAllActiveMedicationsSync().filter { it.name in names }.forEach { medication ->
                        AlarmScheduler(context).cancelAlarmsForMedication(medication.id, repo.getSchedulesForMedicationSync(medication.id))
                        repo.permanentlyDeleteMedication(medication.id, true)
                    }
                }
            }
        }
    }
}
