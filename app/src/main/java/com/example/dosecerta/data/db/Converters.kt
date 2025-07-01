package com.example.dosecerta.data.db

import androidx.room.TypeConverter
import com.example.dosecerta.data.model.DosageForm
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.StrengthUnit
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromDosageForm(value: String?): DosageForm? {
        return value?.let { DosageForm.valueOf(it) }
    }

    @TypeConverter
    fun dosageFormToString(dosageForm: DosageForm?): String? {
        return dosageForm?.name
    }

    @TypeConverter
    fun fromStrengthUnit(value: String?): StrengthUnit? {
        return value?.let { StrengthUnit.valueOf(it) }
    }

    @TypeConverter
    fun strengthUnitToString(strengthUnit: StrengthUnit?): String? {
        return strengthUnit?.name
    }

    @TypeConverter
    fun fromLogStatus(value: String?): LogStatus? {
        return value?.let { LogStatus.valueOf(it) }
    }

    @TypeConverter
    fun logStatusToString(logStatus: LogStatus?): String? {
        return logStatus?.name
    }

    @TypeConverter
    fun fromFrequencyType(value: String?): FrequencyType? {
        return value?.let { FrequencyType.valueOf(it) }
    }

    @TypeConverter
    fun frequencyTypeToString(frequencyType: FrequencyType?): String? {
        return frequencyType?.name
    }
} 