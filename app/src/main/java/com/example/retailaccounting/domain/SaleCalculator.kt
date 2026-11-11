package com.example.retailaccounting.domain

data class SaleLineInput(
    val quantity: Double,
    val baseUnitsPerSelectedUnit: Double,
    val unitSalePrice: Double,
    val unitCostSnapshot: Double
)

data class SaleLineResult(
    val quantityInBaseUnit: Double,
    val lineTotal: Double,
    val costTotal: Double,
    val profit: Double
)

object SaleCalculator {
    fun calculateLine(input: SaleLineInput): SaleLineResult {
        require(input.quantity.isFinite() && input.quantity > 0.0) { "الكمية يجب أن تكون أكبر من صفر" }
        require(input.baseUnitsPerSelectedUnit.isFinite() && input.baseUnitsPerSelectedUnit > 0.0) { "معامل الوحدة يجب أن يكون أكبر من صفر" }
        require(input.unitSalePrice.isFinite() && input.unitSalePrice >= 0.0) { "سعر البيع لا يمكن أن يكون سالباً" }
        require(input.unitCostSnapshot.isFinite() && input.unitCostSnapshot >= 0.0) { "الكلفة لا يمكن أن تكون سالبة" }
        val baseQuantity = input.quantity * input.baseUnitsPerSelectedUnit
        val total = input.quantity * input.unitSalePrice
        val cost = baseQuantity * input.unitCostSnapshot
        require(baseQuantity.isFinite() && total.isFinite() && cost.isFinite()) { "القيم المدخلة أكبر من المجال المسموح" }
        return SaleLineResult(
            quantityInBaseUnit = baseQuantity,
            lineTotal = total,
            costTotal = cost,
            profit = total - cost
        )
    }

    fun saleTotal(lineTotals: List<Double>, discount: Double): Double {
        require(discount.isFinite() && discount >= 0.0) { "الحسم لا يمكن أن يكون سالباً" }
        require(lineTotals.all { it.isFinite() && it >= 0.0 }) { "قيمة السطر لا يمكن أن تكون سالبة" }
        val subtotal = lineTotals.sum()
        require(subtotal.isFinite()) { "مجموع الفاتورة غير صالح" }
        require(discount <= subtotal) { "الحسم أكبر من مجموع الفاتورة" }
        return subtotal - discount
    }
}
