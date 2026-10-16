package com.baskom.pembacakue.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val cardUid: String,
    val cardName: String,
    val balance: Double,
    val timestamp: Long,
    val transactionCount: Int
)
