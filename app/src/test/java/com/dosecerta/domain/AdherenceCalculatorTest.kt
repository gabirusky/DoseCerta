package com.dosecerta.domain

import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import org.junit.Assert.*
import org.junit.Test

class AdherenceCalculatorTest {
    private fun log(status: MedicationStatus, extra: Boolean = false) = MedicationLog(medicationId = 1, scheduleId = if (extra) null else 2,
        scheduledTime = 1000, status = status, isExtraDose = extra)
    @Test fun noEligibleOutcomeHasNoPercentage() {
        assertNull(AdherenceCalculator.calculate(emptyList()).percentage)
        assertNull(AdherenceCalculator.calculate(listOf(log(MedicationStatus.PENDING), log(MedicationStatus.TAKEN, true))).percentage)
    }
    @Test fun extraPrnPendingAndCancelledCannotFabricateAdherence() {
        val summary = AdherenceCalculator.calculate(listOf(log(MedicationStatus.TAKEN), log(MedicationStatus.MISSED), log(MedicationStatus.SKIPPED),
            log(MedicationStatus.TAKEN, true), log(MedicationStatus.PENDING), log(MedicationStatus.TAKEN).copy(snapshotFrequency = Frequency.AS_NEEDED),
            log(MedicationStatus.MISSED).copy(state = DoseState.CANCELLED)))
        assertEquals(3, summary.eligible); assertEquals(33, summary.percentage); assertEquals(1, summary.extra)
    }
    @Test fun retainedHistoryStillCountsAfterPermanentParentRemoval() {
        assertEquals(100, AdherenceCalculator.calculate(listOf(log(MedicationStatus.TAKEN).copy(scheduleId = null, medicationId = null))).percentage)
    }
}
