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
        val reportButton = Button(this).apply { text = "▤ التقارير اليومية والأسبوعية والشهرية والسنوية"; setOnClickListener { showReportPeriod() } }
        val paymentButton = Button(this).apply { text = "↔ تسجيل دفعة زبون / مورد"; setOnClickListener { showPartyPaymentDialog() } }
        val accountsButton = Button(this).apply { text = "▤ أرصدة الزبائن والموردين"; setOnClickListener { showAccountBalances() } }
        val exchangeButton = Button(this).apply { text = "سعر صرف الدولار"; setOnClickListener { showExchangeRateDialog() } }
        val cashButton = Button(this).apply { text = "▣ حركة الصندوق والأرصدة"; setOnClickListener { showCashSummary() } }
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
        page.addView(reportButton)
        page.addView(paymentButton)
        page.addView(accountsButton)
        page.addView(exchangeButton)
        page.addView(cashButton)
        page.addView(section)
        page.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(page)
        showPasscodeGate()

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



    private fun showPasscodeGate() {
        val firstSetup = !com.example.retailaccounting.security.PasscodeStore.hasPasscode(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val code = EditText(this).apply {
            hint = if (firstSetup) "أنشئ الرمز السري (4 أرقام على الأقل)" else "أدخل الرمز السري"
            inputType = 18
        }
        val confirm = if (firstSetup) EditText(this).apply {
            hint = "أعد إدخال الرمز السري"
            inputType = 18
        } else null
        box.addView(code)
        confirm?.let { box.addView(it) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (firstSetup) "إنشاء رمز حماية Good Girl" else "التحقق من الرمز السري")
            .setMessage(if (firstSetup) "احفظ الرمز في مكان آمن؛ ستحتاج إليه عند فتح التطبيق." else "أدخل رمز الحماية للمتابعة.")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton(if (firstSetup) "حفظ الرمز" else "دخول", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = code.text.toString()
                if (value.length < 4 || value.any { it !in '0'..'9' }) {
                    code.error = "استخدم 4 أرقام على الأقل"
                    return@setOnClickListener
                }
                if (firstSetup) {
                    if (confirm?.text?.toString() != value) {
                        confirm?.error = "الرمزان غير متطابقين"
                        return@setOnClickListener
                    }
                    com.example.retailaccounting.security.PasscodeStore.save(this, value)
                    Toast.makeText(this, "تم إنشاء الرمز السري", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else {
                    if (com.example.retailaccounting.security.PasscodeStore.verify(this, value)) {
                        dialog.dismiss()
                    } else {
                        code.error = "الرمز غير صحيح"
                        code.setText("")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showCashSummary() {
        lifecycleScope.launch {
            try {
                val movements = dao.observeCashMovements().first()
                val currencies = listOf("USD", "SYP")
                val report = currencies.joinToString("\n\n") { currency ->
                    val rows = movements.filter { it.currency == currency }
                    val incoming = rows.filter { it.direction == "IN" }.sumOf { it.amount }
                    val outgoing = rows.filter { it.direction == "OUT" }.sumOf { it.amount }
                    "العملة: " + currency +
                        "\nإجمالي المقبوضات: " + incoming +
                        "\nإجمالي المدفوعات: " + outgoing +
                        "\nصافي حركة الصندوق: " + (incoming - outgoing) +
                        "\nعدد الحركات: " + rows.size
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("ملخص حركة الصندوق")
                    .setMessage(report)
                    .setPositiveButton("إغلاق", null)
                    .show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, error.message ?: "تعذر إعداد ملخص الصندوق", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showExchangeRateDialog() {
        val input = EditText(this).apply {
            hint = "كم ليرة سورية تساوي دولاراً واحداً؟"
            inputType = 8194
        }
        lifecycleScope.launch {
            val current = dao.getExchangeRate()
            if (current != null) input.setText(current.usdToSyp.toString())
            AlertDialog.Builder(this@MainActivity)
                .setTitle("تحديد سعر الصرف USD / SYP")
                .setView(input)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create().also { dialog ->
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val value = input.text.toString().toDoubleOrNull()
                            if (value == null || !value.isFinite() || value <= 0.0) {
                                input.error = "أدخل سعر صرف صحيحاً"
                                return@setOnClickListener
                            }
                            lifecycleScope.launch {
                                try {
                                    dao.updateExchangeRate(value)
                                    Toast.makeText(this@MainActivity, "تم حفظ سعر الصرف", Toast.LENGTH_LONG).show()
                                    dialog.dismiss()
                                } catch (error: Exception) {
                                    Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ سعر الصرف", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    dialog.show()
                }
        }
    }

    private fun showPartyPaymentDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val partyType = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("زبون", "مورد")) }
        val name = EditText(this).apply { hint = "اسم الزبون أو المورد" }
        val amount = EditText(this).apply { hint = "قيمة الدفعة"; inputType = 8194 }
        val currency = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP")) }
        box.addView(partyType); box.addView(name); box.addView(amount); box.addView(currency)
        val dialog = AlertDialog.Builder(this).setTitle("تسجيل دفعة").setView(box).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val party = name.text.toString().trim()
                val value = amount.text.toString().toDoubleOrNull()
                if (party.isBlank()) { name.error = "الاسم مطلوب"; return@setOnClickListener }
                if (value == null || !value.isFinite() || value <= 0.0) { amount.error = "أدخل مبلغاً صحيحاً"; return@setOnClickListener }
                lifecycleScope.launch {
                    try {
                        val isCustomer = partyType.selectedItemPosition == 0
                        dao.recordPartyPayment(
                            partyType = if (isCustomer) "CUSTOMER" else "SUPPLIER",
                            partyName = party,
                            amount = value,
                            currency = currency.selectedItem.toString(),
                            direction = if (isCustomer) "IN" else "OUT"
                        )
                        Toast.makeText(this@MainActivity, "تم تسجيل الدفعة وتحديث الحساب والصندوق", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } catch (error: Exception) { Toast.makeText(this@MainActivity, error.message ?: "تعذر تسجيل الدفعة", Toast.LENGTH_LONG).show() }
                }
            }
        }
        dialog.show()
    }

    private fun showAccountBalances() {
        lifecycleScope.launch {
            try {
                val movements = dao.observeAccountMovements().first()
                val balances = com.example.retailaccounting.domain.AccountBalanceCalculator.calculate(movements)
                val message = if (balances.isEmpty()) "لا توجد حركات حساب مسجلة بعد." else balances.joinToString("\n") {
                    (if (it.partyType == "CUSTOMER") "زبون: " else "مورد: ") + it.partyName + "\nالرصيد: " + it.balance + " " + it.currency
                }
                AlertDialog.Builder(this@MainActivity).setTitle("أرصدة الزبائن والموردين").setMessage(message).setPositiveButton("إغلاق", null).show()
            } catch (error: Exception) { Toast.makeText(this@MainActivity, error.message ?: "تعذر قراءة الأرصدة", Toast.LENGTH_LONG).show() }
        }
    }

    private fun showReportPeriod() {
        val periods = listOf("اليوم", "هذا الأسبوع", "هذا الشهر", "هذه السنة")
        AlertDialog.Builder(this)
            .setTitle("اختر فترة التقرير")
            .setItems(periods.toTypedArray()) { _, which ->
                lifecycleScope.launch {
                    try {
                        val start = java.util.Calendar.getInstance()
                        when (which) {
                            0 -> {
                                start.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                start.set(java.util.Calendar.MINUTE, 0)
                                start.set(java.util.Calendar.SECOND, 0)
                                start.set(java.util.Calendar.MILLISECOND, 0)
                            }
                            1 -> {
                                start.set(java.util.Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
                                start.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                start.set(java.util.Calendar.MINUTE, 0)
                                start.set(java.util.Calendar.SECOND, 0)
                                start.set(java.util.Calendar.MILLISECOND, 0)
                            }
                            2 -> {
                                start.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                start.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                start.set(java.util.Calendar.MINUTE, 0)
                                start.set(java.util.Calendar.SECOND, 0)
                                start.set(java.util.Calendar.MILLISECOND, 0)
                            }
                            3 -> {
                                start.set(java.util.Calendar.DAY_OF_YEAR, 1)
                                start.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                start.set(java.util.Calendar.MINUTE, 0)
                                start.set(java.util.Calendar.SECOND, 0)
                                start.set(java.util.Calendar.MILLISECOND, 0)
                            }
                        }
                        val end = (start.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, if (which == 0) 1 else 0) }
                        if (which != 0) {
                            when (which) {
                                1 -> end.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                                2 -> end.add(java.util.Calendar.MONTH, 1)
                                3 -> end.add(java.util.Calendar.YEAR, 1)
                            }
                        }
                        val sales = dao.observeSales().first()
                        val items = dao.observeSaleItems().first()
                        val expenses = dao.observeExpenses().first()
                        val usd = com.example.retailaccounting.domain.ReportCalculator.calculate(sales, items, expenses, start.timeInMillis, end.timeInMillis, "USD")
                        val syp = com.example.retailaccounting.domain.ReportCalculator.calculate(sales, items, expenses, start.timeInMillis, end.timeInMillis, "SYP")
                        val report = "الفترة: " + periods[which] + "\n\nالدولار USD\nالمبيعات: " + usd.sales + "\nكلفة البضاعة: " + usd.costOfGoods + "\nالربح الإجمالي: " + usd.grossProfit + "\nالمصروفات: " + usd.expenses + "\nصافي الربح: " + usd.netProfit + "\nعدد الفواتير: " + usd.salesCount + "\n\nالليرة SYP\nالمبيعات: " + syp.sales + "\nكلفة البضاعة: " + syp.costOfGoods + "\nالربح الإجمالي: " + syp.grossProfit + "\nالمصروفات: " + syp.expenses + "\nصافي الربح: " + syp.netProfit + "\nعدد الفواتير: " + syp.salesCount
                        AlertDialog.Builder(this@MainActivity).setTitle("ملخص المبيعات والأرباح").setMessage(report).setPositiveButton("إغلاق", null).show()
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity, error.message ?: "تعذر إعداد التقرير", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
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
                    val customerName = customer.text.toString().trim()
                    if (payment.selectedItemPosition == 1 && customerName.isBlank()) {
                        customer.error = "اسم الزبون مطلوب للبيع الآجل"
                        return@setOnClickListener
                    }
                    val line = com.example.retailaccounting.domain.SaleCalculator.calculateLine(com.example.retailaccounting.domain.SaleLineInput(quantity, 1.0, salePrice, product.costPrice))
                    lifecycleScope.launch {
                        try {
                            val exchange = if (product.currency == "USD") 1.0 else (dao.getExchangeRate()?.usdToSyp ?: 0.0)
                            if (!exchange.isFinite() || exchange <= 0.0) throw IllegalStateException("أدخل سعر صرف صحيحاً قبل تسجيل المبيع بالليرة")
                            val sale = com.example.retailaccounting.data.Sale(currency = product.currency, exchangeRateAtSale = exchange, subtotal = line.lineTotal, total = line.lineTotal, paymentType = if (payment.selectedItemPosition == 0) "CASH" else "DEBT", customerName = customerName.ifBlank { null })
                            val item = com.example.retailaccounting.data.SaleItem(saleId = 0, productId = product.id, productNameSnapshot = product.name, quantityInSelectedUnit = quantity, selectedUnit = product.baseUnit, baseUnitsPerSelectedUnit = 1.0, quantityInBaseUnit = line.quantityInBaseUnit, unitPrice = salePrice, unitCostSnapshot = product.costPrice, lineTotal = line.lineTotal)
                            val accountMovement = if (sale.paymentType == "DEBT") {
                                com.example.retailaccounting.data.AccountMovement(
                                    partyType = "CUSTOMER",
                                    partyName = customerName,
                                    movementType = "SALE_CREDIT",
                                    amount = sale.total,
                                    currency = sale.currency,
                                    note = "فاتورة مبيع آجلة"
                                )
                            } else null
                            val cashMovement = if (sale.paymentType == "CASH") {
                                com.example.retailaccounting.data.CashMovement(
                                    direction = "IN",
                                    amount = sale.total,
                                    currency = sale.currency,
                                    reason = "مبيع نقدي"
                                )
                            } else null
                            dao.saveSaleAndDeductStock(sale, listOf(item), accountMovement, cashMovement)
                            Toast.makeText(this@MainActivity, "تم حفظ فاتورة المبيع وتحديث المخزون", Toast.LENGTH_LONG).show()
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
            val payment = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدي", "آجل"))
            }
            box.addView(productSpinner)
            box.addView(quantity)
            box.addView(unitCost)
            box.addView(supplier)
            box.addView(invoiceNumber)
            box.addView(currency)
            box.addView(payment)
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
                            val supplierName = supplier.text.toString().trim()
                            val isDebt = payment.selectedItemPosition == 1
                            if (isDebt && supplierName.isBlank()) {
                                supplier.error = "اسم المورد مطلوب للشراء الآجل"
                                return@setOnClickListener
                            }
                            val total = qty * cost
                            if (!total.isFinite()) { unitCost.error = "قيمة الفاتورة كبيرة أو غير صالحة"; return@setOnClickListener }
                            lifecycleScope.launch {
                                try {
                                    val rate = if (money == "USD") 1.0 else (dao.getExchangeRate()?.usdToSyp ?: 0.0)
                                    if (!rate.isFinite() || rate <= 0.0) throw IllegalStateException("أدخل سعر صرف صحيحاً قبل تسجيل فاتورة بالليرة")
                                    val purchase = com.example.retailaccounting.data.Purchase(
                                        currency = money,
                                        exchangeRateAtPurchase = rate,
                                        total = total,
                                        supplierName = supplierName.ifBlank { null },
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
                                    val accountMovement = if (isDebt) {
                                        com.example.retailaccounting.data.AccountMovement(
                                            partyType = "SUPPLIER",
                                            partyName = supplierName,
                                            movementType = "PURCHASE_CREDIT",
                                            amount = total,
                                            currency = money,
                                            note = "فاتورة شراء آجلة"
                                        )
                                    } else null
                                    val cashMovement = if (!isDebt) {
                                        com.example.retailaccounting.data.CashMovement(
                                            direction = "OUT",
                                            amount = total,
                                            currency = money,
                                            reason = "فاتورة شراء"
                                        )
                                    } else null
                                    dao.savePurchaseAndAddStock(purchase, listOf(item), accountMovement, cashMovement)
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
