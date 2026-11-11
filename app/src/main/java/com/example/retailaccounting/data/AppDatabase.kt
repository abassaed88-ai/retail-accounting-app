package com.example.retailaccounting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class, ExchangeRate::class, Sale::class, SaleItem::class,
        Customer::class, Supplier::class, Expense::class, CashMovement::class,
        Purchase::class, PurchaseItem::class, StockMovement::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, createdAt INTEGER NOT NULL, currency TEXT NOT NULL, exchangeRateAtPurchase REAL NOT NULL, total REAL NOT NULL, supplierName TEXT, invoiceNumber TEXT NOT NULL, note TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS purchase_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseId INTEGER NOT NULL, productId INTEGER NOT NULL, productNameSnapshot TEXT NOT NULL, quantityInBaseUnit REAL NOT NULL, unitCostSnapshot REAL NOT NULL, lineTotal REAL NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productId INTEGER NOT NULL, productNameSnapshot TEXT NOT NULL, movementType TEXT NOT NULL, quantityDelta REAL NOT NULL, unitCostSnapshot REAL NOT NULL, currency TEXT NOT NULL, referenceId INTEGER, createdAt INTEGER NOT NULL, note TEXT NOT NULL)")
            }
        }
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "retail_accounting.db"
                )
                    // No destructive fallback: never erase a shop's ledger silently.
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
