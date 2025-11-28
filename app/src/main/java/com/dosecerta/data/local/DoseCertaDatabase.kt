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
        MedicationLog::class
    ],
    version = 1,
    exportSchema = false
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
        
        fun getDatabase(context: Context): DoseCertaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DoseCertaDatabase::class.java,
                    "dose_certa_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
