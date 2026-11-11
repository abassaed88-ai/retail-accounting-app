package com.example.retailaccounting.domain

import com.example.retailaccounting.data.Expense
import com.example.retailaccounting.data.Sale
import com.example.retailaccounting.data.SaleItem

data class FinancialTotals(
    val sales: Double,
    val costOfGoods: Double,
    val grossProfit: Double,
    val expenses: Double,
    val netProfit: Double,
    val salesCount: Int
)

object ReportCalculator {
    fun calculate(
        sales: List<Sale>,
        items: List<SaleItem>,
        expenses: List<Expense>,
        startInclusive: Long,
        endExclusive: Long,
        currency: String
    ): FinancialTotals {
        require(startInclusive < endExclusive) { "نطاق التاريخ غير صحيح" }
        val selectedSales = sales.filter {
            it.createdAt >= startInclusive && it.createdAt < endExclusive && it.currency == currency
        }
        val saleIds = selectedSales.map { it.id }.toSet()
        val selectedItems = items.filter { it.saleId in saleIds }
        require(selectedSales.all { it.total.isFinite() && it.total >= 0.0 }) { "توجد قيمة مبيعات غير صالحة" }
        require(selectedItems.all { it.quantityInBaseUnit.isFinite() && it.quantityInBaseUnit >= 0.0 && it.unitCostSnapshot.isFinite() && it.unitCostSnapshot >= 0.0 }) { "توجد كلفة بضاعة غير صالحة" }
        val revenue = selectedSales.sumOf { it.total }
        val cost = selectedItems.sumOf { it.quantityInBaseUnit * it.unitCostSnapshot }
        val expenseTotal = expenses.filter {
            it.createdAt >= startInclusive && it.createdAt < endExclusive && it.currency == currency
        }.sumOf { it.amount }
        require(expenseTotal.isFinite() && expenseTotal >= 0.0) { "توجد مصروفات غير صالحة" }
        val gross = revenue - cost
        return FinancialTotals(
            sales = revenue,
            costOfGoods = cost,
            grossProfit = gross,
            expenses = expenseTotal,
            netProfit = gross - expenseTotal,
            salesCount = selectedSales.size
        )
    }
}
