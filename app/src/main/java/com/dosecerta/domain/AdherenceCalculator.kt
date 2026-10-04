package com.dosecerta.domain

import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus

data class AdherenceSummary(val taken: Int, val missed: Int, val skipped: Int, val extra: Int) {
    val eligible: Int get() = taken + missed + skipped
    val percentage: Int? get() = if (eligible == 0) null else (taken.toLong() * 100 / eligible).toInt()
}

object AdherenceCalculator {
    fun calculate(logs: List<MedicationLog>): AdherenceSummary {
        val eligible = logs.filter {
            !it.isExtraDose && it.isScheduledDose && it.snapshotFrequency != com.dosecerta.data.model.Frequency.AS_NEEDED &&
                it.state in setOf(DoseState.TAKEN, DoseState.SKIPPED, DoseState.MISSED)
        }
        return AdherenceSummary(
            eligible.count { it.status == MedicationStatus.TAKEN },
            eligible.count { it.status == MedicationStatus.MISSED },
            eligible.count { it.status == MedicationStatus.SKIPPED },
            logs.count { (it.isExtraDose || !it.isScheduledDose) && it.state == DoseState.TAKEN }
        )
    }
}
