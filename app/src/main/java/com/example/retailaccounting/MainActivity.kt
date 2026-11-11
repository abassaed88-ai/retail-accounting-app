package com.example.retailaccounting

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.retailaccounting.data.AppDatabase
import com.example.retailaccounting.data.Product
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    com.example.retailaccounting.data.DatabaseBackup.export(this@MainActivity, uri)
                    Toast.makeText(this@MainActivity, "تم إنشاء النسخة الاحتياطية بنجاح", Toast.LENGTH_LONG).show()
                    recreate()
                } catch (error: Exception) {
                    Toast.makeText(this@MainActivity, error.message ?: "فشل إنشاء النسخة الاحتياطية", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AlertDialog.Builder(this)
                .setTitle("تأكيد استعادة النسخة")
                .setMessage("سيتم استبدال بيانات التطبيق الحالية بالبيانات الموجودة في الملف المحدد. أنشئ نسخة احتياطية حالية قبل المتابعة.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("استعادة") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            com.example.retailaccounting.data.DatabaseBackup.restore(this@MainActivity, uri)
                            Toast.makeText(this@MainActivity, "تمت الاستعادة. سيعاد تشغيل التطبيق.", Toast.LENGTH_LONG).show()
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            finish()
                        } catch (error: Exception) {
                            Toast.makeText(this@MainActivity, error.message ?: "فشلت استعادة النسخة", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .show()
        }
    }

    private lateinit var list: LinearLayout
    private lateinit var searchInput: EditText
    private var productCache: List<Product> = emptyList()
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
        val invoicesButton = Button(this).apply { text = "▤ سجل فواتير المبيع والشراء"; setOnClickListener { showInvoiceHistory() } }
        val backupButton = Button(this).apply { text = "⬇ إنشاء نسخة احتياطية"; setOnClickListener { backupLauncher.launch("GoodGirl-Backup.db") } }
        val restoreButton = Button(this).apply { text = "⬆ استعادة نسخة احتياطية"; setOnClickListener { restoreLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) } }
        val archivedButton = Button(this).apply { text = "▣ الأصناف المؤرشفة"; setOnClickListener { showArchivedProducts() } }
        val section = TextView(this).apply {
            text = "الأصناف والمخزون"
            textSize = 20f
            setTextColor(0xFF351052.toInt())
            setPadding(4, 22, 4, 10)
        }
        searchInput = EditText(this).apply {
            hint = "بحث باسم الصنف أو الرمز أو التصنيف"
            textSize = 15f
            maxLines = 1
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderProducts(productCache) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
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
        page.addView(invoicesButton)
        page.addView(backupButton)
        page.addView(restoreButton)
        page.addView(archivedButton)
        page.addView(section)
        page.addView(searchInput)
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
        productCache = products
        val query = if (::searchInput.isInitialized) searchInput.text.toString().trim() else ""
        val visibleProducts = if (query.isBlank()) products else products.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }
        list.removeAllViews()
        if (visibleProducts.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (query.isBlank()) "لا توجد أصناف بعد. اضغط «إضافة صنف جديد» للبدء." else "لا توجد نتائج مطابقة للبحث."
                textSize = 16f
                setPadding(12, 20, 12, 20)
            })
        }
        visibleProducts.forEach { product ->
            val row = TextView(this).apply {
                text = "${product.name}\nالكمية: ${product.quantity} ${product.baseUnit}   |   " +
                    "سعر البيع: ${product.salePrice} ${product.currency}"
                textSize = 16f
                setTextColor(0xFF25202A.toInt())
                setPadding(18, 16, 18, 16)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            row.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(product.name)
                    .setMessage("رمز الصنف: " + product.code + "\nالتصنيف: " + product.category + "\nالكمية: " + product.quantity + " " + product.baseUnit)
                    .setPositiveButton("تعديل البيانات") { _, _ -> showEditProductDialog(product) }
                    .setNeutralButton("أرشفة الصنف") { _, _ ->
                        lifecycleScope.launch {
                            dao.archiveProduct(product.id)
                            Toast.makeText(this@MainActivity, "تمت أرشفة الصنف مع الحفاظ على فواتيره وحركاته", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("إغلاق", null)
                    .show()
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
            list.addView(row, params)
        }
    }



    private fun showArchivedProducts() {
        lifecycleScope.launch {
            val archived = dao.observeArchivedProducts().first()
            val panel = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 8) }
            if (archived.isEmpty()) {
                panel.addView(TextView(this@MainActivity).apply { text = "لا توجد أصناف مؤرشفة."; textSize = 16f })
            } else {
                archived.forEach { product ->
                    val row = TextView(this@MainActivity).apply {
                        text = product.name + " | " + product.code + "\nالكمية: " + product.quantity + " " + product.baseUnit
                        textSize = 15f
                        setPadding(14, 14, 14, 14)
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("استعادة الصنف")
                                .setMessage("إعادة تفعيل " + product.name + "؟")
                                .setNegativeButton("إلغاء", null)
                                .setPositiveButton("استعادة") { _, _ ->
                                    lifecycleScope.launch {
                                        dao.restoreProduct(product.id)
                                        Toast.makeText(this@MainActivity, "تمت استعادة الصنف", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .show()
                        }
                    }
                    panel.addView(row)
                }
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("الأصناف المؤرشفة - اضغط للاستعادة")
                .setView(ScrollView(this@MainActivity).apply { addView(panel) })
                .setPositiveButton("إغلاق", null)
                .show()
        }
    }

    private fun showInvoiceHistory() {
        lifecycleScope.launch {
            try {
                val sales = dao.observeSales().first()
                val purchases = dao.observePurchases().first()
                val entries = mutableListOf<Pair<Long, View>>()
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                sales.forEach { sale ->
                    val row = TextView(this@MainActivity).apply {
                        text = "مبيع رقم #" + sale.id + " | " + dateFormat.format(java.util.Date(sale.createdAt)) +
                            "\nالإجمالي: " + sale.total + " " + sale.currency +
                            " | " + if (sale.paymentType == "DEBT") "آجل" else "نقدي"
                        textSize = 15f
                        setPadding(16, 14, 16, 14)
                        setBackgroundColor(0xFFFFFFFF.toInt())
                        setOnClickListener {
                            lifecycleScope.launch {
                                val items = dao.getSaleItems(sale.id)
                                val details = items.joinToString("\n") { item ->
                                    item.productNameSnapshot + " | " + item.quantityInBaseUnit + " × " +
                                        item.unitPrice + " = " + item.lineTotal
                                }
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("تفاصيل فاتورة المبيع #" + sale.id)
                                    .setMessage("الزبون: " + (sale.customerName ?: "غير محدد") + "\n" + details +
                                        "\nالإجمالي: " + sale.total + " " + sale.currency)
                                    .setPositiveButton("إغلاق", null).show()
                            }
                        }
                    }
                    entries.add(sale.createdAt to row)
                }
                purchases.forEach { purchase ->
                    val row = TextView(this@MainActivity).apply {
                        text = "شراء رقم #" + purchase.id + " | " + dateFormat.format(java.util.Date(purchase.createdAt)) +
                            "\nالإجمالي: " + purchase.total + " " + purchase.currency +
                            " | المورد: " + (purchase.supplierName ?: "غير محدد")
                        textSize = 15f
                        setPadding(16, 14, 16, 14)
                        setBackgroundColor(0xFFFFFFFF.toInt())
                        setOnClickListener {
                            lifecycleScope.launch {
                                val items = dao.getPurchaseItems(purchase.id)
                                val details = items.joinToString("\n") { item ->
                                    item.productNameSnapshot + " | " + item.quantityInBaseUnit + " × " +
                                        item.unitCostSnapshot + " = " + item.lineTotal
                                }
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("تفاصيل فاتورة الشراء #" + purchase.id)
                                    .setMessage("المورد: " + (purchase.supplierName ?: "غير محدد") + "\n" + details +
                                        "\nالإجمالي: " + purchase.total + " " + purchase.currency)
                                    .setPositiveButton("إغلاق", null).show()
                            }
                        }
                    }
                    entries.add(purchase.createdAt to row)
                }
                val content = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 8) }
                if (entries.isEmpty()) {
                    content.addView(TextView(this@MainActivity).apply { text = "لا توجد فواتير محفوظة بعد."; textSize = 16f })
                } else {
                    entries.sortedByDescending { it.first }.forEach { content.addView(it.second) }
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("سجل الفواتير - اضغط لعرض التفاصيل")
                    .setView(ScrollView(this@MainActivity).apply { addView(content) })
                    .setPositiveButton("إغلاق", null)
                    .show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, error.message ?: "تعذر عرض سجل الفواتير", Toast.LENGTH_LONG).show()
            }
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
            val drafts = mutableListOf<com.example.retailaccounting.data.SaleItem>()
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
            val itemsPanel = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val totalLabel = TextView(this@MainActivity).apply { textSize = 17f; text = "إجمالي الفاتورة: 0" }
            fun refreshLines() {
                itemsPanel.removeAllViews()
                drafts.forEachIndexed { index, item ->
                    val row = TextView(this@MainActivity).apply {
                        text = (index + 1).toString() + ". " + item.productNameSnapshot + " | الكمية: " + item.quantityInBaseUnit + " | سعر الوحدة: " + item.unitPrice + " | الإجمالي: " + item.lineTotal + "\n(اضغط على السطر لحذفه)"
                        textSize = 14f; setPadding(12, 12, 12, 12)
                        setOnClickListener { drafts.removeAt(index); refreshLines() }
                    }
                    itemsPanel.addView(row)
                }
                totalLabel.text = "إجمالي الفاتورة: " + drafts.sumOf { it.lineTotal }
            }
            val addLine = Button(this@MainActivity).apply {
                text = "＋ إضافة صنف إلى المبيع"
                setOnClickListener {
                    val lineBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
                    val productSpinner = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, products.map { it.name }) }
                    val qtyInput = EditText(this@MainActivity).apply { hint = "الكمية المباعة"; inputType = 8194 }
                    val priceInput = EditText(this@MainActivity).apply { hint = "سعر بيع الوحدة"; inputType = 8194 }
                    productSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { priceInput.setText(products[position].salePrice.toString()) }
                    }
                    lineBox.addView(productSpinner); lineBox.addView(qtyInput); lineBox.addView(priceInput)
                    val lineDialog = AlertDialog.Builder(this@MainActivity).setTitle("إضافة صنف للفاتورة").setView(lineBox).setNegativeButton("إلغاء", null).setPositiveButton("إضافة", null).create()
                    lineDialog.setOnShowListener {
                        lineDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val qty = qtyInput.text.toString().toDoubleOrNull()
                            val price = priceInput.text.toString().toDoubleOrNull()
                            if (qty == null || !qty.isFinite() || qty <= 0.0) { qtyInput.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                            if (price == null || !price.isFinite() || price < 0.0) { priceInput.error = "أدخل سعراً صحيحاً"; return@setOnClickListener }
                            val product = products[productSpinner.selectedItemPosition]
                            if (drafts.any { it.productId == product.id }) { Toast.makeText(this@MainActivity, "الصنف موجود مسبقاً؛ احذف السطر وعدّل كميته", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                            if (drafts.isNotEmpty() && drafts.first().let { first -> products.first { it.id == first.productId }.currency != product.currency }) { Toast.makeText(this@MainActivity, "لا يمكن جمع عملتين مختلفتين في فاتورة واحدة", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                            val line = com.example.retailaccounting.domain.SaleCalculator.calculateLine(com.example.retailaccounting.domain.SaleLineInput(qty, 1.0, price, product.costPrice))
                            drafts.add(com.example.retailaccounting.data.SaleItem(saleId = 0, productId = product.id, productNameSnapshot = product.name, quantityInSelectedUnit = qty, selectedUnit = product.baseUnit, baseUnitsPerSelectedUnit = 1.0, quantityInBaseUnit = line.quantityInBaseUnit, unitPrice = price, unitCostSnapshot = product.costPrice, lineTotal = line.lineTotal))
                            refreshLines(); lineDialog.dismiss()
                        }
                    }
                    lineDialog.show()
                }
            }
            val customer = EditText(this@MainActivity).apply { hint = "اسم الزبون (مطلوب للبيع الآجل)" }
            val payment = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدي", "آجل")) }
            box.addView(addLine); box.addView(itemsPanel); box.addView(totalLabel); box.addView(customer); box.addView(payment)
            val dialog = AlertDialog.Builder(this@MainActivity).setTitle("فاتورة مبيع متعددة الأصناف").setView(ScrollView(this@MainActivity).apply { addView(box) }).setNegativeButton("إلغاء", null).setPositiveButton("حفظ الفاتورة", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (drafts.isEmpty()) { Toast.makeText(this@MainActivity, "أضف صنفاً واحداً على الأقل", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                    val customerName = customer.text.toString().trim()
                    val isDebt = payment.selectedItemPosition == 1
                    if (isDebt && customerName.isBlank()) { customer.error = "اسم الزبون مطلوب للبيع الآجل"; return@setOnClickListener }
                    val money = products.first { it.id == drafts.first().productId }.currency
                    val total = drafts.sumOf { it.lineTotal }
                    if (!total.isFinite()) { Toast.makeText(this@MainActivity, "إجمالي الفاتورة غير صالح", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                    lifecycleScope.launch {
                        try {
                            val exchange = if (money == "USD") 1.0 else (dao.getExchangeRate()?.usdToSyp ?: 0.0)
                            if (!exchange.isFinite() || exchange <= 0.0) throw IllegalStateException("أدخل سعر صرف صحيحاً قبل تسجيل المبيع بالليرة")
                            val sale = com.example.retailaccounting.data.Sale(currency = money, exchangeRateAtSale = exchange, subtotal = total, total = total, paymentType = if (isDebt) "DEBT" else "CASH", customerName = customerName.ifBlank { null })
                            val accountMovement = if (isDebt) com.example.retailaccounting.data.AccountMovement(partyType = "CUSTOMER", partyName = customerName, movementType = "SALE_CREDIT", amount = total, currency = money, note = "فاتورة مبيع آجلة") else null
                            val cashMovement = if (!isDebt) com.example.retailaccounting.data.CashMovement(direction = "IN", amount = total, currency = money, reason = "مبيع نقدي") else null
                            dao.saveSaleAndDeductStock(sale, drafts.toList(), accountMovement, cashMovement)
                            Toast.makeText(this@MainActivity, "تم حفظ فاتورة المبيع وتحديث المخزون والحساب", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } catch (error: Exception) { Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ فاتورة المبيع", Toast.LENGTH_LONG).show() }
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
            val drafts = mutableListOf<com.example.retailaccounting.data.PurchaseItem>()
            val itemsPanel = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val totalLabel = TextView(this@MainActivity).apply { textSize = 17f; text = "إجمالي الفاتورة: 0" }
            fun refreshLines() {
                itemsPanel.removeAllViews()
                drafts.forEachIndexed { index, item ->
                    val row = TextView(this@MainActivity).apply {
                        text = (index + 1).toString() + ". " + item.productNameSnapshot +
                            " | الكمية: " + item.quantityInBaseUnit +
                            " | الكلفة: " + item.unitCostSnapshot +
                            " | الإجمالي: " + item.lineTotal + "\n(اضغط على السطر لحذفه)"
                        textSize = 14f
                        setPadding(12, 12, 12, 12)
                        setOnClickListener {
                            drafts.removeAt(index)
                            refreshLines()
                        }
                    }
                    itemsPanel.addView(row)
                }
                totalLabel.text = "إجمالي الفاتورة: " + drafts.sumOf { it.lineTotal }
            }
            val addLine = Button(this@MainActivity).apply {
                text = "＋ إضافة صنف إلى الفاتورة"
                setOnClickListener {
                    val lineBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
                    val productSpinner = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, products.map { it.name }) }
                    val qtyInput = EditText(this@MainActivity).apply { hint = "الكمية"; inputType = 8194 }
                    val costInput = EditText(this@MainActivity).apply { hint = "كلفة الوحدة"; inputType = 8194 }
                    lineBox.addView(productSpinner); lineBox.addView(qtyInput); lineBox.addView(costInput)
                    val lineDialog = AlertDialog.Builder(this@MainActivity).setTitle("إضافة صنف للفاتورة").setView(lineBox).setNegativeButton("إلغاء", null).setPositiveButton("إضافة", null).create()
                    lineDialog.setOnShowListener {
                        lineDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val qty = qtyInput.text.toString().toDoubleOrNull()
                            val cost = costInput.text.toString().toDoubleOrNull()
                            if (qty == null || !qty.isFinite() || qty <= 0.0) { qtyInput.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                            if (cost == null || !cost.isFinite() || cost < 0.0) { costInput.error = "أدخل كلفة صحيحة"; return@setOnClickListener }
                            val product = products[productSpinner.selectedItemPosition]
                            val lineTotal = qty * cost
                            if (!lineTotal.isFinite()) { costInput.error = "إجمالي الصنف غير صالح"; return@setOnClickListener }
                            drafts.add(com.example.retailaccounting.data.PurchaseItem(
                                purchaseId = 0,
                                productId = product.id,
                                productNameSnapshot = product.name,
                                quantityInBaseUnit = qty,
                                unitCostSnapshot = cost,
                                lineTotal = lineTotal
                            ))
                            refreshLines()
                            lineDialog.dismiss()
                        }
                    }
                    lineDialog.show()
                }
            }
            val supplier = EditText(this@MainActivity).apply { hint = "اسم المورد" }
            val invoiceNumber = EditText(this@MainActivity).apply { hint = "رقم الفاتورة (اختياري)" }
            val currency = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP")) }
            val payment = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدي", "آجل")) }
            box.addView(addLine); box.addView(itemsPanel); box.addView(totalLabel)
            box.addView(supplier); box.addView(invoiceNumber); box.addView(currency); box.addView(payment)
            val dialog = AlertDialog.Builder(this@MainActivity).setTitle("فاتورة شراء متعددة الأصناف").setView(ScrollView(this@MainActivity).apply { addView(box) }).setNegativeButton("إلغاء", null).setPositiveButton("حفظ الفاتورة", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (drafts.isEmpty()) { Toast.makeText(this@MainActivity, "أضف صنفاً واحداً على الأقل", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                    val supplierName = supplier.text.toString().trim()
                    val isDebt = payment.selectedItemPosition == 1
                    if (isDebt && supplierName.isBlank()) { supplier.error = "اسم المورد مطلوب للشراء الآجل"; return@setOnClickListener }
                    val money = currency.selectedItem.toString()
                    val total = drafts.sumOf { it.lineTotal }
                    if (!total.isFinite()) { Toast.makeText(this@MainActivity, "إجمالي الفاتورة غير صالح", Toast.LENGTH_LONG).show(); return@setOnClickListener }
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
                            val accountMovement = if (isDebt) com.example.retailaccounting.data.AccountMovement(
                                partyType = "SUPPLIER", partyName = supplierName, movementType = "PURCHASE_CREDIT",
                                amount = total, currency = money, note = "فاتورة شراء آجلة"
                            ) else null
                            val cashMovement = if (!isDebt) com.example.retailaccounting.data.CashMovement(
                                direction = "OUT", amount = total, currency = money, reason = "فاتورة شراء"
                            ) else null
                            dao.savePurchaseAndAddStock(purchase, drafts.toList(), accountMovement, cashMovement)
                            Toast.makeText(this@MainActivity, "تم حفظ فاتورة الشراء وتحديث المخزون والحساب", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } catch (error: Exception) {
                            Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ فاتورة الشراء", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            dialog.show()
        }
    }
    private fun showEditProductDialog(product: Product) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutDirection = ViewGroup.LAYOUT_DIRECTION_RTL
        }
        fun field(label: String, value: String, numeric: Boolean = false): EditText {
            return EditText(this).apply {
                hint = label
                setText(value)
                textSize = 16f
                if (numeric) inputType = 8194
            }
        }
        val name = field("اسم الصنف", product.name)
        val code = field("رمز الصنف", product.code)
        val category = field("التصنيف", product.category)
        val unit = field("وحدة القياس", product.baseUnit)
        val quantity = field("الكمية الحالية", product.quantity.toString(), true)
        val cost = field("كلفة الوحدة الحالية", product.costPrice.toString(), true)
        val price = field("سعر البيع الحالي", product.salePrice.toString(), true)
        val currency = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP"))
            setSelection(if (product.currency == "SYP") 1 else 0)
        }
        listOf(name, code, category, unit, quantity, cost, price).forEach { box.addView(it) }
        box.addView(currency)
        val dialog = AlertDialog.Builder(this)
            .setTitle("تعديل بيانات الصنف")
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ التعديلات", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                val q = quantity.text.toString().toDoubleOrNull()
                val cp = cost.text.toString().toDoubleOrNull()
                val sp = price.text.toString().toDoubleOrNull()
                if (n.isBlank()) { name.error = "اسم الصنف مطلوب"; return@setOnClickListener }
                if (q == null || !q.isFinite()) { quantity.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                if (cp == null || !cp.isFinite() || cp < 0.0) { cost.error = "أدخل كلفة صحيحة"; return@setOnClickListener }
                if (sp == null || !sp.isFinite() || sp < 0.0) { price.error = "أدخل سعراً صحيحاً"; return@setOnClickListener }
                lifecycleScope.launch {
                    try {
                        dao.updateProduct(product.copy(
                            name = n,
                            code = code.text.toString().trim(),
                            category = category.text.toString().trim(),
                            baseUnit = unit.text.toString().trim().ifBlank { "قطعة" },
                            quantity = q,
                            costPrice = cp,
                            salePrice = sp,
                            currency = currency.selectedItem.toString()
                        ))
                        Toast.makeText(this@MainActivity, "تم تحديث بيانات الصنف؛ أرباح الفواتير السابقة محفوظة كما هي", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity, error.message ?: "تعذر تعديل الصنف", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
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
