package com.baskom.pembacakue.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScanHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ScanHistoryEntity)

    // Beberapa scan terbaru untuk cardUid yang sama. Diambil 3 (bukan 2) karena
    // baris teratas biasanya adalah scan yang baru saja disimpan, sehingga
    // pembanding "scan sebelumnya" ada di baris berikutnya.
    @Query("SELECT * FROM scan_history WHERE cardUid = :cardUid ORDER BY timestamp DESC LIMIT 3")
    suspend fun getRecentByCardUid(cardUid: String): List<ScanHistoryEntity>

    /** Scan terakhir tiap kartu — "saldo terakhir yang diketahui". */
    @Query(
        "SELECT h.* FROM scan_history h " +
        "JOIN (SELECT cardUid, MAX(timestamp) AS mt FROM scan_history GROUP BY cardUid) m " +
        "ON h.cardUid = m.cardUid AND h.timestamp = m.mt " +
        "ORDER BY h.timestamp DESC"
    )
    suspend fun latestPerCard(): List<ScanHistoryEntity>

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory()
}
