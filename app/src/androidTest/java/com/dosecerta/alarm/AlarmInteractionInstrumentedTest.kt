package com.dosecerta.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.domain.DoseState
import com.dosecerta.notification.NotificationHelper
import com.dosecerta.qa.SyntheticQaDevice
import com.dosecerta.util.Constants
import com.dosecerta.util.SettingsPreferences
import com.dosecerta.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Supplementary interaction tests. Real initial alarm delivery is proven by the pipeline/journey suites. */
@RunWith(AndroidJUnit4::class)
class AlarmInteractionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val database by lazy { DoseCertaDatabase.getDatabase(context) }
    private val repository by lazy { MedicationRepository(database) }
    private val coordinator by lazy { DoseActionCoordinator(repository) }
    private val scheduler by lazy { AlarmScheduler(context) }
    private val medications = mutableListOf<Long>()
    private var oldDetails = false
    private var dedicated = false
    private fun app(id: String) = By.res(context.packageName, id)
    private fun find(id: String) = device.wait(Until.findObject(app(id)), 10_000)
        ?: throw AssertionError("Missing app control: $id")

    @Before fun syntheticOnly() = runBlocking {
        assumeTrue(SyntheticQaDevice.isDedicated(device))
        dedicated = true
        device.wakeUp(); device.pressMenu()
        if (Build.VERSION.SDK_INT >= 33) device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        if (Build.VERSION.SDK_INT >= 31) device.executeShellCommand("cmd appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val preferences = SettingsPreferences(context)
        oldDetails = preferences.getShowMedicationOnLockScreenSync()
        preferences.saveShowMedicationOnLockScreen(false)
        preferences.setSetupCompleted()
    }
    @After fun cleanup() = runBlocking {
        if (!dedicated) return@runBlocking
        instrumentation.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filterIsInstance<AlarmActivity>().forEach { it.finish() }
        }
        for (id in medications) {
            scheduler.cancelAlarmsForMedication(id, repository.getSchedulesForMedicationSync(id))
            repository.permanentlyDeleteMedication(id, true)
        }
        SettingsPreferences(context).saveShowMedicationOnLockScreen(oldDetails)
    }
    private suspend fun occurrence(): MedicationLog {
        val now = System.currentTimeMillis()
        val due = now / 60_000 * 60_000
        val local = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
        val id = repository.insertMedication(Medication(name = "Synthetic card ${UUID.randomUUID()}", dosage = "1", unit = "mg",
            pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.DAILY,
            color = 0xFFD81B60.toInt(), createdAt = now - 3_600_000))
        medications += id
        val slot = repository.insertSchedule(Schedule(medicationId = id, timeInMinutes = local.hour * 60 + local.minute,
            daysOfWeek = emptyList(), validFrom = now - 3_600_000))
        return requireNotNull(repository.ensureOccurrence(slot, due))
    }
    private fun capture(name: String) {
        val output = File(context.filesDir, "qa-alarm-interactions").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(output, "$name.png")))
        device.dumpWindowHierarchy(File(output, "$name.xml"))
    }
    private suspend fun await(condition: suspend () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) delay(100)
        assertTrue("Condition did not become true", condition())
    }
    private fun currentCard(): AlarmActivity? {
        var activity: AlarmActivity? = null
        instrumentation.runOnMainSync {
            activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<AlarmActivity>().singleOrNull()
        }
        return activity
    }

    @Test fun newIntentAndRecreationDiscardPreviousGesture() = runBlocking {
        val first = occurrence(); val second = occurrence()
        assertTrue(coordinator.deliver(first.occurrenceId) is DoseActionResult.Success)
        assertTrue(coordinator.deliver(second.occurrenceId) is DoseActionResult.Success)
        val intent = AlarmIdentity.intent(context, AlarmActivity::class.java, first.occurrenceId, "card")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
        try {
            assertTrue(device.wait(Until.hasObject(By.text(requireNotNull(first.snapshotName))), 10_000))
            find("button_take")
            val originalCard = requireNotNull(currentCard())
            instrumentation.runOnMainSync {
                val swipe = originalCard.findViewById<SwipeToConfirmView>(R.id.swipe_take)
                val down = SystemClock.uptimeMillis()
                for ((event, fraction) in listOf(MotionEvent.ACTION_DOWN to .05f, MotionEvent.ACTION_MOVE to .95f)) {
                    MotionEvent.obtain(down, SystemClock.uptimeMillis(), event, swipe.width * fraction, swipe.height / 2f, 0)
                        .also { swipe.dispatchTouchEvent(it); it.recycle() }
                }
            }
            capture("first-unreleased-gesture")
            context.startActivity(AlarmIdentity.intent(context, AlarmActivity::class.java, second.occurrenceId, "card")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertTrue(device.wait(Until.hasObject(By.text(requireNotNull(second.snapshotName))), 10_000))
            assertSame("singleTop must update the existing card", originalCard, currentCard())
            instrumentation.runOnMainSync {
                val swipe = originalCard.findViewById<SwipeToConfirmView>(R.id.swipe_take)
                MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                    swipe.width * .95f, swipe.height / 2f, 0).also { swipe.dispatchTouchEvent(it); it.recycle() }
            }
            assertEquals(DoseState.ALERTING, repository.getOccurrence(first.occurrenceId)?.state)
            assertEquals(DoseState.ALERTING, repository.getOccurrence(second.occurrenceId)?.state)
            capture("second-intent")
            // ActivityScenario filters by its launch Intent; onNewIntent changes
            // that identity. Follow the real activity lifecycle across recreation.
            instrumentation.runOnMainSync { originalCard.recreate() }
            await { currentCard()?.let { it !== originalCard } == true }
            assertEquals(second.snapshotName, find("text_medication_name").text)
            capture("second-restored")
            find("button_skip").click()
            await { repository.getOccurrence(second.occurrenceId)?.state == DoseState.SKIPPED }
            assertFalse("A single tap records the action without a dialog", device.hasObject(By.res("android", "button1")))
            assertEquals(DoseState.ALERTING, repository.getOccurrence(first.occurrenceId)?.state)
            assertNull(repository.getOccurrence(second.occurrenceId)?.actualTime)
        } finally {
            val remaining = currentCard()
            instrumentation.runOnMainSync { remaining?.finish() }
        }
    }

    @Test fun completedSwipeRecordsOnceWithoutConfirmation() = runBlocking {
        val log = occurrence()
        assertTrue(coordinator.deliver(log.occurrenceId) is DoseActionResult.Success)
        context.startActivity(AlarmIdentity.intent(context, AlarmActivity::class.java, log.occurrenceId, "card")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        assertTrue(device.wait(Until.hasObject(By.text(requireNotNull(log.snapshotName))), 10_000))
        find("button_take")
        val card = requireNotNull(currentCard())
        instrumentation.runOnMainSync {
            val swipe = card.findViewById<SwipeToConfirmView>(R.id.swipe_take)
            val down = SystemClock.uptimeMillis()
            for ((event, fraction) in listOf(MotionEvent.ACTION_DOWN to .05f, MotionEvent.ACTION_MOVE to .95f,
                MotionEvent.ACTION_UP to .95f)) {
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), event, swipe.width * fraction, swipe.height / 2f, 0)
                    .also { swipe.dispatchTouchEvent(it); it.recycle() }
            }
            // A second click while persistence is running cannot apply another action.
            card.findViewById<View>(R.id.button_skip).performClick()
        }
        await { repository.getOccurrence(log.occurrenceId)?.state == DoseState.TAKEN }
        val taken = requireNotNull(repository.getOccurrence(log.occurrenceId))
        assertNotNull(taken.actualTime)
        assertEquals(log.originalDueAt, taken.originalDueAt)
        assertFalse(device.hasObject(By.res("android", "button1")))
        assertTrue(coordinator.skip(log.occurrenceId) is DoseActionResult.Rejected)
        assertEquals(taken.actualTime, repository.getOccurrence(log.occurrenceId)?.actualTime)
    }

    @Test fun lockedCardShowsCompleteDoseAndSnoozesWithOneTap() = runBlocking {
        val log = occurrence()
        assertTrue(coordinator.deliver(log.occurrenceId) is DoseActionResult.Success)
        device.sleep()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        await { keyguard.isKeyguardLocked }
        try {
            context.startActivity(AlarmIdentity.intent(context, AlarmActivity::class.java, log.occurrenceId, "card")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            assertTrue(device.wait(Until.hasObject(By.text(requireNotNull(log.snapshotName))), 10_000))
            assertEquals(log.snapshotName, find("text_medication_name").text)
            assertEquals("1 mg", find("text_dosage_info").text)
            find("button_take"); find("button_skip")
            assertTrue(keyguard.isKeyguardLocked)
            val shownCard = requireNotNull(currentCard())
            instrumentation.runOnMainSync {
                assertEquals(log.snapshotColor, shownCard.findViewById<TextView>(R.id.text_medication_name).currentTextColor)
            }
            capture("complete-card-locked")
            val before = System.currentTimeMillis()
            find("button_snooze").click()
            await { repository.getOccurrence(log.occurrenceId)?.state == DoseState.SNOOZED }
            val snoozed = requireNotNull(repository.getOccurrence(log.occurrenceId))
            assertEquals(log.originalDueAt, snoozed.originalDueAt)
            assertNull(snoozed.actualTime)
            assertTrue(requireNotNull(snoozed.snoozedUntil) in (before + Constants.SNOOZE_DURATION_MINUTES * 60_000L)..(System.currentTimeMillis() + Constants.SNOOZE_DURATION_MINUTES * 60_000L))
            assertTrue("Alarm action must keep the device locked", keyguard.isKeyguardLocked)
            assertFalse(device.hasObject(By.res("android", "button1")))
        } finally {
            val card = currentCard()
            instrumentation.runOnMainSync { card?.finish() }
            device.wakeUp(); device.pressMenu()
        }
    }

    @Test fun cancelledSwipeNeverConfirmsAndAccessibilityClickDoes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            find("bottom_navigation")
            scenario.onActivity { activity ->
            val swipe = SwipeToConfirmView(activity)
            activity.findViewById<FrameLayout>(android.R.id.content).addView(swipe, FrameLayout.LayoutParams(600, 150))
            var confirmations = 0
            swipe.onConfirmed = { confirmations++ }
            swipe.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(150, View.MeasureSpec.EXACTLY))
            swipe.layout(0, 0, 600, 150)
            val down = SystemClock.uptimeMillis()
            for ((action, x) in listOf(MotionEvent.ACTION_DOWN to 20f, MotionEvent.ACTION_MOVE to 580f,
                MotionEvent.ACTION_CANCEL to 580f, MotionEvent.ACTION_UP to 580f)) {
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, 75f, 0).also { swipe.dispatchTouchEvent(it); it.recycle() }
            }
            assertEquals("ACTION_CANCEL cannot confirm or snooze", 0, confirmations)
            val info = swipe.createAccessibilityNodeInfo()
            assertEquals(android.widget.Button::class.java.name, info.className)
            assertTrue(info.isClickable)
            assertTrue(swipe.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null))
            assertEquals(1, confirmations)
            }
        }
    }

    @Test fun staleNotificationActionsKeepClosedDoseAndOtherNotification() = runBlocking {
        val first = occurrence(); val second = occurrence()
        assertTrue(coordinator.deliver(second.occurrenceId) is DoseActionResult.Success)
        val helper = NotificationHelper(context)
        SettingsPreferences(context).saveShowMedicationOnLockScreen(true)
        val old = helper.buildAlarm(first, false, false)
        assertTrue(helper.showAlarm(second, false, false))
        val taken = coordinator.take(first.occurrenceId) as DoseActionResult.Success
        val originalTime = taken.occurrence.actualTime
        for (label in listOf(R.string.notification_action_skip, R.string.notification_action_take, R.string.reminder_silence)) {
            val before = AlarmDiagnostics.read(context)
            old.actions.single { it.title.toString() == context.getString(label) }.actionIntent.send()
            await { AlarmDiagnostics.read(context) != before }
            assertEquals(DoseState.TAKEN, repository.getOccurrence(first.occurrenceId)?.state)
            assertEquals(originalTime, repository.getOccurrence(first.occurrenceId)?.actualTime)
            assertEquals(DoseState.ALERTING, repository.getOccurrence(second.occurrenceId)?.state)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.any {
                it.tag == AlarmIdentity.uri(second.occurrenceId, "notification").toString()
            })
        }
    }

    @Test fun actualTimeoutAndFollowUpRespectPersistedState() = runBlocking {
        val first = occurrence()
        val deadline = System.currentTimeMillis() + 5000
        // A shortened synthetic deadline tests the real callback without changing the production 30-minute policy.
        database.medicationLogDao().update(first.copy(deadlineAt = deadline))
        assertTrue(scheduler.scheduleTimeout(requireNotNull(repository.getOccurrence(first.occurrenceId))) is ScheduleResult.Scheduled)
        assertEquals(MedicationStatus.PENDING, repository.getOccurrence(first.occurrenceId)?.status)
        await { repository.getOccurrence(first.occurrenceId)?.state == DoseState.MISSED }
        val missed = requireNotNull(repository.getOccurrence(first.occurrenceId))
        assertTrue(System.currentTimeMillis() >= deadline)
        scheduler.cancelOccurrence(first.occurrenceId)
        database.medicationLogDao().update(missed.copy(reminderAt = System.currentTimeMillis() + 3000))
        assertTrue(scheduler.scheduleFollowUp(requireNotNull(repository.getOccurrence(first.occurrenceId))) is ScheduleResult.Scheduled)
        val manager = context.getSystemService(NotificationManager::class.java)
        await { manager.activeNotifications.any { it.tag == AlarmIdentity.uri(first.occurrenceId, "notification").toString() } }
        assertNull(repository.getOccurrence(first.occurrenceId)?.reminderAt)
        val skipped = occurrence()
        assertTrue(coordinator.skip(skipped.occurrenceId) is DoseActionResult.Success)
        assertTrue(scheduler.scheduleFollowUp(requireNotNull(repository.getOccurrence(skipped.occurrenceId))) is ScheduleResult.Unavailable)
        assertFalse(manager.activeNotifications.any { it.tag == AlarmIdentity.uri(skipped.occurrenceId, "notification").toString() })
    }
}
