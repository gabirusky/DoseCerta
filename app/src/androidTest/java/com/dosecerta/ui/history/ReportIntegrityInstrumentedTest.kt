package com.dosecerta.ui.history

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.local.entity.ReconciliationCheckpoint
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionResult
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReportIntegrityInstrumentedTest {
    @Test fun exportedSnapshotsMatchDatabaseAfterCorrectionArchiveAndFilter() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-report-integrity-${System.nanoTime()}.db"
        val db = Room.databaseBuilder(context, DoseCertaDatabase::class.java, name).build()
        val zone = ZoneId.of("America/Sao_Paulo")
        val start = Instant.parse("2026-10-02T03:00:00Z").toEpochMilli()
        val now = start + 20 * 60_000
        val repository = MedicationRepository(db, Clock.fixed(Instant.ofEpochMilli(now), zone))
        val output = File(context.cacheDir, "qa-reports").apply { mkdirs() }
        try {
            val medication = Medication(name = "Frozen synthetic medication", dosage = "500", unit = "mg",
                pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.DAILY, createdAt = start - 86_400_000)
            val medId = repository.insertMedication(medication)
            val slotId = repository.insertSchedule(Schedule(medicationId = medId, timeInMinutes = 0, daysOfWeek = emptyList(),
                validFrom = start - 86_400_000, zoneId = zone.id))
            val rows = listOf(MedicationStatus.TAKEN, MedicationStatus.SKIPPED, MedicationStatus.MISSED)
                .mapIndexed { index, status -> repository.insertLog(MedicationLog(medicationId = medId, scheduleId = slotId,
                    occurrenceId = "qa-report:$index", scheduledTime = start + index * 60_000, status = status,
                    actualTime = if (status == MedicationStatus.TAKEN) start + 60_000 else null)) }.toMutableList()
            rows += repository.insertLog(MedicationLog(medicationId = medId, scheduleId = null, occurrenceId = "qa-report:extra",
                scheduledTime = start + 4 * 60_000, actualTime = start + 4 * 60_000, status = MedicationStatus.TAKEN, isExtraDose = true))
            val prn = repository.insertMedication(medication.copy(name = "Synthetic as needed", frequency = Frequency.AS_NEEDED))
            rows += repository.insertLog(MedicationLog(medicationId = prn, scheduleId = null, occurrenceId = "qa-report:prn",
                scheduledTime = start + 5 * 60_000, actualTime = start + 5 * 60_000, status = MedicationStatus.TAKEN))
            repository.insertLog(MedicationLog(medicationId = medId, scheduleId = slotId, occurrenceId = "qa-report:outside",
                scheduledTime = start + 86_400_000, actualTime = start + 86_400_000, status = MedicationStatus.TAKEN))
            assertTrue(repository.correctLog(rows.first(), MedicationStatus.TAKEN, start + 3 * 60_000) is DoseActionResult.Success)
            repository.updateMedication(medication.copy(id = medId, name = "Current renamed medication", dosage = "900"))
            db.medicationLogDao().setReconciliationCheckpoint(ReconciliationCheckpoint(reconciledUntil = now))
            repository.archiveMedication(medId)
            val request = ReportRequest(start, start + 86_400_000, zone.id, "pt-BR", null, now)
            val full = ReportSnapshot.capture(repository, request)
            assertEquals(rows.toSet(), full.rows.map { it.id }.toSet())
            assertEquals(5, full.rows.size)
            assertEquals(3, full.summary.eligible); assertEquals(2, full.summary.extra)
            assertEquals(33, full.summary.percentage)
            assertEquals(start + 3 * 60_000, full.rows.single { it.id == rows.first() }.actualTime)
            full.rows.filter { it.medicationId == medId }.forEach {
                assertEquals("Frozen synthetic medication", it.name); assertEquals("500", it.dosage)
                if (it.status != MedicationStatus.TAKEN) assertNull(it.actualTime)
            }
            val filtered = ReportSnapshot.capture(repository, request.copy(statusFilter = MedicationStatus.TAKEN, languageTag = "en"))
            assertEquals(3, filtered.rows.size)
            assertEquals(full.summary, filtered.summary)
            val expected = JSONObject()
            for ((fileName, snapshot) in listOf("integrity-all.pdf" to full, "integrity-taken.pdf" to filtered)) {
                val file = File(output, fileName)
                val result = file.outputStream().use { PdfReportGenerator().write(snapshot, it) }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { pdf -> assertEquals(result.pageCount, pdf.pageCount) }
                }
                expected.put(fileName, JSONObject().put("rowIds", JSONArray(snapshot.rows.map { it.id }))
                    .put("periodRows", snapshot.periodRows.size).put("eligible", snapshot.summary.eligible)
                    .put("extra", snapshot.summary.extra).put("adherencePercentage", snapshot.summary.percentage)
                    .put("pageCount", result.pageCount).put("frozenName", "Frozen synthetic medication")
                    .put("forbiddenName", "Current renamed medication"))
            }
            File(output, "integrity-expected.json").writeText(expected.toString(2))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
