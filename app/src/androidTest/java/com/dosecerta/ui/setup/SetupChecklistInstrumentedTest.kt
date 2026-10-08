package com.dosecerta.ui.setup

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.PowerManager
import android.util.SparseArray
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dosecerta.R
import com.dosecerta.alarm.ReminderCapabilityChecker
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.util.SettingsPreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class SetupChecklistInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val progress = context.getSharedPreferences("ui.setup.SetupActivity", Context.MODE_PRIVATE)
    private val manualChecks = context.getSharedPreferences("setup_access_checklist", Context.MODE_PRIVATE)
    private var originalDestination = 0
    private var originalStep = 0
    private var originalAutostart = false
    private var originalLockReview = false
    private var originalNotificationPermission = false
    private var prepared = false

    @Before fun setup() = runBlocking<Unit> {
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        device.wakeUp(); device.pressMenu()
        originalDestination = progress.getInt("setup_destination", 0)
        originalStep = progress.getInt("tutorial_step", 0)
        originalAutostart = manualChecks.getBoolean("autostart_reviewed", false)
        originalLockReview = manualChecks.getBoolean("lock_screen_reviewed", false)
        originalNotificationPermission = ReminderCapabilityChecker(context).check().notificationPermission
        SettingsPreferences(context).acceptTerms()
        progress.edit().putInt("setup_destination", R.id.setupNotificationsFragment).putInt("tutorial_step", 0).commit()
        manualChecks.edit().putBoolean("autostart_reviewed", false).putBoolean("lock_screen_reviewed", false).commit()
        device.executeShellCommand("pm revoke ${context.packageName} android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("pm clear-permission-flags ${context.packageName} android.permission.POST_NOTIFICATIONS user-set user-fixed")
        prepared = true
    }

    @After fun restore() {
        if (!prepared) return
        progress.edit().putInt("setup_destination", originalDestination).putInt("tutorial_step", originalStep).commit()
        manualChecks.edit().putBoolean("autostart_reviewed", originalAutostart).putBoolean("lock_screen_reviewed", originalLockReview).commit()
        if (originalNotificationPermission)
            device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
    }

    private fun awaitChecklist(scenario: ActivityScenario<SetupActivity>) {
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
        var attached = false
        val deadline = android.os.SystemClock.uptimeMillis() + 10_000
        while (!attached && android.os.SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { attached = it.findViewById<View>(R.id.button_allow)?.isAttachedToWindow == true }
            if (!attached) Thread.sleep(50)
        }
        assertTrue("The checklist did not attach", attached)
        onView(withId(R.id.button_allow)).perform(scrollTo()).check(matches(isEnabled()))
        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_setup) as NavHostFragment
            assertEquals(R.id.setupNotificationsFragment, host.navController.currentDestination?.id)
        }
    }

    private fun capture(name: String) {
        device.waitForIdle()
        val output = File(context.filesDir, "qa-setup").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(output, "$name.png")))
        device.dumpWindowHierarchy(File(output, "$name.xml"))
    }

    private fun continueToTutorial() {
        onView(withId(R.id.button_allow)).perform(scrollTo(), click())
        assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "button_action")), 10_000))
    }

    @Test fun deniedNotificationStaysUncheckedAndContinueRemainsAvailable() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            awaitChecklist(scenario)
            onView(withId(R.id.button_notifications)).perform(scrollTo()).check(matches(isNotChecked())).perform(click())
            val deny = device.wait(Until.findObject(By.res(Pattern.compile(".*permissioncontroller:id/permission_deny_button"))), 10_000)
                ?: throw AssertionError("Notification permission dialog did not appear")
            deny.click()
            awaitChecklist(scenario)
            assertFalse(ReminderCapabilityChecker(context).check().notificationPermission)
            onView(withId(R.id.button_notifications)).perform(scrollTo()).check(matches(isNotChecked()))
            assertFalse("Returning from a grant must not launch another Settings screen", device.wait(Until.hasObject(By.pkg("com.android.settings")), 1000))
            onView(withId(R.id.button_allow)).perform(scrollTo())
            capture("checklist-denied-top")
            onView(withId(R.id.button_skip)).perform(scrollTo()).check(matches(isEnabled()))
            capture("checklist-footer")
            continueToTutorial()
            repeat(3) {
                onView(withId(R.id.button_action)).perform(scrollTo(), click())
            }
            // SetupActivity finishes after the last page; observe Main through the device,
            // without calling onActivity on a scenario whose activity was destroyed.
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "bottom_navigation")), 10_000))
            assertTrue(runBlocking { SettingsPreferences(context).isSetupCompletedSync() })
            capture("setup-completed-main")
        }
    }

    @Test fun settingsReturnAndRecreationOnlyRefreshTheChecklist() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            awaitChecklist(scenario)
            onView(withId(R.id.button_exact)).perform(scrollTo(), click())
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10_000))
            device.pressBack()
            awaitChecklist(scenario)
            assertFalse("Resume must not open another settings screen", device.wait(Until.hasObject(By.pkg("com.android.settings")), 1000))
            scenario.recreate()
            awaitChecklist(scenario)
            assertFalse("Recreation must not resume a permission sequence", device.wait(Until.hasObject(By.pkg("com.android.settings")), 1000))
            onView(withId(R.id.button_notifications)).perform(scrollTo()).check(matches(isNotChecked()))
            continueToTutorial()
        }
    }

    @Test fun legacyPendingPermissionStateRestoresWithoutBlockingContinue() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            awaitChecklist(scenario)
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_setup) as NavHostFragment
                val manager = host.childFragmentManager
                val original = manager.fragments.single { it is SetupNotificationsFragment }
                val saved = requireNotNull(manager.saveFragmentInstanceState(original))
                val parcel = Parcel.obtain()
                try {
                    saved.writeToParcel(parcel, 0)
                    parcel.setDataPosition(0)
                    val state = requireNotNull(parcel.readBundle(SetupNotificationsFragment::class.java.classLoader))
                    fun addLegacyPendingState(bundle: Bundle) {
                        bundle.putBoolean("access_active", true)
                        bundle.putBoolean("access_waiting", true)
                        bundle.putStringArrayList("access_attempted", arrayListOf("NOTIFICATIONS", "EXACT_ALARMS", "FULL_SCREEN"))
                    }
                    addLegacyPendingState(state)
                    state.getBundle("savedInstanceState")?.let { addLegacyPendingState(it) }
                    parcel.setDataPosition(0)
                    parcel.writeBundle(state)
                    parcel.setDataPosition(0)
                    val restored = Fragment.SavedState.CREATOR.createFromParcel(parcel)
                    manager.beginTransaction().replace(R.id.nav_host_fragment_setup,
                        SetupNotificationsFragment().apply { setInitialSavedState(restored) }).commitNow()
                } finally { parcel.recycle() }
            }
            awaitChecklist(scenario)
            assertFalse("Legacy pending state must not auto-launch settings", device.wait(Until.hasObject(By.pkg("com.android.settings")), 1000))
            continueToTutorial()
        }
    }

    @Test fun manufacturerReviewPersistsSeparatelyFromAndroidPermission() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            awaitChecklist(scenario)
            // The dedicated emulator is not Xiaomi; expose the manual rows without faking grants.
            scenario.onActivity { it.findViewById<View>(R.id.card_xiaomi).visibility = View.VISIBLE }
            onView(withId(R.id.check_auto_start_reviewed)).perform(scrollTo(), click())
            onView(withId(R.id.check_lock_screen_reviewed)).perform(scrollTo(), click())
            assertTrue(manualChecks.getBoolean("autostart_reviewed", false))
            assertTrue(manualChecks.getBoolean("lock_screen_reviewed", false))
            scenario.recreate()
            awaitChecklist(scenario)
            scenario.onActivity { it.findViewById<View>(R.id.card_xiaomi).visibility = View.VISIBLE }
            onView(withId(R.id.check_auto_start_reviewed)).perform(scrollTo()).check(matches(isChecked()))
            onView(withId(R.id.check_lock_screen_reviewed)).perform(scrollTo()).check(matches(isChecked()))
            capture("manufacturer-reviewed")
            assertFalse(ReminderCapabilityChecker(context).check().notificationPermission)
            onView(withId(R.id.button_notifications)).perform(scrollTo()).check(matches(isNotChecked()))
            continueToTutorial()
        }
    }

    @Test fun legacyButtonHierarchyDoesNotCrashOrOverrideThePermissionChecklist() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            awaitChecklist(scenario)
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_setup) as NavHostFragment
                val fragment = host.childFragmentManager.fragments.single { it is SetupNotificationsFragment }
                val legacyStates = SparseArray<Parcelable>()
                listOf(R.id.button_notifications, R.id.button_exact, R.id.button_full_screen, R.id.button_battery).forEach { id ->
                    MaterialButton(activity).apply { this.id = id }.saveHierarchyState(legacyStates)
                    assertTrue(requireNotNull(legacyStates[id]).javaClass.name.contains("MaterialButton"))
                }
                // Restore the actual old button payloads through the parent dispatch path.
                // CompoundButton would cast these incompatible states without the migration guard.
                fragment.requireView().restoreHierarchyState(legacyStates)
            }
            fun assertEffectiveChecklist() {
                scenario.onActivity { activity ->
                    val state = ReminderCapabilityChecker(activity).check()
                    val expected = mapOf(
                        R.id.button_notifications to (state.notificationPermission && state.notificationsEnabled),
                        R.id.button_alarm_channel to state.alarmChannelEnabled,
                        R.id.button_reminder_channel to state.reminderChannelEnabled,
                        R.id.button_exact to state.exactAlarms,
                        R.id.button_full_screen to state.fullScreenIntent,
                        R.id.button_battery to activity.getSystemService(PowerManager::class.java)
                            .isIgnoringBatteryOptimizations(activity.packageName)
                    )
                    expected.forEach { (id, allowed) ->
                        assertEquals("Checkbox must reflect effective access: $id", allowed,
                            activity.findViewById<MaterialCheckBox>(id).isChecked)
                    }
                    assertTrue(activity.findViewById<View>(R.id.button_allow).isEnabled)
                }
            }
            assertEffectiveChecklist()
            scenario.recreate()
            awaitChecklist(scenario)
            assertEffectiveChecklist()
            continueToTutorial()
        }
    }
}
