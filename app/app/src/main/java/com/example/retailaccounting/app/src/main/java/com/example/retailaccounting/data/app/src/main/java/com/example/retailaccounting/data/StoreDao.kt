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

@Query("SELECT * FROM sales ORDER BY createdAt DESC")
abstract fun observeSales(): Flow<List<Sale>>

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
    return saleId
}

}
