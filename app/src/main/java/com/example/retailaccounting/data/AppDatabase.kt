package com.example.retailaccounting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Product::class, ExchangeRate::class, Sale::class, SaleItem::class,
        Customer::class, Supplier::class, Expense::class, CashMovement::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "retail_accounting.db"
                )
                    // No destructive fallback: never erase a shop's ledger silently.
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
