package com.dosecerta.ui.history

import android.os.Parcelable
import com.dosecerta.BuildConfig
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.AdherenceCalculator
import com.dosecerta.domain.AdherenceSummary
import kotlinx.parcelize.Parcelize
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Parcelize
data class ReportRequest(
    val startInclusive: Long,
    val endExclusive: Long,
    val zoneId: String,
    val languageTag: String,
    val statusFilter: MedicationStatus?,
    val requestedAt: Long
) : Parcelable {
    init { require(startInclusive < endExclusive); ZoneId.of(zoneId); require(languageTag.isNotBlank()) }
}

data class ReportRow(
    val id: Long, val occurrenceId: String, val medicationId: Long?,
    val name: String, val dosage: String, val unit: String, val form: String,
    val frequency: String, val notes: String, val originalDueAt: Long,
    val actualTime: Long?, val status: MedicationStatus, val extra: Boolean,
    val snapshotOrigin: String
)

data class ReportSnapshot(
    val request: ReportRequest,
    val rows: List<ReportRow>,
    val periodRows: List<ReportRow>,
    val summary: AdherenceSummary,
    val capturedAt: Long,
    val appVersion: String = BuildConfig.VERSION_NAME
) {
    companion object {
        suspend fun capture(repository: MedicationRepository, request: ReportRequest): ReportSnapshot {
            val records = repository.reportSnapshot(request.startInclusive, request.endExclusive)
            val rows = records.map { detail ->
                val log = detail.log
                ReportRow(log.id, log.occurrenceId, log.medicationId,
                    log.customMedicationName ?: detail.medicationName.orEmpty(), detail.dosage.orEmpty(),
                    detail.unit.orEmpty(), detail.pharmaceuticalForm?.name.orEmpty(), detail.frequency?.name.orEmpty(),
                    listOfNotNull(detail.medicationNotes, log.notes).distinct().joinToString("\n"),
                    log.originalDueAt, if (log.status == MedicationStatus.TAKEN) log.actualTime else null,
                    log.status, log.isExtraDose || !log.isScheduledDose, log.snapshotOrigin)
            }
            return ReportSnapshot(request, rows.filter { request.statusFilter == null || it.status == request.statusFilter },
                rows, AdherenceCalculator.calculate(records.map { it.log }), System.currentTimeMillis())
        }
    }
}

/** The report formats only immutable rows, using the language and zone captured at request time. */
class ReportText(request: ReportRequest) {
    val locale: Locale = Locale.forLanguageTag(request.languageTag)
    private val pt = locale.language == "pt"
    private val zone = ZoneId.of(request.zoneId)
    fun text(ptValue: String, enValue: String) = if (pt) ptValue else enValue
    fun date(time: Long): String = java.time.format.DateTimeFormatter.ofPattern("dd MMM uuuu", locale)
        .format(Instant.ofEpochMilli(time).atZone(zone))
    fun dateTime(time: Long): String = java.time.format.DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", locale)
        .format(Instant.ofEpochMilli(time).atZone(zone))
    fun status(status: MedicationStatus): String = when (status) {
        MedicationStatus.TAKEN -> text("Tomado", "Taken")
        MedicationStatus.SKIPPED -> text("Pulado", "Skipped")
        MedicationStatus.MISSED -> text("Não confirmado", "Unconfirmed")
        MedicationStatus.PENDING -> text("Pendente", "Pending")
    }
    fun form(value: String): String = when (value) {
        "TABLET" -> text("Comprimido", "Tablet")
        "CAPSULE" -> text("Cápsula", "Capsule")
        "SYRUP" -> text("Xarope", "Syrup")
        "DROPS" -> text("Gotas", "Drops")
        "INJECTION" -> text("Injetável", "Injection")
        "CREAM" -> text("Pomada", "Cream")
        "SPRAY" -> text("Spray", "Spray")
        "OTHER" -> text("Outra forma", "Other form")
        else -> text("Não informado", "Not recorded")
    }
    fun frequency(value: String): String = when (value) {
        "DAILY" -> text("Diária", "Daily")
        "EVERY_4_HOURS" -> text("A cada 4 horas", "Every 4 hours")
        "EVERY_6_HOURS" -> text("A cada 6 horas", "Every 6 hours")
        "EVERY_8_HOURS" -> text("A cada 8 horas", "Every 8 hours")
        "EVERY_12_HOURS" -> text("A cada 12 horas", "Every 12 hours")
        "WEEKLY" -> text("Semanal", "Weekly")
        "MONTHLY" -> text("Mensal", "Monthly")
        "SELECTED_DAYS" -> text("Dias específicos", "Selected days")
        "AS_NEEDED" -> text("Conforme necessário", "As needed")
        else -> text("Horários definidos", "Scheduled times")
    }
}
