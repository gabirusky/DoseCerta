package com.dosecerta.ui

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.provider.DocumentsContract
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
        ?: throw AssertionError("Missing app control: $id")
    private fun capture(name: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
        val output = File(context.filesDir, "qa-system").apply { mkdirs() }
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
    @Test fun backKeyboardRecentsAndRotationKeepDraftAndSafeInsets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            find("nav_medications").click()
            find("fab_add").click()
            onView(withId(R.id.edit_name)).perform(scrollTo(), click(), typeText("Synthetic unsaved draft"))
            device.pressBack() // IME first, without discarding the form.
            onView(withId(R.id.edit_name)).check(matches(withText("Synthetic unsaved draft")))
            capture("form-keyboard-dismissed")
            device.pressRecentApps()
            device.pressHome()
            context.startActivity(requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)))
            find("edit_name")
            device.setOrientationLeft()
            onView(withId(R.id.edit_name)).perform(scrollTo()).check(matches(withText("Synthetic unsaved draft")))
            capture("form-landscape")
            device.setOrientationNatural()
            scenario.recreate()
            onView(withId(R.id.edit_name)).perform(scrollTo()).check(matches(withText("Synthetic unsaved draft")))
            scenario.onActivity { activity ->
                val root = activity.findViewById<android.view.View>(android.R.id.content)
                val insets = requireNotNull(ViewCompat.getRootWindowInsets(root))
                    .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val save = activity.findViewById<android.view.View>(R.id.button_save)
                val rect = android.graphics.Rect()
                assertTrue(save.getGlobalVisibleRect(rect))
                assertTrue("Save overlaps status bar", rect.top >= insets.top)
                assertTrue("Save overlaps navigation bar", rect.bottom <= root.height - insets.bottom)
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
                device.waitForIdle()
                capture("pdf-open-result")
                assertTrue(device.currentPackageName != context.packageName ||
                    device.hasObject(By.text(context.getString(R.string.report_no_viewer))))
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
