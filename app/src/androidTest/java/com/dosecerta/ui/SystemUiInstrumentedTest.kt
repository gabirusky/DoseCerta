package com.dosecerta.ui

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.provider.DocumentsContract
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.ui.history.ReportDocuments
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises system boundaries on a synthetic device without deleting existing medicine data. */
@RunWith(AndroidJUnit4::class)
class SystemUiInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var originalKeyboardSetting: String? = null
    private fun app(id: String) = By.res(context.packageName, id)
    private fun find(id: String) = device.wait(Until.findObject(app(id)), 10_000)
        ?: run { capture("missing-$id"); throw AssertionError("Missing app control: $id") }
    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
        val runId = InstrumentationRegistry.getArguments().getString("evidenceRunId")
        val output = File(context.filesDir, if (runId == null) "qa-system" else "qa-system/$runId").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(output, "$name.png")))
        device.dumpWindowHierarchy(File(output, "$name.xml"))
    }
    @Before fun dedicatedDevice() {
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        device.wakeUp()
        device.pressMenu()
        originalKeyboardSetting = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        runBlocking { SettingsPreferences(context).setSetupCompleted() }
    }
    @After fun restoreDevice() {
        originalKeyboardSetting?.let {
            device.executeShellCommand(if (it == "null") "settings delete secure show_ime_with_hard_keyboard"
                else "settings put secure show_ime_with_hard_keyboard $it")
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }
    @Test fun bottomNavigationDoesNotRepeatRootSystemOrKeyboardInsets() {
        // API 30 can represent all inset types independently, including
        // synthetic cutouts and the IME. Older APIs retain the real-IME
        // coverage in backKeyboardRecentsAndRotationKeepDraftAndSafeInsets.
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 30)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            find("bottom_navigation")
            scenario.onActivity { activity ->
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val root = content.getChildAt(0)
                val navigation = activity.findViewById<android.view.View>(R.id.bottom_navigation)
                val originalInsets = requireNotNull(ViewCompat.getRootWindowInsets(root))
                val navigationHeight = navigation.height
                assertEquals("The root already protects the navigation bar", 0, navigation.paddingBottom)
                assertTrue(navigationHeight > 0)
                fun dispatch(keyboardBottom: Int) {
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(12, 24, 18, 48))
                        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 56, 0, 0))
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboardBottom))
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(root, insets)
                    root.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(root.width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(root.height, android.view.View.MeasureSpec.EXACTLY)
                    )
                    root.layout(root.left, root.top, root.right, root.bottom)
                    assertEquals(12, root.paddingLeft)
                    assertEquals(56, root.paddingTop)
                    assertEquals(18, root.paddingRight)
                    assertEquals(maxOf(48, keyboardBottom), root.paddingBottom)
                    assertEquals("Material must not add a second bottom inset", 0, navigation.paddingBottom)
                    assertEquals("Insets must not inflate the app navigation bar", navigationHeight, navigation.height)
                }
                try {
                    dispatch(0)
                    dispatch(0) // Repeated delivery must not accumulate padding.
                    dispatch(320)
                    dispatch(0) // IME dismissal restores just the navigation-bar inset.
                } finally {
                    ViewCompat.dispatchApplyWindowInsets(root, originalInsets)
                    ViewCompat.requestApplyInsets(root)
                }
            }
        }
    }
    @Test fun backKeyboardRecentsAndRotationKeepDraftAndSafeInsets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            find("nav_medications").click()
            find("fab_add").click()
            // Set an exact fixture, then show the real IME. Keyboard autocorrection
            // must not change the text used to assert draft restoration.
            onView(withId(R.id.edit_name)).perform(scrollTo(), replaceText("Synthetic unsaved draft"), click())
            scenario.onActivity { activity ->
                val edit = activity.findViewById<android.view.View>(R.id.edit_name)
                edit.requestFocus()
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            val imeDeadline = android.os.SystemClock.elapsedRealtime() + 10_000
            var imeVisible = false
            while (!imeVisible && android.os.SystemClock.elapsedRealtime() < imeDeadline) {
                scenario.onActivity { activity ->
                    imeVisible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                if (!imeVisible) Thread.sleep(50)
            }
            capture("form-keyboard-open")
            assertTrue("The real IME must be visible before testing its Back dismissal", imeVisible)
            device.pressBack() // IME first, without discarding the form.
            onView(withId(R.id.edit_name)).check(matches(withText("Synthetic unsaved draft")))
            capture("form-keyboard-dismissed")
            device.pressRecentApps()
            // Android 13 can expose the app's live accessibility tree inside
            // the overview card. Check the actual resumed OS activity as well.
            val overviewDeadline = android.os.SystemClock.elapsedRealtime() + 10_000
            var overviewOpened = false
            var resumed = ""
            while (!overviewOpened && android.os.SystemClock.elapsedRealtime() < overviewDeadline) {
                resumed = device.executeShellCommand("dumpsys activity activities").lineSequence()
                    .filter { it.contains("mResumedActivity") || it.contains("topResumedActivity") }.joinToString("\n")
                overviewOpened = !device.hasObject(app("edit_name")) ||
                    resumed.contains("launcher", ignoreCase = true) || resumed.contains(".recents.RecentsActivity")
                if (!overviewOpened) Thread.sleep(50)
            }
            File(context.filesDir, "qa-system").resolve("form-recents-resumed.txt").writeText(resumed)
            if (!overviewOpened) capture("form-recents-failed")
            assertTrue("System overview did not replace the app; verify the AVD has completed OS setup", overviewOpened)
            device.waitForIdle()
            capture("form-recents")
            // Return to this task through SystemUI. Starting a second MAIN
            // intent from a test Context is not the launcher's task restore
            // operation on older Android versions.
            if (android.os.Build.VERSION.SDK_INT <= 28) {
                // Older overview stacks can center a previous viewer or the
                // instrumentation helper. Select this app's real task title.
                val taskTitle = device.wait(Until.findObject(By.text(context.getString(R.string.app_name))), 5000)
                assertNotNull("Dose Certa task is missing from system overview", taskTitle)
                taskTitle!!.parent.click()
            } else {
                device.click(device.displayWidth / 2, device.displayHeight / 2)
            }
            find("edit_name")
            device.setOrientationLeft()
            onView(withId(R.id.edit_name)).perform(scrollTo()).check(matches(withText("Synthetic unsaved draft")))
            capture("form-landscape")
            device.setOrientationNatural()
            scenario.recreate()
            onView(withId(R.id.edit_name)).perform(scrollTo()).check(matches(withText("Synthetic unsaved draft")))
            capture("form-restored")
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.View>(android.R.id.content)
                val insets = requireNotNull(ViewCompat.getRootWindowInsets(root))
                    .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val save = activity.findViewById<android.view.View>(R.id.button_save)
                val rect = android.graphics.Rect()
                assertTrue(save.getGlobalVisibleRect(rect))
                // View rectangles are in screen coordinates. Pre-edge-to-edge
                // content height already excludes bars and cannot be subtracted
                // from a global rectangle a second time.
                val usable = android.graphics.Rect()
                activity.window.decorView.getWindowVisibleDisplayFrame(usable)
                val screenSafe = android.graphics.Rect(insets.left, insets.top,
                    device.displayWidth - insets.right, device.displayHeight - insets.bottom)
                assertTrue(usable.intersect(screenSafe))
                assertTrue("Save $rect is outside usable screen $usable", usable.contains(rect))
            }
            device.pressBack()
            val stay = device.wait(Until.findObject(By.res("android", "button2")), 5000)
            assertNotNull("Dirty draft must ask before leaving", stay)
            stay!!.click()
            find("edit_name")
            device.pressBack()
            device.wait(Until.findObject(By.res("android", "button1")), 5000)!!.click()
            find("search_view")
            capture("back-to-medications")
        }
        device.unfreezeRotation()
    }
    @Test fun documentPickerCancellationAndSaveProduceOneReadableGrantedPdf() {
        val resolver = context.contentResolver
        val before = resolver.persistedUriPermissions.map { it.uri }.toSet()
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                find("nav_history").click()
                find("fab_export_pdf").click()
                assertTrue(device.wait(Until.hasObject(By.res("android", "title")), 10_000))
                capture("pdf-picker-cancel")
                device.pressBack()
                assertTrue(find("fab_export_pdf").isEnabled)
                assertEquals(before, resolver.persistedUriPermissions.map { it.uri }.toSet())
                find("fab_export_pdf").click()
                val name = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
                    ?: throw AssertionError("Document filename field missing")
                name.text = "DoseCerta-synthetic-${System.nanoTime()}.pdf"
                val requestedName = name.text
                device.setOrientationLeft()
                assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
                capture("pdf-picker-rotated")
                device.setOrientationNatural()
                // Android 8 DocumentsUI can reset its filename on rotation.
                // Re-enter the requested destination in the system picker;
                // the app's request must survive and produce just one PDF.
                device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)!!.text = requestedName
                val save = device.wait(Until.findObject(By.res("android", "button1")), 10_000)
                    ?: throw AssertionError("System document save button missing")
                capture("pdf-picker-save")
                save.click()
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.report_saved))), 30_000))
                capture("pdf-saved")
                val added = resolver.persistedUriPermissions.map { it.uri }.toSet() - before
                assertEquals("One export must create one document", 1, added.size)
                val uri = added.single()
                resolver.openFileDescriptor(uri, "r")!!.use { fd ->
                    PdfRenderer(fd).use { pdf -> assertTrue(pdf.pageCount > 0) }
                }
                for (intent in listOf(ReportDocuments.open(uri), ReportDocuments.share(uri))) {
                    assertEquals("application/pdf", intent.type)
                    assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
                    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                }
                device.findObject(By.res("android", "button1")).click() // Open, or show the no-viewer alternative.
                // A Snackbar is transient. Observe its real result before a
                // screenshot/XML dump consumes its display interval.
                val noViewer = By.text(context.getString(R.string.report_no_viewer))
                val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                while (device.currentPackageName == context.packageName && !device.hasObject(noViewer) &&
                    android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
                val missingViewer = device.hasObject(noViewer)
                assertTrue("Open must launch a reader or offer a recovery action",
                    device.currentPackageName != context.packageName || missingViewer)
                if (missingViewer) assertTrue("No-reader feedback must offer Share",
                    device.hasObject(By.text(context.getString(R.string.report_share))))
                capture("pdf-open-result")
            } finally {
                // Only documents created by this test are removed.
                (resolver.persistedUriPermissions.map { it.uri }.toSet() - before).forEach { uri ->
                    DocumentsContract.deleteDocument(resolver, uri)
                    if (resolver.persistedUriPermissions.any { it.uri == uri }) {
                        resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                }
            }
        }
    }
}
