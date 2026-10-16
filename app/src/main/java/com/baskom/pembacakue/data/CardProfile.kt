package com.baskom.pembacakue.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_profile")
data class CardProfile(
    @PrimaryKey val cardUid: String,
    val nickname: String,
    val lastBalance: Double,
    val lastSeen: Long
)
