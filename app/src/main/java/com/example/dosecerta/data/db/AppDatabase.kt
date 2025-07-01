package com.example.dosecerta.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.data.model.Reminder

@Database(
    entities = [Medication::class, Reminder::class, MedicationLog::class],
    version = 3, // INCREMENTED version from 2 to 3
    exportSchema = true // Recommended to set to true and check in schema files
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun reminderDao(): ReminderDao
    abstract fun medicationLogDao(): MedicationLogDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dosecerta_database" // Name of the database file
                )
                // REMOVED fallbackToDestructiveMigration()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Add both migrations
                .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }

        // Migration from version 1 to 2: Add frequency columns to medications table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add frequencyType column (TEXT, as it stores the Enum name)
                db.execSQL("ALTER TABLE medications ADD COLUMN frequencyType TEXT NOT NULL DEFAULT 'DAILY'")
                // Add frequencyIntervalDays column (INTEGER, nullable)
                db.execSQL("ALTER TABLE medications ADD COLUMN frequencyIntervalDays INTEGER")
                // Add frequencyDaysOfWeek column (TEXT, nullable)
                db.execSQL("ALTER TABLE medications ADD COLUMN frequencyDaysOfWeek TEXT")
            }
        }
        
        // Migration from version 2 to 3: Add startDate column
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // startDate stored as INTEGER (Timestamp)
                db.execSQL("ALTER TABLE medications ADD COLUMN startDate INTEGER")
            }
        }
    }
} 