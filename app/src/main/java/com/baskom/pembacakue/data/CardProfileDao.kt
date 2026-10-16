package com.baskom.pembacakue.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardProfileDao {
    @Query("SELECT * FROM card_profile WHERE cardUid = :uid LIMIT 1")
    suspend fun getProfile(uid: String): CardProfile?

    @Query("SELECT * FROM card_profile")
    fun observeAll(): Flow<List<CardProfile>>

    @Query("SELECT * FROM card_profile")
    suspend fun getAll(): List<CardProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: CardProfile)
}
