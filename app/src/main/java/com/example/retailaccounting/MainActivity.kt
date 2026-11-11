package com.example.retailaccounting

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.retailaccounting.data.AppDatabase
import com.example.retailaccounting.data.Product
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var dao: com.example.retailaccounting.data.StoreDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dao = AppDatabase.getInstance(this).storeDao()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFFF7F3FA.toInt())
        }
        val title = TextView(this).apply {
            text = "Good Girl"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFC2185B.toInt())
            setPadding(8, 18, 8, 18)
        }
        val subtitle = TextView(this).apply {
            text = "إدارة المبيعات والمخزون"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 18)
        }
        val addButton = Button(this).apply {
            text = "＋ إضافة صنف جديد"
            setOnClickListener { showAddProductDialog() }
        }
        val purchaseButton = Button(this).apply {
            text = "＋ تسجيل فاتورة شراء"
            setOnClickListener { showPurchaseDialog() }
        }
        val saleButton = Button(this).apply { text = "＋ تسجيل فاتورة مبيع"; setOnClickListener { showSaleDialog() } }
        val expenseButton = Button(this).apply { text = "－ تسجيل مصروف"; setOnClickListener { showExpenseDialog() } }
        val section = TextView(this).apply {
            text = "الأصناف والمخزون"
            textSize = 20f
            setTextColor(0xFF351052.toInt())
            setPadding(4, 22, 4, 10)
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }

        page.addView(title)
        page.addView(subtitle)
        page.addView(addButton)
        page.addView(purchaseButton)
        page.addView(saleButton)
        page.addView(expenseButton)
        page.addView(section)
        page.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(page)

        lifecycleScope.launch {
            dao.observeProducts().collectLatest { products -> renderProducts(products) }
        }
    }

    private fun renderProducts(products: List<Product>) {
        list.removeAllViews()
        if (products.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "لا توجد أصناف بعد. اضغط «إضافة صنف جديد» للبدء."
                textSize = 16f
                setPadding(12, 20, 12, 20)
            })
        }
        products.forEach { product ->
            val row = TextView(this).apply {
                text = "${product.name}\nالكمية: ${product.quantity} ${product.baseUnit}   |   " +
                    "سعر البيع: ${product.salePrice} ${product.currency}"
                textSize = 16f
                setTextColor(0xFF25202A.toInt())
                setPadding(18, 16, 18, 16)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
            list.addView(row, params)
        }
    }


    private fun showExpenseDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val title = EditText(this).apply { hint = "بيان المصروف" }
        val amount = EditText(this).apply { hint = "قيمة المصروف"; inputType = 8194 }
        val currency = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP")) }
        box.addView(title); box.addView(amount); box.addView(currency)
        AlertDialog.Builder(this).setTitle("تسجيل مصروف").setView(box).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create().also { dialog ->
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = title.text.toString().trim()
                val value = amount.text.toString().toDoubleOrNull()
                if (label.isBlank()) { title.error = "اكتب بيان المصروف"; return@setOnClickListener }
                if (value == null || value <= 0.0) { amount.error = "أدخل مبلغاً أكبر من صفر"; return@setOnClickListener }
                lifecycleScope.launch {
                    try {
                        val money = currency.selectedItem.toString()
                        dao.recordExpenseAndCash(
                            com.example.retailaccounting.data.Expense(title = label, amount = value, currency = money),
                            com.example.retailaccounting.data.CashMovement(direction = "OUT", amount = value, currency = money, reason = "مصروف: " + label)
                        )
                        Toast.makeText(this@MainActivity, "تم تسجيل المصروف وحركة الصندوق", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } catch (error: Exception) { Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ المصروف", Toast.LENGTH_LONG).show() }
                }
            } }
            dialog.show()
        }
    }
    private fun showSaleDialog() {
        lifecycleScope.launch {
            val products = dao.observeProducts().first()
            if (products.isEmpty()) { Toast.makeText(this@MainActivity, "أضف صنفاً أولاً", Toast.LENGTH_LONG).show(); return@launch }
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
            val spinner = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, products.map { it.name }) }
            fun field(label: String) = EditText(this@MainActivity).apply { hint = label; textSize = 16f }
            val qty = field("الكمية المباعة").apply { inputType = 8194 }
            val price = field("سعر بيع الوحدة").apply { inputType = 8194 }
            val customer = field("اسم الزبون (اختياري)")
            val payment = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدي", "آجل")) }
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { price.setText(products[position].salePrice.toString()) }
            }
            box.addView(spinner); box.addView(qty); box.addView(price); box.addView(customer); box.addView(payment)
            val dialog = AlertDialog.Builder(this@MainActivity).setTitle("تسجيل فاتورة مبيع").setView(box).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val quantity = qty.text.toString().toDoubleOrNull()
                    val salePrice = price.text.toString().toDoubleOrNull()
                    if (quantity == null || quantity <= 0.0) { qty.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                    if (salePrice == null || salePrice < 0.0) { price.error = "أدخل سعراً صحيحاً"; return@setOnClickListener }
                    val product = products[spinner.selectedItemPosition]
                    val line = com.example.retailaccounting.domain.SaleCalculator.calculateLine(com.example.retailaccounting.domain.SaleLineInput(quantity, 1.0, salePrice, product.costPrice))
                    val exchange = if (product.currency == "USD") 1.0 else (dao.getExchangeRate()?.usdToSyp ?: 1.0)
                    val sale = com.example.retailaccounting.data.Sale(currency = product.currency, exchangeRateAtSale = exchange, subtotal = line.lineTotal, total = line.lineTotal, paymentType = if (payment.selectedItemPosition == 0) "CASH" else "DEBT", customerName = customer.text.toString().trim().ifBlank { null })
                    val item = com.example.retailaccounting.data.SaleItem(saleId = 0, productId = product.id, productNameSnapshot = product.name, quantityInSelectedUnit = quantity, selectedUnit = product.baseUnit, baseUnitsPerSelectedUnit = 1.0, quantityInBaseUnit = line.quantityInBaseUnit, unitPrice = salePrice, unitCostSnapshot = product.costPrice, lineTotal = line.lineTotal)
                    lifecycleScope.launch {
                        try { dao.saveSaleAndDeductStock(sale, listOf(item)); Toast.makeText(this@MainActivity, "تم حفظ فاتورة المبيع وتحديث المخزون", Toast.LENGTH_LONG).show(); dialog.dismiss() }
                        catch (error: Exception) { Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ الفاتورة", Toast.LENGTH_LONG).show() }
                    }
                }
            }
            dialog.show()
        }
    }
    private fun showPurchaseDialog() {
        lifecycleScope.launch {
            val products = dao.observeProducts().first()
            if (products.isEmpty()) {
                Toast.makeText(this@MainActivity, "أضف صنفاً أولاً قبل تسجيل الشراء", Toast.LENGTH_LONG).show()
                return@launch
            }
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 8, 24, 0)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            fun field(label: String): EditText = EditText(this@MainActivity).apply {
                hint = label
                textSize = 16f
            }
            val productSpinner = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, products.map { it.name })
            }
            val quantity = field("الكمية المشتراة").apply { inputType = 8194 }
            val unitCost = field("كلفة الوحدة الجديدة").apply { inputType = 8194 }
            val supplier = field("اسم المورد (اختياري)")
            val invoiceNumber = field("رقم الفاتورة (اختياري)")
            val currency = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP"))
            }
            box.addView(productSpinner)
            box.addView(quantity)
            box.addView(unitCost)
            box.addView(supplier)
            box.addView(invoiceNumber)
            box.addView(currency)
            AlertDialog.Builder(this@MainActivity)
                .setTitle("تسجيل فاتورة شراء")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create().also { dialog ->
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val qty = quantity.text.toString().toDoubleOrNull()
                            val cost = unitCost.text.toString().toDoubleOrNull()
                            if (qty == null || qty <= 0.0) {
                                quantity.error = "أدخل كمية أكبر من صفر"
                                return@setOnClickListener
                            }
                            if (cost == null || cost < 0.0) {
                                unitCost.error = "أدخل كلفة صحيحة"
                                return@setOnClickListener
                            }
                            val product = products[productSpinner.selectedItemPosition]
                            val money = currency.selectedItem.toString()
                            val total = qty * cost
                            lifecycleScope.launch {
                                try {
                                    val rate = if (money == "USD") 1.0 else (dao.getExchangeRate()?.usdToSyp ?: 1.0)
                                    val purchase = com.example.retailaccounting.data.Purchase(
                                        currency = money,
                                        exchangeRateAtPurchase = rate,
                                        total = total,
                                        supplierName = supplier.text.toString().trim().ifBlank { null },
                                        invoiceNumber = invoiceNumber.text.toString().trim()
                                    )
                                    val item = com.example.retailaccounting.data.PurchaseItem(
                                        purchaseId = 0,
                                        productId = product.id,
                                        productNameSnapshot = product.name,
                                        quantityInBaseUnit = qty,
                                        unitCostSnapshot = cost,
                                        lineTotal = total
                                    )
                                    dao.savePurchaseAndAddStock(purchase, listOf(item))
                                    Toast.makeText(this@MainActivity, "تم حفظ فاتورة الشراء وتحديث المخزون", Toast.LENGTH_LONG).show()
                                    dialog.dismiss()
                                } catch (error: Exception) {
                                    Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ الفاتورة", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    dialog.show()
                }
        }
    }

    private fun showAddProductDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
        }
        fun input(hint: String): EditText = EditText(this).apply {
            this.hint = hint
            textSize = 16f
        }
        val name = input("اسم الصنف")
        val code = input("رمز الصنف (اختياري)")
        val category = input("التصنيف (اختياري)")
        val unit = input("وحدة القياس، مثال: قطعة أو مل")
        val quantity = input("الكمية الافتتاحية").apply { inputType = 8194 }
        val cost = input("سعر التكلفة").apply { inputType = 8194 }
        val price = input("سعر البيع").apply { inputType = 8194 }
        val currency = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("USD", "SYP")
            )
        }
        box.addView(name); box.addView(code); box.addView(category); box.addView(unit)
        box.addView(quantity); box.addView(cost); box.addView(price); box.addView(currency)

        AlertDialog.Builder(this)
            .setTitle("إضافة صنف")
            .setView(box)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val productName = name.text.toString().trim()
                        if (productName.isBlank()) {
                            name.error = "اكتب اسم الصنف"
                            return@setOnClickListener
                        }
                        val product = Product(
                            name = productName,
                            code = code.text.toString().trim(),
                            category = category.text.toString().trim(),
                            baseUnit = unit.text.toString().trim().ifBlank { "قطعة" },
                            quantity = quantity.text.toString().toDoubleOrNull() ?: 0.0,
                            costPrice = cost.text.toString().toDoubleOrNull() ?: 0.0,
                            salePrice = price.text.toString().toDoubleOrNull() ?: 0.0,
                            currency = currency.selectedItem.toString()
                        )
                        lifecycleScope.launch {
                            dao.insertProduct(product)
                            dialog.dismiss()
                        }
                    }
                }
                dialog.show()
            }
    }
}
