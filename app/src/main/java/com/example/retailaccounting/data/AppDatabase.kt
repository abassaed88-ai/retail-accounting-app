package com.example.retailaccounting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class, ExchangeRate::class, Sale::class, SaleItem::class, SalesReturn::class, SalesReturnItem::class,
        Customer::class, Supplier::class, Expense::class, CashMovement::class,
        Purchase::class, PurchaseItem::class, PartyTransaction::class, InventoryMovement::class
    ],
    version = 7,
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


        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS party_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, partyType TEXT NOT NULL, partyId INTEGER NOT NULL, action TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS inventory_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productId INTEGER NOT NULL, productName TEXT NOT NULL, direction TEXT NOT NULL, quantity REAL NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sales ADD COLUMN customerId INTEGER")
                database.execSQL("ALTER TABLE purchases ADD COLUMN paymentType TEXT NOT NULL DEFAULT 'CASH'")
                database.execSQL("ALTER TABLE purchases ADD COLUMN supplierId INTEGER")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS sales_returns (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, saleId INTEGER NOT NULL, currency TEXT NOT NULL, total REAL NOT NULL, reason TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS sales_return_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, returnId INTEGER NOT NULL, saleItemId INTEGER NOT NULL, productId INTEGER NOT NULL, productNameSnapshot TEXT NOT NULL, quantityInBaseUnit REAL NOT NULL, unitPrice REAL NOT NULL, lineTotal REAL NOT NULL)")
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
