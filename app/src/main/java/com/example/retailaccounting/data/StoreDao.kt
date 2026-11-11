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

    // Negative stock is intentionally permitted for a registered, active item.
    @Query("UPDATE products SET quantity = quantity - :quantity WHERE id = :productId AND isActive = 1")
    abstract suspend fun deductStock(productId: Long, quantity: Double): Int

    @Query("UPDATE products SET quantity = quantity + :quantity, costPrice = :unitCost, currency = :currency WHERE id = :productId AND isActive = 1")
    abstract suspend fun addPurchasedStock(productId: Long, quantity: Double, unitCost: Double, currency: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveExchangeRate(rate: ExchangeRate)

    @Query("SELECT * FROM exchange_rates WHERE id = 1")
    abstract suspend fun getExchangeRate(): ExchangeRate?

    @Insert
    abstract suspend fun insertSale(sale: Sale): Long

    @Insert
    abstract suspend fun insertSaleItems(items: List<SaleItem>)

    @Insert
    abstract suspend fun insertPurchase(purchase: Purchase): Long

    @Insert
    abstract suspend fun insertPurchaseItems(items: List<PurchaseItem>)

    @Insert
    abstract suspend fun insertStockMovement(movement: StockMovement): Long

    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    abstract fun observePurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC")
    abstract fun observeStockMovements(productId: Long): Flow<List<StockMovement>>
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    abstract fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sale_items ORDER BY id")
    abstract fun observeSaleItems(): Flow<List<SaleItem>>

    @Insert
    abstract suspend fun insertCustomer(customer: Customer): Long

    @Query("SELECT * FROM customers ORDER BY name")
    abstract fun observeCustomers(): Flow<List<Customer>>

    @Insert
    abstract suspend fun insertSupplier(supplier: Supplier): Long

    @Query("SELECT * FROM suppliers ORDER BY name")
    abstract fun observeSuppliers(): Flow<List<Supplier>>

    @Insert
    abstract suspend fun insertAccountMovement(movement: AccountMovement): Long

    @Query("SELECT * FROM account_movements ORDER BY createdAt DESC")
    abstract fun observeAccountMovements(): Flow<List<AccountMovement>>

    @Query("SELECT * FROM account_movements WHERE partyType = :partyType AND partyName = :partyName ORDER BY createdAt DESC")
    abstract fun observePartyMovements(partyType: String, partyName: String): Flow<List<AccountMovement>>

    // Positive amounts represent amounts owed to the shop by customers,
    // or amounts owed by the shop to suppliers. Payments are negative.
    @Transaction
    open suspend fun recordAccountMovement(movement: AccountMovement): Long {
        require(movement.partyType == "CUSTOMER" || movement.partyType == "SUPPLIER") { "نوع الحساب غير صحيح" }
        require(movement.partyName.isNotBlank()) { "اسم الطرف مطلوب" }
        require(movement.movementType in setOf("SALE_CREDIT", "PURCHASE_CREDIT", "PAYMENT", "ADJUSTMENT")) { "نوع الحركة غير صحيح" }
        require(movement.amount.isFinite() && movement.amount != 0.0) { "قيمة الحركة غير صحيحة" }
        require(movement.currency == "USD" || movement.currency == "SYP") { "العملة غير صحيحة" }
        return insertAccountMovement(movement)
    }

    @Insert
    abstract suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    abstract fun observeExpenses(): Flow<List<Expense>>

    @Insert
    abstract suspend fun insertCashMovement(movement: CashMovement): Long

    @Transaction
    open suspend fun recordExpenseAndCash(expense: Expense, movement: CashMovement): Long {
        require(expense.amount > 0.0) { "قيمة المصروف يجب أن تكون أكبر من صفر" }
        val id = insertExpense(expense)
        insertCashMovement(movement)
        return id
    }
    @Transaction
    open suspend fun savePurchaseAndAddStock(purchase: Purchase, items: List<PurchaseItem>): Long {
        require(items.isNotEmpty()) { "لا يمكن حفظ فاتورة شراء فارغة" }
        items.forEach { item ->
            require(item.quantityInBaseUnit > 0.0) { "كمية الشراء يجب أن تكون أكبر من صفر" }
            require(item.unitCostSnapshot >= 0.0) { "كلفة الشراء غير صحيحة" }
            val product = getProduct(item.productId)
            require(product != null && product.isActive) {
                "لا يمكن شراء صنف غير مسجل أو مؤرشف: ${item.productNameSnapshot}"
            }
            val changed = addPurchasedStock(item.productId, item.quantityInBaseUnit, item.unitCostSnapshot, purchase.currency)
            check(changed == 1) { "تعذر تحديث مخزون الصنف: ${item.productNameSnapshot}" }
        }
        val purchaseId = insertPurchase(purchase)
        insertPurchaseItems(items.map { it.copy(purchaseId = purchaseId) })
        items.forEach { item ->
            insertStockMovement(StockMovement(
                productId = item.productId,
                productNameSnapshot = item.productNameSnapshot,
                movementType = "PURCHASE",
                quantityDelta = item.quantityInBaseUnit,
                unitCostSnapshot = item.unitCostSnapshot,
                currency = purchase.currency,
                referenceId = purchaseId
            ))
        }
        return purchaseId
    }
    @Transaction
    open suspend fun saveSaleAndDeductStock(
        sale: Sale,
        items: List<SaleItem>,
        accountMovement: AccountMovement? = null,
        cashMovement: CashMovement? = null
    ): Long {
        require(items.isNotEmpty()) { "لا يمكن حفظ فاتورة فارغة" }
        require(sale.total.isFinite() && sale.total >= 0.0) { "إجمالي الفاتورة غير صالح" }
        if (sale.paymentType == "DEBT") {
            require(accountMovement != null) { "يجب تسجيل مديونية الزبون مع البيع الآجل" }
            require(accountMovement.partyType == "CUSTOMER" && accountMovement.movementType == "SALE_CREDIT") { "حركة حساب الزبون غير صحيحة" }
            require(accountMovement.amount == sale.total && accountMovement.currency == sale.currency) { "قيمة مديونية الزبون لا تطابق الفاتورة" }
            require(sale.customerName?.isNotBlank() == true && accountMovement.partyName == sale.customerName) { "اسم الزبون مطلوب للبيع الآجل" }
        } else {
            require(accountMovement == null) { "لا يجوز تسجيل مديونية لعملية نقدية" }
            require(cashMovement != null && cashMovement.direction == "IN" && cashMovement.amount == sale.total && cashMovement.currency == sale.currency) { "حركة الصندوق النقدية غير مطابقة للفاتورة" }
        }
        items.forEach { item ->
            require(item.quantityInBaseUnit.isFinite() && item.quantityInBaseUnit > 0.0) { "الكمية يجب أن تكون أكبر من صفر" }
            val product = getProduct(item.productId)
            require(product != null && product.isActive) {
                "لا يمكن بيع صنف غير مسجل أو مؤرشف: ${item.productNameSnapshot}"
            }
            val changed = deductStock(item.productId, item.quantityInBaseUnit)
            check(changed == 1) { "تعذر تحديث مخزون الصنف: ${item.productNameSnapshot}" }
        }
        val saleId = insertSale(sale)
        insertSaleItems(items.map { it.copy(saleId = saleId) })
        items.forEach { item ->
            insertStockMovement(StockMovement(
                productId = item.productId,
                productNameSnapshot = item.productNameSnapshot,
                movementType = "SALE",
                quantityDelta = -item.quantityInBaseUnit,
                unitCostSnapshot = item.unitCostSnapshot,
                currency = sale.currency,
                referenceId = saleId
            ))
        }
        accountMovement?.let { insertAccountMovement(it.copy(referenceId = saleId)) }
        cashMovement?.let { insertCashMovement(it.copy(reason = "مبيع نقدي #${saleId}: ${it.reason}")) }
        return saleId
    }
}
