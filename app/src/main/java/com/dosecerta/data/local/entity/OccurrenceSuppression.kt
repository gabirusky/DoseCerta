package com.dosecerta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A non-clinical tombstone: reconciliation must not recreate a deleted occurrence. */
@Entity(tableName = "occurrence_suppressions")
data class OccurrenceSuppression(
    @PrimaryKey val occurrenceId: String,
    val deletedAt: Long
)
