package com.dosecerta.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dosecerta.data.local.dao.MedicationDao
import com.dosecerta.data.local.dao.MedicationLogDao
import com.dosecerta.data.local.dao.ScheduleDao
import com.dosecerta.data.local.entity.*

/**
 * Room Database for Dose Certa app.
 */
@Database(
    entities = [
        Medication::class,
        Schedule::class,
        MedicationLog::class,
        OccurrenceSuppression::class,
        ReconciliationCheckpoint::class,
        MedicationSaveReceipt::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(
    MedicationTypeConverters::class,
    ScheduleTypeConverters::class,
    MedicationLogTypeConverters::class
)
abstract class DoseCertaDatabase : RoomDatabase() {
    
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun medicationLogDao(): MedicationLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: DoseCertaDatabase? = null

        /** Compatibility for repository callers that already obtained this singleton's three DAOs. */
        fun initializedInstance(): DoseCertaDatabase? = INSTANCE
        
        fun getDatabase(context: Context): DoseCertaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DoseCertaDatabase::class.java,
                    "dose_certa_database"
                )
                    .addMigrations(OccurrenceMigration.FROM_1_TO_2, OccurrenceMigration.FROM_2_TO_3, OccurrenceMigration.FROM_3_TO_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
