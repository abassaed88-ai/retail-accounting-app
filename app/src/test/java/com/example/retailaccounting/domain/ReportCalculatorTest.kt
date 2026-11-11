package com.example.retailaccounting.domain

import com.example.retailaccounting.data.Expense
import com.example.retailaccounting.data.Sale
import com.example.retailaccounting.data.SaleItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCalculatorTest {
    @Test fun calculatesGrossAndNetProfitUsingHistoricalSaleCosts() {
        val sales = listOf(
            Sale(id = 1, createdAt = 100, currency = "USD", exchangeRateAtSale = 1.0,
                subtotal = 30.0, total = 28.0),
            Sale(id = 2, createdAt = 200, currency = "USD", exchangeRateAtSale = 1.0,
                subtotal = 10.0, total = 10.0),
            Sale(id = 3, createdAt = 300, currency = "SYP", exchangeRateAtSale = 15000.0,
                subtotal = 500.0, total = 500.0)
        )
        val items = listOf(
            SaleItem(saleId = 1, productId = 1, productNameSnapshot = "A",
                quantityInSelectedUnit = 3.0, selectedUnit = "قطعة",
                baseUnitsPerSelectedUnit = 1.0, quantityInBaseUnit = 3.0,
                unitPrice = 10.0, unitCostSnapshot = 4.0, lineTotal = 30.0),
            SaleItem(saleId = 2, productId = 1, productNameSnapshot = "A",
                quantityInSelectedUnit = 1.0, selectedUnit = "قطعة",
                baseUnitsPerSelectedUnit = 1.0, quantityInBaseUnit = 1.0,
                unitPrice = 10.0, unitCostSnapshot = 6.0, lineTotal = 10.0)
        )
        val expenses = listOf(
            Expense(id = 1, title = "Rent", amount = 5.0, currency = "USD", createdAt = 150),
            Expense(id = 2, title = "Other period", amount = 99.0, currency = "USD", createdAt = 500)
        )
        val result = ReportCalculator.calculate(sales, items, expenses, 0, 400, "USD")
        assertEquals(38.0, result.sales, 0.000001)
        assertEquals(18.0, result.costOfGoods, 0.000001)
        assertEquals(20.0, result.grossProfit, 0.000001)
        assertEquals(5.0, result.expenses, 0.000001)
        assertEquals(15.0, result.netProfit, 0.000001)
        assertEquals(2, result.salesCount)
    }
}
