package com.example.retailaccounting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StoreDao {
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    abstract fun observeProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    abstract suspend fun getProduct(id: Long): Product?

    @Insert
    abstract suspend fun insertProduct(product: Product): Long

    @Query("UPDATE products SET quantity = quantity - :quantity WHERE id = :productId AND quantity >= :quantity")
    abstract suspend fun deductStock(productId: Long, quantity: Double): Int

    @Query("UPDATE products SET quantity = quantity + :quantity WHERE id = :productId")
    abstract suspend fun addStock(productId: Long, quantity: Double): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveExchangeRate(rate: ExchangeRate)

    @Query("SELECT * FROM exchange_rates WHERE id = 1")
    abstract suspend fun getExchangeRate(): ExchangeRate?

    @Insert
    abstract suspend fun insertSale(sale: Sale): Long

    @Insert
    abstract suspend fun insertSaleItems(items: List<SaleItem>)


    @Query("UPDATE products SET quantity = quantity + :quantity, costPrice = :unitCost, salePrice = :salePrice WHERE id = :productId")
    abstract suspend fun receiveStock(productId: Long, quantity: Double, unitCost: Double, salePrice: Double): Int

    @Insert
    abstract suspend fun insertPurchase(purchase: Purchase): Long

    @Insert
    abstract suspend fun insertPurchaseItems(items: List<PurchaseItem>)

    @Transaction
    open suspend fun savePurchaseAndAddStock(purchase: Purchase, items: List<PurchaseItem>): Long {
        require(items.isNotEmpty()) { "لا يمكن حفظ فاتورة شراء فارغة" }
        items.forEach { item ->
            require(item.quantity > 0.0) { "الكمية يجب أن تكون أكبر من صفر" }
            require(item.unitCost >= 0.0 && item.salePriceSet >= 0.0) { "تحقق من الأسعار المدخلة" }
            val changed = receiveStock(item.productId, item.quantity, item.unitCost, item.salePriceSet)
            check(changed == 1) { "المادة المحددة غير موجودة: ${item.productNameSnapshot}" }
        }
        val purchaseId = insertPurchase(purchase)
        insertPurchaseItems(items.map { it.copy(purchaseId = purchaseId) })
        insertCashMovement(CashMovement(
            direction = "OUT", amount = purchase.total, currency = purchase.currency,
            reason = "مشتريات فاتورة #$purchaseId", createdAt = purchase.createdAt
        ))
        return purchaseId
    }

    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    abstract fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    abstract fun observePurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    abstract suspend fun getSaleItems(saleId: Long): List<SaleItem>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY id")
    abstract suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItem>

    @Query("SELECT sales.currency AS currency, COALESCE(SUM(sale_items.lineTotal), 0) AS revenue, COALESCE(SUM(sale_items.unitCostSnapshot * sale_items.quantityInBaseUnit), 0) AS cost FROM sale_items INNER JOIN sales ON sales.id = sale_items.saleId GROUP BY sales.currency")
    abstract suspend fun getProfitSummary(): List<ProfitSummary>

    @Insert
    abstract suspend fun insertCustomer(customer: Customer): Long

    @Query("SELECT * FROM customers ORDER BY name")
    abstract fun observeCustomers(): Flow<List<Customer>>

    @Insert
    abstract suspend fun insertSupplier(supplier: Supplier): Long

    @Query("SELECT * FROM suppliers ORDER BY name")
    abstract fun observeSuppliers(): Flow<List<Supplier>>

    @Insert
    abstract suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    abstract fun observeExpenses(): Flow<List<Expense>>

    @Insert
    abstract suspend fun insertCashMovement(movement: CashMovement): Long

    @Query("SELECT * FROM cash_movements ORDER BY createdAt DESC")
    abstract fun observeCashMovements(): Flow<List<CashMovement>>

    // The invoice and stock deductions succeed together, or are rolled back together.
    @Transaction
    open suspend fun saveSaleAndDeductStock(sale: Sale, items: List<SaleItem>): Long {
        require(items.isNotEmpty()) { "لا يمكن حفظ فاتورة فارغة" }
        items.forEach { item ->
            require(item.quantityInBaseUnit > 0.0) { "الكمية يجب أن تكون أكبر من صفر" }
            val changed = deductStock(item.productId, item.quantityInBaseUnit)
            check(changed == 1) { "المخزون غير كافٍ للصنف: ${item.productNameSnapshot}" }
        }
        val saleId = insertSale(sale)
        insertSaleItems(items.map { it.copy(saleId = saleId) })
        if (sale.paymentType == "CASH") {
            insertCashMovement(CashMovement(
                direction = "IN", amount = sale.total, currency = sale.currency,
                reason = "مبيعات فاتورة #$saleId", createdAt = sale.createdAt
            ))
        }
        return saleId
    }
}
