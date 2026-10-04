package com.dosecerta.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.OccurrenceMigration
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class OccurrencePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private class MutableClock(var now: Instant = Instant.parse("2026-09-29T07:00:00Z")) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }
    private fun medication() = Medication(name = "Fixture medicine", dosage = "500", unit = "mg", pharmaceuticalForm = PharmaceuticalForm.TABLET,
        frequency = Frequency.DAILY, notes = "Original prescription")
    private fun slot() = Schedule(medicationId = 0, timeInMinutes = 480, daysOfWeek = emptyList(), zoneId = "UTC")
    private suspend fun withDatabase(test: suspend (DoseCertaDatabase, MedicationRepository, MutableClock) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, DoseCertaDatabase::class.java).build()
        val clock = MutableClock()
        try { test(db, MedicationRepository(db, clock), clock) } finally { db.close() }
    }
    private val due get() = Instant.parse("2026-09-29T08:00:00Z").toEpochMilli()

    @Test fun uniqueCreationConcurrentOutcomesAndRepeatedActions() = runBlocking {
        withDatabase { db, repository, clock ->
            val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            val scheduleId = saved.activeSchedules.single().id
            val creations = coroutineScope { (1..20).map { async(Dispatchers.IO) { repository.ensureOccurrence(scheduleId, due)!! } }.awaitAll() }
            assertEquals(1, creations.map { it.id }.distinct().size)
            assertEquals(1, repository.reportSnapshot(due, due + 1).size)
            clock.now = Instant.ofEpochMilli(due)
            val coordinator = DoseActionCoordinator(repository)
            assertEquals(DoseState.ALERTING, (coordinator.deliver(creations.first().occurrenceId) as DoseActionResult.Success).occurrence.state)
            val outcomes = coroutineScope { listOf(async(Dispatchers.IO) { coordinator.take(creations.first().occurrenceId) }, async(Dispatchers.IO) { coordinator.skip(creations.first().occurrenceId) }).awaitAll() }
            assertEquals(1, outcomes.count { it is DoseActionResult.Success && it.changed })
            assertEquals(1, outcomes.count { it is DoseActionResult.Rejected })
            val committed = repository.getOccurrence(creations.first().occurrenceId)!!
            val repeat = if (committed.state == DoseState.TAKEN) coordinator.take(committed.occurrenceId) else coordinator.skip(committed.occurrenceId)
            assertFalse((repeat as DoseActionResult.Success).changed)
            clock.now = clock.now.plusSeconds(1900)
            assertTrue(coordinator.timeout(committed.occurrenceId) is DoseActionResult.Rejected)
            assertEquals(committed.state, db.medicationLogDao().getOccurrence(committed.occurrenceId)!!.state)
            val tomorrow = repository.ensureOccurrence(scheduleId, due + 86_400_000)!!
            clock.now = Instant.ofEpochMilli(tomorrow.deadlineAt)
            val deadlineRace = coroutineScope { listOf(
                async(Dispatchers.IO) { coordinator.take(tomorrow.occurrenceId) },
                async(Dispatchers.IO) { coordinator.timeout(tomorrow.occurrenceId) }
            ).awaitAll() }
            assertEquals(1, deadlineRace.count { it is DoseActionResult.Success && it.changed })
            assertEquals(DoseState.MISSED, repository.getOccurrence(tomorrow.occurrenceId)!!.state)
        }
    }

    @Test fun saveRollsBackAfterDatabaseFailureAndCreatedAtIsPreserved() = runBlocking {
        withDatabase { db, repository, _ ->
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_fixture BEFORE INSERT ON schedules BEGIN SELECT RAISE(ABORT, 'fixture failure'); END")
            try { repository.saveMedicationWithSchedules(medication(), listOf(slot())); fail("Should fail") } catch (_: Exception) { }
            assertTrue(repository.getAllActiveMedicationsSync().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_fixture")
            val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            val before = repository.getMedicationByIdSync(saved.id)!!
            try {
                repository.saveMedicationWithSchedules(before, listOf(slot(), slot()))
                fail("Duplicate wall-clock slots must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(1, repository.getSchedulesForMedicationSync(saved.id).size)
            repository.saveMedicationWithSchedules(before.copy(name = "Edited", createdAt = 0), listOf(slot()))
            assertEquals(before.createdAt, repository.getMedicationByIdSync(saved.id)!!.createdAt)
        }
    }

    @Test fun snapshotsStayFrozenAcrossEditArchiveAndParentDeletion() = runBlocking {
        withDatabase { _, repository, clock ->
            val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            val occurrence = repository.ensureOccurrence(saved.activeSchedules.single().id, due)!!
            clock.now = Instant.ofEpochMilli(due)
            assertTrue(DoseActionCoordinator(repository).take(occurrence.occurrenceId) is DoseActionResult.Success)
            val medication = repository.getMedicationByIdSync(saved.id)!!
            repository.saveMedicationWithSchedules(medication.copy(name = "Changed", dosage = "250", notes = "New notes"), listOf(slot().copy(timeInMinutes = 600)))
            assertEquals("Fixture medicine", repository.reportSnapshot(due, due + 1).single().medicationName)
            assertEquals("500", repository.reportSnapshot(due, due + 1).single().dosage)
            assertEquals("Original prescription", repository.reportSnapshot(due, due + 1).single().medicationNotes)
            val editedSlot = repository.getSchedulesForMedicationSync(saved.id).single()
            val future = repository.ensureOccurrence(editedSlot.id, due + 7_200_000)!!
            repository.saveMedicationWithSchedules(repository.getMedicationByIdSync(saved.id)!!.copy(frequency = Frequency.AS_NEEDED), emptyList())
            assertTrue(repository.getSchedulesForMedicationSync(saved.id).isEmpty())
            assertEquals(DoseState.CANCELLED, repository.getOccurrence(future.occurrenceId)!!.state)
            assertEquals(DoseState.TAKEN, repository.getOccurrence(occurrence.occurrenceId)!!.state)
            repository.archiveMedication(saved.id)
            repository.permanentlyDeleteMedication(saved.id, deleteHistory = false)
            val retained = repository.reportSnapshot(due, due + 1).single()
            assertNull(retained.log.medicationId); assertNull(retained.log.scheduleId)
            assertEquals("Fixture medicine", retained.medicationName)
            assertEquals(100, AdherenceCalculator.calculate(listOf(retained.log)).percentage)
            val report = com.dosecerta.ui.history.ReportSnapshot.capture(repository,
                com.dosecerta.ui.history.ReportRequest(due, due + 1, "UTC", "pt-BR", null, due))
            assertFalse("Deleting the parent must not label a scheduled dose as extra", report.rows.single().extra)
            assertEquals(0, report.summary.extra)
        }
    }

    @Test fun deletedHistoryCannotReappearAndNextPreviewSkipsSuppressedSlot() = runBlocking {
        withDatabase { _, repository, clock ->
            val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            val schedule = saved.activeSchedules.single()
            val occurrence = repository.ensureOccurrence(schedule.id, due)!!
            repository.deleteLog(occurrence)
            assertNull(repository.ensureOccurrence(schedule.id, due))
            assertEquals("2026-09-30T08:00:00Z", Instant.ofEpochMilli(repository.previewOccurrences(schedule, 1).single().originalDueAt).toString())
            clock.now = Instant.parse("2026-10-02T09:00:00Z")
            repository.reconcileBacklog()
            assertTrue(repository.reportSnapshot(due, due + 1).isEmpty())
            repository.permanentlyDeleteMedication(saved.id, deleteHistory = true)
            assertNull(repository.getMedicationByIdSync(saved.id))
            assertTrue(repository.reportSnapshot(0, Long.MAX_VALUE).isEmpty())
            clock.now = clock.now.plusSeconds(86_400 * 10)
            repository.reconcileBacklog()
            assertNull(repository.ensureOccurrence(schedule.id, due))
            assertTrue(repository.reportSnapshot(0, Long.MAX_VALUE).isEmpty())
        }
    }

    @Test fun graceSnoozeAndHistoryCorrectionRetainOriginalInstant() = runBlocking {
        withDatabase { _, repository, clock ->
            val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            val occurrence = repository.ensureOccurrence(saved.activeSchedules.single().id, due)!!
            val coordinator = DoseActionCoordinator(repository)
            clock.now = Instant.ofEpochMilli(due)
            coordinator.deliver(occurrence.occurrenceId)
            assertTrue(coordinator.timeout(occurrence.occurrenceId) is DoseActionResult.Rejected)
            val nextDay = Instant.parse("2026-09-30T00:10:00Z").toEpochMilli()
            val snoozed = (coordinator.snooze(occurrence.occurrenceId, nextDay) as DoseActionResult.Success).occurrence
            assertEquals(due, snoozed.originalDueAt); assertEquals(occurrence.occurrenceId, snoozed.occurrenceId)
            clock.now = Instant.ofEpochMilli(snoozed.deadlineAt)
            assertTrue(coordinator.take(snoozed.occurrenceId) is DoseActionResult.Rejected)
            assertEquals(DoseState.MISSED, (coordinator.timeout(snoozed.occurrenceId) as DoseActionResult.Success).occurrence.state)
            assertTrue(repository.correctLog(snoozed.id, MedicationStatus.TAKEN, null) is DoseActionResult.Rejected)
            val corrected = (repository.correctLog(snoozed.id, MedicationStatus.TAKEN, due + 120000) as DoseActionResult.Success).occurrence
            assertEquals(due + 120000, corrected.actualTime); assertEquals(due, corrected.scheduledTime)
            val skipped = (repository.correctLog(snoozed.id, MedicationStatus.SKIPPED, due + 120000) as DoseActionResult.Success).occurrence
            assertNull(skipped.actualTime)
        }
    }

    @Test fun recurrenceRevisionCancelsPendingButDoesNotRewriteCompletedDoses() = runBlocking {
        withDatabase { _, repository, clock ->
            val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            val old = saved.activeSchedules.single()
            val occurrence = repository.ensureOccurrence(old.id, due)!!
            val changed = repository.saveMedicationWithSchedules(repository.getMedicationByIdSync(saved.id)!!, listOf(slot().copy(timeInMinutes = 600)))
            assertEquals(listOf(old.id), changed.retiredScheduleIds)
            assertEquals(DoseState.CANCELLED, repository.getOccurrence(occurrence.occurrenceId)!!.state)
            assertTrue(DoseActionCoordinator(repository).deliver(occurrence.occurrenceId) is DoseActionResult.Rejected)
            assertEquals(2L, changed.activeSchedules.single().version)
            clock.now = Instant.parse("2026-09-30T11:00:00Z")
            repository.reconcileBacklog()
            val history = repository.reportSnapshot(0, Long.MAX_VALUE)
            assertTrue(history.all { it.log.originalLocalDateTime.endsWith("T10:00") })
            assertTrue(history.all { it.log.state == DoseState.MISSED && it.log.reminderAt == null })
        }
    }

    @Test fun extraDoseRequestTokenIsIdempotent() = runBlocking {
        withDatabase { _, repository, _ ->
            val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            val ids = coroutineScope { (1..20).map { async(Dispatchers.IO) { repository.recordExtraDose(saved.id, "same-request") } }.awaitAll() }
            assertEquals(1, ids.distinct().size)
            assertEquals(1, repository.reportSnapshot(0, Long.MAX_VALUE).size)
            assertNull(AdherenceCalculator.calculate(repository.reportSnapshot(0, Long.MAX_VALUE).map { it.log }).percentage)
            val scheduled = repository.ensureOccurrence(saved.activeSchedules.single().id, due)!!
            assertTrue(repository.correctLog(scheduled.id, MedicationStatus.SKIPPED, null) is DoseActionResult.Success)
            val filtered = com.dosecerta.ui.history.ReportSnapshot.capture(repository,
                com.dosecerta.ui.history.ReportRequest(0, due + 1, "UTC", "en", MedicationStatus.TAKEN, due))
            assertEquals(1, filtered.rows.size)
            assertEquals(2, filtered.periodRows.size)
            assertEquals(0, filtered.summary.percentage)
            assertEquals(1, filtered.summary.extra)
        }
    }

    @Test fun medicationSaveRequestTokenSurvivesCommitRetryWithoutNewRevision() = runBlocking {
        withDatabase { _, repository, _ ->
            val first = repository.saveMedicationWithSchedules(medication(), listOf(slot()), "same-save-request")
            val retry = repository.saveMedicationWithSchedules(medication(), listOf(slot()), "same-save-request")
            assertEquals(first.id, retry.id)
            assertEquals(first.activeSchedules.map { it.id }, retry.activeSchedules.map { it.id })
            assertEquals(1, repository.getAllActiveMedicationsSync().size)
            assertEquals(1L, retry.activeSchedules.single().version)
        }
    }

    @Test fun newPrescriptionAfterBackwardsClockChangeHasKnownBacklog() = runBlocking {
        withDatabase { _, repository, clock ->
            repository.reconcileBacklog(Instant.parse("2026-10-10T07:00:00Z").toEpochMilli())
            clock.now = Instant.parse("2026-09-29T07:00:00Z")
            repository.saveMedicationWithSchedules(medication(), listOf(slot()))
            clock.now = Instant.parse("2026-09-30T09:00:00Z")
            repository.reconcileBacklog()
            val first = repository.reportSnapshot(0, Long.MAX_VALUE)
            assertEquals(2, first.size)
            assertTrue(first.all { it.log.state == DoseState.MISSED })
            repository.reconcileBacklog()
            assertEquals(first.map { it.log.id }, repository.reportSnapshot(0, Long.MAX_VALUE).map { it.log.id })
        }
    }

    @Test fun deviceZoneChangeKeepsCapturedLocalSlotIdentityAndPinnedPreview() = runBlocking {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            withDatabase { _, repository, _ ->
                val saved = repository.saveMedicationWithSchedules(medication(), listOf(slot().copy(zoneId = "")))
                val schedule = saved.activeSchedules.single()
                val captured = repository.ensureOccurrence(schedule.id, due)!!
                TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
                val recalculatedDue = Instant.parse("2026-09-29T12:00:00Z").toEpochMilli()
                assertEquals(captured.id, repository.ensureOccurrence(schedule.id, recalculatedDue)!!.id)
                val preview = repository.previewOccurrences(schedule, 1).single()
                assertTrue(preview.isPinned); assertEquals(due, preview.originalDueAt)
                assertEquals(1, repository.reportSnapshot(0, Long.MAX_VALUE).size)
            }
        } finally { TimeZone.setDefault(originalZone) }
    }

    @Test fun versionThreeMigrationPreservesRowsDeduplicatesAndReplacesCascade() = runBlocking {
        val name = "migration-v3-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE medications (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,dosage TEXT NOT NULL,unit TEXT NOT NULL,pharmaceuticalForm TEXT NOT NULL,frequency TEXT NOT NULL,notes TEXT,color INTEGER NOT NULL,isActive INTEGER NOT NULL,createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE schedules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,medicationId INTEGER NOT NULL,timeInMinutes INTEGER NOT NULL,daysOfWeek TEXT NOT NULL,isActive INTEGER NOT NULL,FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_schedules_medicationId ON schedules(medicationId)")
            old.execSQL("CREATE TABLE medication_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,medicationId INTEGER,scheduleId INTEGER,scheduledTime INTEGER NOT NULL,actualTime INTEGER,status TEXT NOT NULL,notes TEXT,isExtraDose INTEGER NOT NULL,customMedicationName TEXT,FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(scheduleId) REFERENCES schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_medication_logs_medicationId ON medication_logs(medicationId)")
            old.execSQL("CREATE INDEX index_medication_logs_scheduleId ON medication_logs(scheduleId)")
            old.execSQL("CREATE INDEX index_medication_logs_scheduledTime ON medication_logs(scheduledTime)")
            old.execSQL("INSERT INTO medications VALUES(17,'Original medication','500','mg','TABLET','EVERY_8_HOURS','Old notes',-16742293,1,1000)")
            old.execSQL("INSERT INTO schedules VALUES(23,17,480,'',1)")
            old.execSQL("INSERT INTO medication_logs VALUES(101,17,23,2000,2100,'TAKEN','dose note',0,NULL)")
            old.execSQL("INSERT INTO medication_logs VALUES(102,17,23,2000,2200,'MISSED',NULL,0,NULL)")
            old.execSQL("INSERT INTO medication_logs VALUES(103,NULL,NULL,3000,3000,'TAKEN',NULL,1,'Custom extra')")
            // Same physical legacy schedule was edited from 08:00 to another time: these are
            // two distinct historical slots, not duplicate confirmations of one occurrence.
            old.execSQL("INSERT INTO medication_logs VALUES(104,17,23,28800000,28920000,'TAKEN',NULL,0,NULL)")
            old.execSQL("INSERT INTO medication_logs VALUES(105,17,23,72000000,72120000,'TAKEN',NULL,0,NULL)")
            old.version = 3
        }
        val db = Room.databaseBuilder(context, DoseCertaDatabase::class.java, name).addMigrations(OccurrenceMigration.FROM_3_TO_4).build()
        try {
            val repository = MedicationRepository(db)
            val details = repository.reportSnapshot(0, Long.MAX_VALUE)
            assertEquals(listOf(101L, 103L, 104L, 105L), details.map { it.log.id })
            assertEquals("Original medication", details.first().medicationName)
            assertEquals("LEGACY_V3_RECONSTRUCTED", details.first().log.snapshotOrigin)
            assertEquals(RecurrenceKind.INTERVAL, repository.getScheduleById(23)!!.recurrenceKind)
            assertEquals(DoseState.CANCELLED, db.medicationLogDao().getById(102)!!.state)
            assertNull(db.medicationLogDao().getById(102)!!.actualTime)
            assertEquals(2000L, details.first().log.originalDueAt)
            assertNotEquals(details.first { it.log.id == 104L }.log.originalLocalDateTime, details.first { it.log.id == 105L }.log.originalLocalDateTime)
            db.scheduleDao().delete(repository.getScheduleById(23)!!)
            assertEquals(101L, repository.reportSnapshot(0, Long.MAX_VALUE).first().log.id)
            assertNull(repository.reportSnapshot(0, Long.MAX_VALUE).first().log.scheduleId)
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM medication_logs").use { cursor -> cursor.moveToFirst(); assertEquals(5, cursor.getInt(0)) }
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun repositoryVersionsOneAndTwoUpgradeWithoutDataLoss() = runBlocking {
        for (version in 1..2) {
            val name = "migration-v$version-${System.nanoTime()}.db"
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val colorColumn = if (version == 2) ",color INTEGER NOT NULL" else ""
                old.execSQL("CREATE TABLE medications (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT NOT NULL,dosage TEXT NOT NULL,unit TEXT NOT NULL,pharmaceuticalForm TEXT NOT NULL,frequency TEXT NOT NULL,notes TEXT,isActive INTEGER NOT NULL,createdAt INTEGER NOT NULL$colorColumn)")
                old.execSQL("CREATE TABLE schedules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,medicationId INTEGER NOT NULL,timeInMinutes INTEGER NOT NULL,daysOfWeek TEXT NOT NULL,isActive INTEGER NOT NULL,FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_schedules_medicationId ON schedules(medicationId)")
                old.execSQL("CREATE TABLE medication_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,medicationId INTEGER NOT NULL,scheduleId INTEGER NOT NULL,scheduledTime INTEGER NOT NULL,actualTime INTEGER,status TEXT NOT NULL,notes TEXT,FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(scheduleId) REFERENCES schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_medication_logs_medicationId ON medication_logs(medicationId)")
                old.execSQL("CREATE INDEX index_medication_logs_scheduleId ON medication_logs(scheduleId)")
                old.execSQL("CREATE INDEX index_medication_logs_scheduledTime ON medication_logs(scheduledTime)")
                val colorValue = if (version == 2) ",-16742021" else ""
                old.execSQL("INSERT INTO medications VALUES(17,'Original v$version','500','mg','TABLET','DAILY',NULL,1,1000$colorValue)")
                old.execSQL("INSERT INTO schedules VALUES(23,17,480,'',1)")
                old.execSQL("INSERT INTO medication_logs VALUES(101,17,23,2000,2100,'TAKEN',NULL)")
                old.version = version
            }
            val db = Room.databaseBuilder(context, DoseCertaDatabase::class.java, name)
                .addMigrations(OccurrenceMigration.FROM_1_TO_2, OccurrenceMigration.FROM_2_TO_3, OccurrenceMigration.FROM_3_TO_4).build()
            try {
                val repository = MedicationRepository(db)
                val detail = repository.reportSnapshot(0, Long.MAX_VALUE).single()
                assertEquals(101L, detail.log.id); assertEquals(17L, detail.log.medicationId); assertEquals(23L, detail.log.scheduleId)
                assertEquals("Original v$version", detail.medicationName); assertEquals(2100L, detail.log.actualTime)
                assertEquals(-16742021, repository.getMedicationByIdSync(17)!!.color)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }
}
