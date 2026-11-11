package com.example.retailaccounting.domain

import com.example.retailaccounting.data.AccountMovement

data class PartyBalance(
    val partyType: String,
    val partyName: String,
    val currency: String,
    val balance: Double
)

object AccountBalanceCalculator {
    fun calculate(movements: List<AccountMovement>): List<PartyBalance> {
        require(movements.all {
            (it.partyType == "CUSTOMER" || it.partyType == "SUPPLIER") &&
                it.partyName.isNotBlank() &&
                (it.currency == "USD" || it.currency == "SYP") &&
                it.amount.isFinite()
        }) { "توجد حركة حساب غير صالحة" }

        return movements
            .groupBy { Triple(it.partyType, it.partyName.trim(), it.currency) }
            .map { (key, entries) ->
                val balance = entries.sumOf { it.amount }
                require(balance.isFinite()) { "رصيد الحساب خارج المجال المسموح" }
                PartyBalance(key.first, key.second, key.third, balance)
            }
            .sortedWith(compareBy<PartyBalance> { it.partyType }.thenBy { it.partyName }.thenBy { it.currency })
    }
}
