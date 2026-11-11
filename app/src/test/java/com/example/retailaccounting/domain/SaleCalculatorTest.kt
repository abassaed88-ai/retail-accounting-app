package com.example.retailaccounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaleCalculatorTest {
    @Test fun calculatesLineTotalAndHistoricalProfit() {
        val result = SaleCalculator.calculateLine(
            SaleLineInput(quantity = 3.0, baseUnitsPerSelectedUnit = 2.0,
                unitSalePrice = 5.0, unitCostSnapshot = 2.0)
        )
        assertEquals(6.0, result.quantityInBaseUnit, 0.000001)
        assertEquals(15.0, result.lineTotal, 0.000001)
        assertEquals(12.0, result.costTotal, 0.000001)
        assertEquals(3.0, result.profit, 0.000001)
    }

    @Test fun calculatesInvoiceTotalAfterDiscount() {
        assertEquals(26.0, SaleCalculator.saleTotal(listOf(10.0, 20.0), 4.0), 0.000001)
    }

    @Test fun rejectsZeroQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            SaleCalculator.calculateLine(SaleLineInput(0.0, 1.0, 10.0, 4.0))
        }
    }

    @Test fun rejectsNaNAndInfiniteMoneyInputs() {
        assertThrows(IllegalArgumentException::class.java) { SaleCalculator.calculateLine(SaleLineInput(Double.NaN, 1.0, 10.0, 4.0)) }
        assertThrows(IllegalArgumentException::class.java) { SaleCalculator.calculateLine(SaleLineInput(1.0, 1.0, Double.POSITIVE_INFINITY, 4.0)) }
        assertThrows(IllegalArgumentException::class.java) { SaleCalculator.saleTotal(listOf(10.0), Double.NaN) }
    }

    @Test fun rejectsOverflowedInvoiceTotals() {
        assertThrows(IllegalArgumentException::class.java) { SaleCalculator.saleTotal(listOf(Double.MAX_VALUE, Double.MAX_VALUE), 0.0) }
    }

    @Test fun rejectsDiscountGreaterThanSubtotal() {
        assertThrows(IllegalArgumentException::class.java) {
            SaleCalculator.saleTotal(listOf(10.0), 11.0)
        }
    }
}
