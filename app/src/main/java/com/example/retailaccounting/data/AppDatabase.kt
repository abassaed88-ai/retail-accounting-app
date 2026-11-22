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
        Purchase::class, PurchaseItem::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, createdAt INTEGER NOT NULL, currency TEXT NOT NULL, subtotal REAL NOT NULL, total REAL NOT NULL, supplierName TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS purchase_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseId INTEGER NOT NULL, productId INTEGER NOT NULL, productNameSnapshot TEXT NOT NULL, quantity REAL NOT NULL, unitCost REAL NOT NULL, salePriceSet REAL NOT NULL, lineTotal REAL NOT NULL)")
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
                    // v0.2 is an early prototype. Destructive migration is acceptable only
                    // before real business data is entered; add explicit migrations before release.
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
