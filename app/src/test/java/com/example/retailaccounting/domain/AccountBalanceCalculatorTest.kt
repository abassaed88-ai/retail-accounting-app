package com.example.retailaccounting.domain

import com.example.retailaccounting.data.AccountMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountBalanceCalculatorTest {
    @Test fun sumsChargesAndPartialPaymentsPerPartyAndCurrency() {
        val movements = listOf(
            AccountMovement(partyType = "CUSTOMER", partyName = "ليلى", movementType = "SALE_CREDIT", amount = 100.0, currency = "USD"),
            AccountMovement(partyType = "CUSTOMER", partyName = "ليلى", movementType = "PAYMENT", amount = -30.0, currency = "USD"),
            AccountMovement(partyType = "CUSTOMER", partyName = "ليلى", movementType = "SALE_CREDIT", amount = 500000.0, currency = "SYP"),
            AccountMovement(partyType = "SUPPLIER", partyName = "مورد", movementType = "PURCHASE_CREDIT", amount = 70.0, currency = "USD")
        )
        val result = AccountBalanceCalculator.calculate(movements)
        assertEquals(3, result.size)
        assertEquals(70.0, result.first { it.partyType == "CUSTOMER" && it.currency == "USD" }.balance, 0.000001)
        assertEquals(500000.0, result.first { it.partyType == "CUSTOMER" && it.currency == "SYP" }.balance, 0.000001)
        assertEquals(70.0, result.first { it.partyType == "SUPPLIER" }.balance, 0.000001)
    }

    @Test fun rejectsInvalidMovementValues() {
        val invalid = AccountMovement(partyType = "CUSTOMER", partyName = "عميل", movementType = "PAYMENT", amount = Double.NaN, currency = "USD")
        assertThrows(IllegalArgumentException::class.java) {
            AccountBalanceCalculator.calculate(listOf(invalid))
        }
    }
}
