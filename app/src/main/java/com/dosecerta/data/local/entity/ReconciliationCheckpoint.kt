package com.dosecerta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reconciliation_checkpoint")
data class ReconciliationCheckpoint(@PrimaryKey val id: Int = 1, val reconciledUntil: Long)
