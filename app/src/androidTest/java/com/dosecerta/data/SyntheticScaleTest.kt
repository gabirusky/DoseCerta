package com.dosecerta.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
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
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** No patient data. Default database is private to this test, distinct from the app's records. */
@RunWith(AndroidJUnit4::class)
class SyntheticScaleTest {
    @Test fun tenThousandSyntheticLogsMeasureSnapshotAndPersistentAction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seedMain = InstrumentationRegistry.getArguments().getString("seedMainDatabase") == "true"
        if (seedMain) {
            val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            require(com.dosecerta.qa.SyntheticQaDevice.isDedicated(device)) { "Main database fixtures require DoseCerta_QA" }
        }
        val databaseName = if (seedMain) "dose_certa_database" else "scale-fixture-${System.nanoTime()}.db"
        val db = if (seedMain) DoseCertaDatabase.getDatabase(context) else Room.databaseBuilder(context, DoseCertaDatabase::class.java, databaseName).build()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val base = today.minusDays(100)
        val schedules = mutableListOf<Schedule>()
        val fillStart = SystemClock.elapsedRealtimeNanos()
        try {
            // Explicit opt-in is only for a clean QA emulator, with the seed assertion preventing data removal.
            db.withTransaction {
                require(db.medicationDao().getAllActiveMedicationsSync().isEmpty()) { "Scale fixture requires an empty database" }
                require(db.medicationLogDao().reportSnapshot(0, Long.MAX_VALUE).isEmpty()) { "Scale fixture requires no existing history" }
                repeat(50) { index ->
                    val medication = Medication(name = "Synthetic medication ${(index + 1).toString().padStart(2, '0')}", dosage = "500", unit = "mg",
                        pharmaceuticalForm = PharmaceuticalForm.TABLET, frequency = Frequency.DAILY, notes = "Synthetic fixture only. No patient or prescription data.",
                        createdAt = base.atStartOfDay(zone).toInstant().toEpochMilli())
                    val medicationId = db.medicationDao().insert(medication)
                    for (minute in listOf(480, 1200)) {
                        val schedule = Schedule(medicationId = medicationId, timeInMinutes = minute, daysOfWeek = emptyList(), zoneId = zone.id,
                            validFrom = medication.createdAt)
                        schedules += schedule.copy(id = db.scheduleDao().insert(schedule))
                    }
                }
                var row = 0
                for (dayOffset in 0 until 100) for (schedule in schedules) {
                    val local = base.plusDays(dayOffset.toLong()).atTime(schedule.timeInMinutes / 60, schedule.timeInMinutes % 60)
                    val due = local.atZone(zone).toInstant().toEpochMilli()
                    val extra = row % 10 == 0
                    val status = when (row % 10) { 8 -> MedicationStatus.MISSED; 9 -> MedicationStatus.SKIPPED; else -> MedicationStatus.TAKEN }
                    val medication = db.medicationDao().getMedicationByIdSync(schedule.medicationId)!!
                    db.medicationLogDao().insert(MedicationLog(medicationId = medication.id, scheduleId = if (extra) null else schedule.id,
                        scheduledTime = due, originalLocalDateTime = local.toString(), originalZoneId = zone.id,
                        actualTime = if (status == MedicationStatus.TAKEN) due + 120_000 else null, status = status, isExtraDose = extra,
                        occurrenceId = if (extra) "scale-extra:$row" else MedicationRepository.occurrenceKey(schedule, local),
                        snapshotName = medication.name, snapshotDosage = medication.dosage, snapshotUnit = medication.unit,
                        snapshotColor = medication.color, snapshotForm = medication.pharmaceuticalForm, snapshotFrequency = medication.frequency,
                        snapshotNotes = medication.notes, snapshotOrigin = "SYNTHETIC_QA"))
                    row++
                }
                db.medicationLogDao().setReconciliationCheckpoint(ReconciliationCheckpoint(reconciledUntil = now))
            }
            val fillMillis = (SystemClock.elapsedRealtimeNanos() - fillStart) / 1_000_000.0
            val frozenClock = Clock.fixed(Instant.ofEpochMilli(now), zone)
            val repository = MedicationRepository(db, frozenClock)
            val snapshotStart = SystemClock.elapsedRealtimeNanos()
            val rows = repository.reportSnapshot(0, Long.MAX_VALUE)
            val snapshotMillis = (SystemClock.elapsedRealtimeNanos() - snapshotStart) / 1_000_000.0
            assertEquals(10_000, rows.size)
            assertEquals(50, rows.map { it.medicationName }.distinct().size)
            val summaryStart = SystemClock.elapsedRealtimeNanos()
            val summary = com.dosecerta.domain.AdherenceCalculator.calculate(rows.map { it.log })
            val summaryMillis = (SystemClock.elapsedRealtimeNanos() - summaryStart) / 1_000_000.0
            assertEquals(9_000, summary.eligible); assertEquals(1_000, summary.extra)
            val dueLocal = today.atTime(8, 0)
            val due = dueLocal.atZone(zone).toInstant().toEpochMilli()
            // A separate clock measures a live action at the original slot regardless of current test time.
            val actionRepository = MedicationRepository(db, Clock.fixed(Instant.ofEpochMilli(due), zone))
            val occurrence = actionRepository.ensureOccurrence(schedules.first().id, due)!!
            val actionStart = SystemClock.elapsedRealtimeNanos()
            val result = DoseActionCoordinator(actionRepository).take(occurrence.occurrenceId)
            val actionMillis = (SystemClock.elapsedRealtimeNanos() - actionStart) / 1_000_000.0
            assertTrue(result is DoseActionResult.Success && result.changed)
            val nextStart = SystemClock.elapsedRealtimeNanos()
            schedules.forEach { repository.previewOccurrences(it, 1) }
            val nextMillis = (SystemClock.elapsedRealtimeNanos() - nextStart) / 1_000_000.0
            val measurement = JSONObject().apply {
                put("api", Build.VERSION.SDK_INT); put("manufacturer", Build.MANUFACTURER); put("model", Build.MODEL)
                put("device", Build.DEVICE); put("androidBuild", Build.DISPLAY); put("zone", zone.id)
                put("volumeBeforeAction", rows.size); put("medications", 50); put("slots", schedules.size); put("historyDays", 100)
                put("database", databaseName); put("mainDatabaseSeeded", seedMain); put("fixtureWriteMs", fillMillis)
                put("reportSnapshotMs", snapshotMillis); put("sharedSummaryMs", summaryMillis); put("takeCommitMs", actionMillis)
                put("nextDose100SlotsMs", nextMillis); put("coldStartMeasured", false)
                put("measuredAtEpochMs", now); put("syntheticOnly", true)
            }
            File(context.filesDir, "qa-scale-measurement.json").writeText(measurement.toString(2))
            println("DOSECERTA_SCALE_MEASUREMENT ${measurement}")
            assertTrue("Persistent dose action must respond under two seconds", actionMillis < 2000)
            assertTrue("Snapshot retrieval must respond under two seconds", snapshotMillis < 2000)
            assertTrue("Upcoming 100 slots must respond under two seconds", nextMillis < 2000)
            // Action is measured at its slot; seed for UI should contain exactly the requested 10,000 historical rows.
            db.medicationLogDao().delete((result as DoseActionResult.Success).occurrence)
            if (InstrumentationRegistry.getArguments().getString("prepareColdStart") == "true") {
                require(seedMain) { "Cold start preparation requires the guarded main database fixture" }
                com.dosecerta.util.SettingsPreferences(context).setSetupCompleted()
            }
        } finally {
            if (!seedMain) { db.close(); context.deleteDatabase(databaseName) }
        }
    }
}
