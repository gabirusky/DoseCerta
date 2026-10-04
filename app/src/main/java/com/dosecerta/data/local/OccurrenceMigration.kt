package com.dosecerta.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Preserve every v3 row/ID. Unknowable old prescription versions are explicitly reconstructed. */
object OccurrenceMigration {
    /** Repository v1 (b1cfb38) predates custom colors; preserve its medication and log IDs. */
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medications ADD COLUMN color INTEGER NOT NULL DEFAULT -16742021")
        }
    }
    /** Repository v2 (c0896bd) predates avulsa/custom intake metadata. v4 rebuild makes FKs nullable. */
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medication_logs ADD COLUMN isExtraDose INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE medication_logs ADD COLUMN customMedicationName TEXT")
        }
    }
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val migrationTime = System.currentTimeMillis()
            db.execSQL("ALTER TABLE schedules ADD COLUMN recurrenceKind TEXT NOT NULL DEFAULT 'DAILY'")
            db.execSQL("ALTER TABLE schedules ADD COLUMN monthDay INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE schedules ADD COLUMN zoneId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE schedules ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE schedules ADD COLUMN validFrom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE schedules ADD COLUMN validUntil INTEGER")
            db.execSQL("UPDATE schedules SET recurrenceKind = CASE WHEN daysOfWeek = '' THEN CASE WHEN (SELECT frequency FROM medications WHERE id = schedules.medicationId) IN ('EVERY_4_HOURS','EVERY_6_HOURS','EVERY_8_HOURS','EVERY_12_HOURS') THEN 'INTERVAL' ELSE 'DAILY' END ELSE 'SELECTED_DAYS' END, validFrom = ?", arrayOf(migrationTime))
            db.execSQL("""CREATE TABLE medication_logs_v4 (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                medicationId INTEGER, scheduleId INTEGER, scheduledTime INTEGER NOT NULL,
                actualTime INTEGER, status TEXT NOT NULL, notes TEXT,
                isExtraDose INTEGER NOT NULL, customMedicationName TEXT,
                originalDueAt INTEGER NOT NULL, scheduleVersion INTEGER NOT NULL,
                originalLocalDateTime TEXT NOT NULL, originalZoneId TEXT NOT NULL,
                occurrenceId TEXT NOT NULL, state TEXT NOT NULL, deadlineAt INTEGER NOT NULL,
                snoozedUntil INTEGER, deliveredAt INTEGER, reminderAt INTEGER, schedulerHandle TEXT,
                isScheduledDose INTEGER NOT NULL,
                snapshotName TEXT, snapshotDosage TEXT, snapshotUnit TEXT, snapshotColor INTEGER,
                snapshotForm TEXT, snapshotFrequency TEXT, snapshotNotes TEXT, snapshotOrigin TEXT NOT NULL,
                FOREIGN KEY(medicationId) REFERENCES medications(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(scheduleId) REFERENCES schedules(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )""")
            // A single canonical row per old slot; retain conflicting audit rows with opaque identities.
            // Precedence: TAKEN, SKIPPED, MISSED, PENDING; most recent ID breaks equal-status ties.
            db.execSQL("""CREATE TEMP TABLE legacy_winners AS
                SELECT l.id FROM medication_logs l
                WHERE l.scheduleId IS NOT NULL AND l.isExtraDose = 0 AND l.id = (
                    SELECT candidate.id FROM medication_logs candidate
                    WHERE candidate.scheduleId = l.scheduleId AND candidate.scheduledTime = l.scheduledTime AND candidate.isExtraDose = 0
                    ORDER BY CASE candidate.status WHEN 'TAKEN' THEN 0 WHEN 'SKIPPED' THEN 1 WHEN 'MISSED' THEN 2 ELSE 3 END, candidate.id DESC LIMIT 1)
            """)
            db.execSQL("""INSERT INTO medication_logs_v4 (
                id, medicationId, scheduleId, scheduledTime, actualTime, status, notes,
                isExtraDose, customMedicationName, originalDueAt, scheduleVersion, originalLocalDateTime, originalZoneId, occurrenceId,
                state, deadlineAt, snoozedUntil, deliveredAt, reminderAt, schedulerHandle, isScheduledDose,
                snapshotName, snapshotDosage, snapshotUnit, snapshotColor, snapshotForm, snapshotFrequency, snapshotNotes, snapshotOrigin)
                SELECT l.id, l.medicationId, l.scheduleId, l.scheduledTime,
                    CASE WHEN l.status = 'TAKEN' THEN l.actualTime ELSE NULL END,
                    l.status, l.notes, l.isExtraDose, l.customMedicationName, l.scheduledTime, 1, '', '',
                    CASE WHEN l.scheduleId IS NULL OR l.isExtraDose = 1 THEN 'legacy-extra:' || l.id
                        WHEN l.id IN (SELECT id FROM legacy_winners) THEN 'dose:' || l.scheduleId || ':1:' || l.scheduledTime
                        ELSE 'legacy-duplicate:' || l.id END,
                    CASE WHEN l.scheduleId IS NOT NULL AND l.isExtraDose = 0 AND l.id NOT IN (SELECT id FROM legacy_winners) THEN 'CANCELLED'
                        WHEN l.status IN ('TAKEN','SKIPPED','MISSED') THEN l.status ELSE 'PENDING' END,
                    l.scheduledTime + 1800000, NULL, NULL, NULL, NULL,
                    CASE WHEN l.scheduleId IS NOT NULL AND l.isExtraDose = 0 AND l.id IN (SELECT id FROM legacy_winners) AND COALESCE(m.frequency,'') != 'AS_NEEDED' THEN 1 ELSE 0 END,
                    COALESCE(l.customMedicationName,m.name),m.dosage,m.unit,m.color,m.pharmaceuticalForm,m.frequency,m.notes,'LEGACY_V3_RECONSTRUCTED'
                FROM medication_logs l LEFT JOIN medications m ON m.id = l.medicationId
            """)
            val zone = ZoneId.systemDefault()
            val seenSlots = mutableSetOf<String>()
            db.query("""SELECT l.id, l.scheduleId, l.originalDueAt, l.occurrenceId, s.timeInMinutes
                FROM medication_logs_v4 l LEFT JOIN schedules s ON s.id = l.scheduleId
                ORDER BY CASE l.state WHEN 'TAKEN' THEN 0 WHEN 'SKIPPED' THEN 1 WHEN 'MISSED' THEN 2 ELSE 3 END, l.id DESC""").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val localAtInstant = Instant.ofEpochMilli(cursor.getLong(2)).atZone(zone).toLocalDateTime()
                    val scheduleRequested = if (!cursor.isNull(4)) {
                        val minutes = cursor.getInt(4)
                        localAtInstant.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60))
                    } else localAtInstant.withSecond(0).withNano(0)
                    // A v3 slot may have been edited since this historical dose. Never replace
                    // its old wall-clock minute with today's schedule. Recover a DST request only
                    // when that request resolves to exactly the retained historical instant.
                    val localSlot = if (scheduleRequested.atZone(zone).toInstant().toEpochMilli() == cursor.getLong(2))
                        scheduleRequested else localAtInstant.withSecond(0).withNano(0)
                    val oldIdentity = cursor.getString(3)
                    val newIdentity = if (oldIdentity.startsWith("dose:")) "dose:${cursor.getLong(1)}:1:$localSlot" else oldIdentity
                    val duplicate = oldIdentity.startsWith("dose:") && !seenSlots.add(newIdentity)
                    db.execSQL("UPDATE medication_logs_v4 SET occurrenceId = ?, originalLocalDateTime = ?, originalZoneId = ? WHERE id = ?",
                        arrayOf(if (duplicate) "legacy-duplicate:$id" else newIdentity, localSlot.toString(), zone.id, id))
                    if (duplicate) db.execSQL("UPDATE medication_logs_v4 SET state = 'CANCELLED', isScheduledDose = 0 WHERE id = ?", arrayOf(id))
                }
            }
            db.execSQL("DROP TABLE legacy_winners")
            db.execSQL("DROP TABLE medication_logs")
            db.execSQL("ALTER TABLE medication_logs_v4 RENAME TO medication_logs")
            db.execSQL("CREATE INDEX index_medication_logs_medicationId ON medication_logs(medicationId)")
            db.execSQL("CREATE INDEX index_medication_logs_scheduleId ON medication_logs(scheduleId)")
            db.execSQL("CREATE INDEX index_medication_logs_scheduledTime ON medication_logs(scheduledTime)")
            db.execSQL("CREATE INDEX index_medication_logs_originalDueAt ON medication_logs(originalDueAt)")
            db.execSQL("CREATE INDEX index_medication_logs_state ON medication_logs(state)")
            db.execSQL("CREATE UNIQUE INDEX index_medication_logs_occurrenceId ON medication_logs(occurrenceId)")
            db.execSQL("CREATE TABLE occurrence_suppressions (occurrenceId TEXT NOT NULL PRIMARY KEY, deletedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE reconciliation_checkpoint (id INTEGER NOT NULL PRIMARY KEY, reconciledUntil INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE medication_save_receipts (requestId TEXT NOT NULL PRIMARY KEY, medicationId INTEGER NOT NULL)")
            db.execSQL("INSERT INTO reconciliation_checkpoint(id,reconciledUntil) VALUES(1,?)", arrayOf(migrationTime))
        }
    }
}
