package com.dosecerta.ui.setup

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dosecerta.R
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupTutorialInstrumentedTest {
    @Test fun selectedIndicatorStaysCircularAcrossPagesAndActivityRecreation() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        device.wakeUp()
        device.pressMenu()
        SettingsPreferences(context).acceptTerms()
        val preferences = context.getSharedPreferences("ui.setup.SetupActivity", Context.MODE_PRIVATE)
        val originalDestination = preferences.getInt("setup_destination", 0)
        val originalStep = preferences.getInt("tutorial_step", 0)
        preferences.edit().putInt("setup_destination", R.id.setupTutorialFragment).putInt("tutorial_step", 0).commit()
        try {
            ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "button_action")), 10_000))
                fun assertIndicators(selected: Int) {
                    scenario.onActivity { activity ->
                        listOf(R.id.indicator_1, R.id.indicator_2, R.id.indicator_3).forEachIndexed { index, id ->
                            val indicator = activity.findViewById<View>(id)
                            assertTrue("Indicator must have a measured diameter", indicator.width > 0)
                            assertEquals("Page indicator must stay circular", indicator.width, indicator.height)
                            assertEquals(index == selected, indicator.isSelected)
                            assertEquals(indicator.background.intrinsicWidth, indicator.background.intrinsicHeight)
                        }
                    }
                }
                assertIndicators(0)
                onView(withId(R.id.button_action)).perform(scrollTo(), click())
                assertIndicators(1)
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "button_action")), 10_000))
                assertIndicators(1)
                onView(withId(R.id.button_action)).perform(scrollTo(), click())
                assertIndicators(2)
            }
        } finally {
            preferences.edit().putInt("setup_destination", originalDestination).putInt("tutorial_step", originalStep).commit()
        }
    }
}
