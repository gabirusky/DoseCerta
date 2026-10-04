package com.dosecerta.domain

import org.junit.Assert.*
import org.junit.Test

class DoseStateMachineTest {
    private val due = 1_000_000L
    @Test fun deliveryIsPendingOutcomeAndDoesNotImmediatelyMarkMissed() {
        val delivered = DoseStateMachine.transition(DoseState.PENDING, DoseCommand.DELIVER, due, due)!!
        assertEquals(DoseState.ALERTING, delivered.state)
        assertNull(DoseStateMachine.transition(delivered.state, DoseCommand.TIMEOUT, due, due + 1))
    }
    @Test fun timeoutCannotOverwriteTakenAndRepeatedTakeIsIdempotent() {
        assertNull(DoseStateMachine.transition(DoseState.TAKEN, DoseCommand.TIMEOUT, due, due + DosePolicy.TOLERANCE_MILLIS))
        assertFalse(DoseStateMachine.transition(DoseState.TAKEN, DoseCommand.TAKE, due, due + 1)!!.changed)
        assertNull(DoseStateMachine.transition(DoseState.SKIPPED, DoseCommand.TAKE, due, due + 1))
    }
    @Test fun closingAndSilencingDoNotConfirmIntakeOrSkip() {
        val dismissed = DoseStateMachine.transition(DoseState.ALERTING, DoseCommand.DISMISS, due, due + 10)!!
        assertEquals(DoseState.DISMISSED, dismissed.state)
        assertEquals(DoseState.MISSED, DoseStateMachine.transition(dismissed.state, DoseCommand.TIMEOUT, due, dismissed.deadlineAt)!!.state)
    }
    @Test fun lateLiveActionsRequireExplicitHistoryCorrection() {
        val deadline = due + DosePolicy.TOLERANCE_MILLIS
        assertNull(DoseStateMachine.transition(DoseState.PENDING, DoseCommand.TAKE, due, deadline))
        assertNull(DoseStateMachine.transition(DoseState.ALERTING, DoseCommand.SKIP, due, deadline + 1))
        assertNull(DoseStateMachine.transition(DoseState.MISSED, DoseCommand.SNOOZE, due, deadline + 1, requestedSnoozeUntil = deadline + 100))
    }
    @Test fun snoozeAcrossMidnightRetainsOriginalDueAndExtendsGrace() {
        val midnight = 1_789_977_600_000L
        val original = midnight - 60_000
        val snooze = DoseStateMachine.transition(DoseState.ALERTING, DoseCommand.SNOOZE, original, original + 10, requestedSnoozeUntil = midnight + 600_000)!!
        assertEquals(DoseState.SNOOZED, snooze.state)
        assertEquals(midnight + 600_000 + DosePolicy.TOLERANCE_MILLIS, snooze.deadlineAt)
        assertNull(DoseStateMachine.transition(snooze.state, DoseCommand.DELIVER, original, midnight, snooze.deadlineAt, snooze.snoozedUntil))
        assertEquals(DoseState.ALERTING, DoseStateMachine.transition(snooze.state, DoseCommand.DELIVER, original, midnight + 600_000, snooze.deadlineAt, snooze.snoozedUntil)!!.state)
    }
    @Test fun cancellationRejectsStaleCommands() {
        DoseCommand.entries.filter { it != DoseCommand.CANCEL }.forEach {
            assertNull(DoseStateMachine.transition(DoseState.CANCELLED, it, due, due))
        }
    }
}
