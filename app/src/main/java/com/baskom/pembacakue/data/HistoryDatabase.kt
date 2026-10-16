package com.baskom.pembacakue.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScanHistoryEntity::class, CardProfile::class, CardTransactionEntity::class],
    version = 3,
    exportSchema = true
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun cardProfileDao(): CardProfileDao
    abstract fun cardTransactionDao(): CardTransactionDao

    companion object {
        const val NAME = "pembacakue_history_database"

        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_profile` (`cardUid` TEXT NOT NULL, `nickname` TEXT NOT NULL, `lastBalance` REAL NOT NULL, `lastSeen` INTEGER NOT NULL, PRIMARY KEY(`cardUid`))"
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_transaction` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardUid` TEXT NOT NULL, `cardName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `amount` REAL NOT NULL, `type` TEXT NOT NULL, `location` TEXT, `terminalId` TEXT)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_card_transaction_cardUid_timestamp_amount_type` ON `card_transaction` (`cardUid`, `timestamp`, `amount`, `type`)"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun getDatabase(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    NAME
                )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
