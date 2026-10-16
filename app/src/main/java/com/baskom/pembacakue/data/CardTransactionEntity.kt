package com.baskom.pembacakue.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Satu transaksi yang terbaca dari log kartu.
 *
 * Sebelumnya hanya saldo yang disimpan, jadi ringkasan pemakaian bulanan terpaksa
 * ditebak dari selisih saldo antar scan. Transaksi yang sama akan terbaca lagi di
 * scan berikutnya, karena itu indeks unik mencegah duplikat.
 */
@Entity(
    tableName = "card_transaction",
    indices = [Index(value = ["cardUid", "timestamp", "amount", "type"], unique = true)]
)
data class CardTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardUid: String,
    val cardName: String,
    /** Waktu transaksi di kartu (epoch millis), bukan waktu scan. */
    val timestamp: Long,
    val amount: Double,
    val type: String,
    val location: String?,
    val terminalId: String?
) {
    val isTopUp: Boolean get() = type.contains("Top up", ignoreCase = true)
}

@Dao
interface CardTransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<CardTransactionEntity>)

    @Query("SELECT * FROM card_transaction ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CardTransactionEntity>>

    @Query("DELETE FROM card_transaction")
    suspend fun clear()
}
