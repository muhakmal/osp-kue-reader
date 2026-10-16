package com.baskom.pembacakue

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baskom.pembacakue.data.HistoryDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrasi diuji terhadap skema versi lama yang benar-benar pernah dirilis, bukan
 * diasumsikan benar. Riwayat scan pengguna hilang kalau salah satu migrasi gagal.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HistoryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrasi1Ke3MempertahankanRiwayat() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO scan_history (cardUid, cardName, balance, timestamp, transactionCount) " +
                    "VALUES ('AABB', 'Mandiri e-money', 125000.0, 1700000000000, 3)"
            )
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, *HistoryDatabase.ALL_MIGRATIONS)

        migrated.query("SELECT cardUid, balance FROM scan_history").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("AABB", c.getString(0))
            assertEquals(125_000.0, c.getDouble(1), 0.0)
        }
        // Tabel baru ikut terbentuk dan bisa menerima tulisan.
        migrated.execSQL(
            "INSERT INTO card_transaction (cardUid, cardName, timestamp, amount, type, location, terminalId) " +
                "VALUES ('AABB', 'Mandiri e-money', 1700000000000, 12500.0, 'Payment', NULL, NULL)"
        )
        migrated.close()
    }

    @Test
    fun migrasi2Ke3MenolakTransaksiGandaYangSama() {
        helper.createDatabase(dbName, 2).close()
        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, *HistoryDatabase.ALL_MIGRATIONS)

        val row = "INSERT OR IGNORE INTO card_transaction (cardUid, cardName, timestamp, amount, type, location, terminalId) " +
            "VALUES ('AABB', 'Mandiri e-money', 1700000000000, 12500.0, 'Payment', NULL, NULL)"
        migrated.execSQL(row)
        // Transaksi yang sama terbaca lagi pada scan berikutnya; indeks unik menjaga
        // ringkasan pemakaian tidak menghitungnya dua kali.
        migrated.execSQL(row)

        migrated.query("SELECT COUNT(*) FROM card_transaction").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        migrated.close()
    }
}
