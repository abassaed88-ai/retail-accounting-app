package com.example.retailaccounting.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val code: String = "",
    val category: String = "",
    val baseUnit: String = "قطعة",
    val quantity: Double = 0.0,
    val costPrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val currency: String = "USD",
    val lowStockLimit: Double = 0.0,
    val isActive: Boolean = true
)

@Entity(tableName = "exchange_rates")
data class ExchangeRate(
    @PrimaryKey val id: Int = 1,
    val usdToSyp: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val currency: String,
    val exchangeRateAtSale: Double,
    val subtotal: Double,
    val discount: Double = 0.0,
    val total: Double,
    val paymentType: String = "CASH",
    val customerName: String? = null
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantityInSelectedUnit: Double,
    val selectedUnit: String,
    val baseUnitsPerSelectedUnit: Double,
    val quantityInBaseUnit: Double,
    val unitPrice: Double,
    val unitCostSnapshot: Double,
    val lineTotal: Double
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val balance: Double = 0.0,
    val currency: String = "USD"
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val balance: Double = 0.0,
    val currency: String = "USD"
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val currency: String,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "cash_movements")
data class CashMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: String,
    val amount: Double,
    val currency: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val currency: String,
    val exchangeRateAtPurchase: Double,
    val total: Double,
    val supplierName: String? = null,
    val invoiceNumber: String = "",
    val note: String = ""
)

@Entity(tableName = "purchase_items")
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantityInBaseUnit: Double,
    val unitCostSnapshot: Double,
    val lineTotal: Double
)

@Entity(tableName = "stock_movements")
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productNameSnapshot: String,
    val movementType: String,
    val quantityDelta: Double,
    val unitCostSnapshot: Double,
    val currency: String,
    val referenceId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "account_movements")
data class AccountMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyType: String,
    val partyName: String,
    val movementType: String,
    val amount: Double,
    val currency: String,
    val referenceId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)
