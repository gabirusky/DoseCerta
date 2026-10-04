package com.dosecerta.domain

enum class DoseState { PENDING, ALERTING, SNOOZED, DISMISSED, TAKEN, SKIPPED, MISSED, CANCELLED }
enum class DoseCommand { DELIVER, TAKE, SKIP, SNOOZE, DISMISS, TIMEOUT, CANCEL }

object DosePolicy {
    const val TOLERANCE_MILLIS = 30L * 60 * 1000
    const val MAX_SOUND_MILLIS = 60L * 1000
    const val DEFAULT_SNOOZE_MILLIS = 10L * 60 * 1000
    const val REMINDER_DELAY_MILLIS = 15L * 60 * 1000
}

data class DoseTransition(val state: DoseState, val deadlineAt: Long, val snoozedUntil: Long?, val changed: Boolean)

/** No IO; a timeout cannot overwrite a committed outcome, and repeated actions are idempotent. */
object DoseStateMachine {
    fun transition(
        state: DoseState, command: DoseCommand, originalDueAt: Long, now: Long,
        deadlineAt: Long = originalDueAt + DosePolicy.TOLERANCE_MILLIS,
        snoozedUntil: Long? = null, requestedSnoozeUntil: Long? = null
    ): DoseTransition? {
        val terminal = state in setOf(DoseState.TAKEN, DoseState.SKIPPED, DoseState.MISSED, DoseState.CANCELLED)
        if (terminal) {
            val same = (state == DoseState.TAKEN && command == DoseCommand.TAKE) ||
                (state == DoseState.SKIPPED && command == DoseCommand.SKIP) ||
                (state == DoseState.MISSED && command == DoseCommand.TIMEOUT) ||
                (state == DoseState.CANCELLED && command == DoseCommand.CANCEL)
            return if (same) DoseTransition(state, deadlineAt, snoozedUntil, false) else null
        }
        val effectiveDueAt = snoozedUntil ?: originalDueAt
        val target = when (command) {
            DoseCommand.DELIVER -> if (now < effectiveDueAt || now >= deadlineAt) return null else DoseState.ALERTING
            DoseCommand.TAKE -> if (now >= deadlineAt) return null else DoseState.TAKEN
            DoseCommand.SKIP -> if (now >= deadlineAt) return null else DoseState.SKIPPED
            DoseCommand.SNOOZE -> {
                if (now >= deadlineAt || requestedSnoozeUntil == null || requestedSnoozeUntil <= now) return null
                DoseState.SNOOZED
            }
            DoseCommand.DISMISS -> if (now >= deadlineAt) return null else DoseState.DISMISSED
            DoseCommand.TIMEOUT -> if (now < deadlineAt) return null else DoseState.MISSED
            DoseCommand.CANCEL -> DoseState.CANCELLED
        }
        val newDeadline = if (command == DoseCommand.SNOOZE) maxOf(deadlineAt, requestedSnoozeUntil!! + DosePolicy.TOLERANCE_MILLIS) else deadlineAt
        val newSnooze = if (command == DoseCommand.SNOOZE) requestedSnoozeUntil else snoozedUntil
        return DoseTransition(target, newDeadline, newSnooze, target != state || newDeadline != deadlineAt || newSnooze != snoozedUntil)
    }
}
