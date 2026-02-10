package com.dosecerta.data.local.entity

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import kotlinx.parcelize.Parcelize

/**
 * Room entity representing a medication in the database.
 */
@Parcelize
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    val dosage: String,          // e.g., "500"
    val unit: String,            // e.g., "mg", "ml", "gotas"
    val pharmaceuticalForm: PharmaceuticalForm,
    val frequency: Frequency,
    val notes: String? = null,
    val color: Int = 0xFF00897B.toInt(), // Default teal color
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable


// Type converters for Room
class MedicationTypeConverters {
    @TypeConverter
    fun fromPharmaceuticalForm(form: PharmaceuticalForm): String {
        return form.name
    }
    
    @TypeConverter
    fun toPharmaceuticalForm(value: String): PharmaceuticalForm {
        return PharmaceuticalForm.valueOf(value)
    }
    
    @TypeConverter
    fun fromFrequency(frequency: Frequency): String {
        return frequency.name
    }
    
    @TypeConverter
    fun toFrequency(value: String): Frequency {
        return Frequency.valueOf(value)
    }
}
