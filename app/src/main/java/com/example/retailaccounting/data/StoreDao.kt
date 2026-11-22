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
        items.forEach { item ->
            insertInventoryMovement(InventoryMovement(
                productId = item.productId, productName = item.productNameSnapshot,
                direction = "IN", quantity = item.quantity,
                note = "فاتورة شراء #$purchaseId", createdAt = purchase.createdAt
            ))
        }
        if (purchase.paymentType == "CASH") {
            insertCashMovement(CashMovement(
                direction = "OUT", amount = purchase.total, currency = purchase.currency,
                reason = "مشتريات فاتورة #$purchaseId", createdAt = purchase.createdAt
            ))
        } else {
            val supplierId = requireNotNull(purchase.supplierId) { "اختر المورد للشراء الآجل" }
            val changed = adjustSupplierBalance(supplierId, purchase.total)
            check(changed == 1) { "حساب المورد غير موجود" }
            insertPartyTransaction(PartyTransaction(
                partyType = "SUPPLIER", partyId = supplierId, action = "DEBT",
                amount = purchase.total, currency = purchase.currency,
                note = "فاتورة شراء آجلة #$purchaseId", createdAt = purchase.createdAt
            ))
        }
        return purchaseId
    }

    @Insert
    abstract suspend fun insertInventoryMovement(movement: InventoryMovement): Long

    @Query("SELECT * FROM inventory_movements ORDER BY createdAt DESC LIMIT 300")
    abstract suspend fun getInventoryMovements(): List<InventoryMovement>

    @Query("UPDATE products SET quantity = quantity + :delta WHERE id = :productId AND quantity + :delta >= 0")
    abstract suspend fun adjustProductQuantity(productId: Long, delta: Double): Int

    @Transaction
    open suspend fun saveInventoryAdjustment(product: Product, delta: Double, note: String): Long {
        require(delta != 0.0) { "أدخل كمية غير صفرية" }
        val changed = adjustProductQuantity(product.id, delta)
        check(changed == 1) { "الكمية الناتجة غير صالحة أو المادة غير موجودة" }
        return insertInventoryMovement(InventoryMovement(
            productId = product.id, productName = product.name,
            direction = if (delta > 0) "IN" else "OUT",
            quantity = kotlin.math.abs(delta), note = note
        ))
    }

    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    abstract fun observeSales(): Flow<List<Sale>>

    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    abstract fun observePurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    abstract suspend fun getSaleItems(saleId: Long): List<SaleItem>
    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    abstract suspend fun getSale(saleId: Long): Sale?
    @Query("SELECT COALESCE(SUM(quantityInBaseUnit), 0) FROM sales_return_items WHERE saleItemId = :saleItemId")
    abstract suspend fun getReturnedQuantity(saleItemId: Long): Double
    @Insert abstract suspend fun insertSalesReturn(value: SalesReturn): Long
    @Insert abstract suspend fun insertSalesReturnItems(values: List<SalesReturnItem>)
    @Transaction
    open suspend fun saveSalesReturn(saleId: Long, selected: List<Pair<SaleItem, Double>>, reason: String): Long {
        require(selected.isNotEmpty()) { "اختر مادة واحدة على الأقل للمرتجع" }
        val sale = requireNotNull(getSale(saleId)) { "فاتورة المبيع غير موجودة" }
        val prepared = selected.map { (item, qty) ->
            require(item.saleId == saleId && qty > 0.0) { "كمية المرتجع غير صحيحة" }
            val returned = getReturnedQuantity(item.id)
            require(returned + qty <= item.quantityInBaseUnit + 0.0000001) { "كمية المرتجع تتجاوز الكمية المتبقية من ${item.productNameSnapshot}" }
            requireNotNull(getProduct(item.productId)) { "المادة غير موجودة بالمخزون" }
            item to qty
        }
        val total = prepared.sumOf { (item, qty) -> item.unitPrice * qty }
        require(total > 0.0) { "قيمة المرتجع غير صحيحة" }
        val returnId = insertSalesReturn(SalesReturn(saleId = saleId, currency = sale.currency, total = total, reason = reason))
        insertSalesReturnItems(prepared.map { (item, qty) -> SalesReturnItem(returnId = returnId, saleItemId = item.id, productId = item.productId, productNameSnapshot = item.productNameSnapshot, quantityInBaseUnit = qty, unitPrice = item.unitPrice, lineTotal = item.unitPrice * qty) })
        prepared.forEach { (item, qty) ->
            check(addStock(item.productId, qty) == 1) { "تعذر إعادة المادة إلى المخزون" }
            insertInventoryMovement(InventoryMovement(productId = item.productId, productName = item.productNameSnapshot, direction = "IN", quantity = qty, note = "مرتجع مبيع #$returnId — فاتورة #$saleId"))
        }
        if (sale.paymentType == "CASH") {
            insertCashMovement(CashMovement(direction = "OUT", amount = total, currency = sale.currency, reason = "رد قيمة مرتجع مبيع #$returnId"))
        } else {
            val customerId = requireNotNull(sale.customerId) { "الفاتورة الآجلة لا تحتوي حساب عميل" }
            check(adjustCustomerBalance(customerId, -total) == 1) { "حساب العميل غير موجود" }
            insertPartyTransaction(PartyTransaction(partyType = "CUSTOMER", partyId = customerId, action = "RETURN", amount = total, currency = sale.currency, note = "مرتجع مبيع #$returnId — فاتورة #$saleId"))
        }
        return returnId
    }

    @Query("SELECT * FROM purchases WHERE id = :purchaseId LIMIT 1")
    abstract suspend fun getPurchase(purchaseId: Long): Purchase?
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM purchase_return_items WHERE purchaseItemId = :purchaseItemId")
    abstract suspend fun getReturnedPurchaseQuantity(purchaseItemId: Long): Double
    @Insert abstract suspend fun insertPurchaseReturn(value: PurchaseReturn): Long
    @Insert abstract suspend fun insertPurchaseReturnItems(values: List<PurchaseReturnItem>)
    @Transaction
    open suspend fun savePurchaseReturn(purchaseId: Long, selected: List<Pair<PurchaseItem, Double>>, reason: String): Long {
        require(selected.isNotEmpty()) { "اختر مادة واحدة على الأقل للمرتجع" }
        val purchase = requireNotNull(getPurchase(purchaseId)) { "فاتورة الشراء غير موجودة" }
        val prepared = selected.map { (item, qty) ->
            require(item.purchaseId == purchaseId && qty > 0.0) { "كمية المرتجع غير صحيحة" }
            require(getReturnedPurchaseQuantity(item.id) + qty <= item.quantity + 0.0000001) { "كمية المرتجع تتجاوز الكمية المتبقية من ${item.productNameSnapshot}" }
            requireNotNull(getProduct(item.productId)) { "المادة غير موجودة بالمخزون" }
            item to qty
        }
        val total = prepared.sumOf { (item, qty) -> item.unitCost * qty }
        require(total > 0.0) { "قيمة المرتجع غير صحيحة" }
        val returnId = insertPurchaseReturn(PurchaseReturn(purchaseId = purchaseId, currency = purchase.currency, total = total, reason = reason))
        insertPurchaseReturnItems(prepared.map { (item, qty) -> PurchaseReturnItem(returnId = returnId, purchaseItemId = item.id, productId = item.productId, productNameSnapshot = item.productNameSnapshot, quantity = qty, unitCost = item.unitCost, lineTotal = item.unitCost * qty) })
        prepared.forEach { (item, qty) ->
            check(adjustProductQuantity(item.productId, -qty) == 1) { "المخزون الحالي لا يكفي لإرجاع ${item.productNameSnapshot}" }
            insertInventoryMovement(InventoryMovement(productId = item.productId, productName = item.productNameSnapshot, direction = "OUT", quantity = qty, note = "مرتجع شراء #$returnId — فاتورة #$purchaseId"))
        }
        if (purchase.paymentType == "CASH") {
            insertCashMovement(CashMovement(direction = "IN", amount = total, currency = purchase.currency, reason = "استرداد قيمة مرتجع شراء #$returnId"))
        } else {
            val supplierId = requireNotNull(purchase.supplierId) { "الفاتورة الآجلة لا تحتوي حساب مورد" }
            check(adjustSupplierBalance(supplierId, -total) == 1) { "حساب المورد غير موجود" }
            insertPartyTransaction(PartyTransaction(partyType = "SUPPLIER", partyId = supplierId, action = "RETURN", amount = total, currency = purchase.currency, note = "مرتجع شراء #$returnId — فاتورة #$purchaseId"))
        }
        return returnId
    }

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY id")
    abstract suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItem>

    @Query("SELECT currencies.currency AS currency, COALESCE((SELECT SUM(sale_items.lineTotal) FROM sale_items INNER JOIN sales ON sales.id = sale_items.saleId WHERE sales.currency = currencies.currency), 0) AS revenue, COALESCE((SELECT SUM(sale_items.unitCostSnapshot * sale_items.quantityInBaseUnit) FROM sale_items INNER JOIN sales ON sales.id = sale_items.saleId WHERE sales.currency = currencies.currency), 0) AS cost, COALESCE((SELECT SUM(expenses.amount) FROM expenses WHERE expenses.currency = currencies.currency), 0) AS expenses FROM (SELECT currency FROM sales UNION SELECT currency FROM expenses) AS currencies")
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
    abstract suspend fun insertPartyTransaction(transaction: PartyTransaction): Long

    @Query("UPDATE customers SET balance = balance + :delta WHERE id = :partyId")
    abstract suspend fun adjustCustomerBalance(partyId: Long, delta: Double): Int

    @Query("UPDATE suppliers SET balance = balance + :delta WHERE id = :partyId")
    abstract suspend fun adjustSupplierBalance(partyId: Long, delta: Double): Int

    @Query("SELECT * FROM party_transactions WHERE partyType = :partyType AND partyId = :partyId ORDER BY createdAt DESC")
    abstract suspend fun getPartyTransactions(partyType: String, partyId: Long): List<PartyTransaction>

    @Transaction
    open suspend fun savePartyTransaction(transaction: PartyTransaction): Long {
        require(transaction.amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
        val delta = if (transaction.action == "DEBT") transaction.amount else -transaction.amount
        val changed = if (transaction.partyType == "CUSTOMER") {
            adjustCustomerBalance(transaction.partyId, delta)
        } else {
            adjustSupplierBalance(transaction.partyId, delta)
        }
        check(changed == 1) { "الحساب المحدد غير موجود" }
        val id = insertPartyTransaction(transaction)
        if (transaction.action == "PAYMENT") {
            insertCashMovement(CashMovement(
                direction = if (transaction.partyType == "CUSTOMER") "IN" else "OUT",
                amount = transaction.amount,
                currency = transaction.currency,
                reason = if (transaction.partyType == "CUSTOMER") "تسديد عميل: ${transaction.note}" else "تسديد مورد: ${transaction.note}",
                createdAt = transaction.createdAt
            ))
        }
        return id
    }

    @Insert
    abstract suspend fun insertExpense(expense: Expense): Long
    @Transaction
    open suspend fun saveExpenseAndCashMovement(expense: Expense): Long {
        require(expense.title.isNotBlank()) { "أدخل بيان المصروف" }
        require(expense.amount > 0.0) { "قيمة المصروف يجب أن تكون أكبر من صفر" }
        val id = insertExpense(expense)
        insertCashMovement(CashMovement(
            direction = "OUT",
            amount = expense.amount,
            currency = expense.currency,
            reason = "مصروف: ${expense.title}",
            createdAt = expense.createdAt
        ))
        return id
    }

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
        items.forEach { item ->
            insertInventoryMovement(InventoryMovement(
                productId = item.productId, productName = item.productNameSnapshot,
                direction = "OUT", quantity = item.quantityInBaseUnit,
                note = "فاتورة مبيع #$saleId", createdAt = sale.createdAt
            ))
        }
        if (sale.paymentType == "CASH") {
            insertCashMovement(CashMovement(
                direction = "IN", amount = sale.total, currency = sale.currency,
                reason = "مبيعات فاتورة #$saleId", createdAt = sale.createdAt
            ))
        } else {
            val customerId = requireNotNull(sale.customerId) { "اختر العميل للبيع الآجل" }
            val changed = adjustCustomerBalance(customerId, sale.total)
            check(changed == 1) { "حساب العميل غير موجود" }
            insertPartyTransaction(PartyTransaction(
                partyType = "CUSTOMER", partyId = customerId, action = "DEBT",
                amount = sale.total, currency = sale.currency,
                note = "فاتورة مبيع آجلة #$saleId", createdAt = sale.createdAt
            ))
        }
        return saleId
    }
}
