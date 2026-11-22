package com.example.retailaccounting

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.retailaccounting.data.AppDatabase
import com.example.retailaccounting.data.Product
import com.example.retailaccounting.data.Purchase
import com.example.retailaccounting.data.PurchaseItem
import com.example.retailaccounting.data.Sale
import com.example.retailaccounting.data.SaleItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val blue = 0xFF438CA5.toInt()
    private val ink = 0xFF315F70.toInt()
    private val pale = 0xFFF3F3F3.toInt()
    private lateinit var dao: com.example.retailaccounting.data.StoreDao
    private lateinit var content: LinearLayout
    private var products: List<Product> = emptyList()
    private var activeTab = "الفواتير"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dao = AppDatabase.getInstance(this).storeDao()
        buildShell()
        lifecycleScope.launch {
            dao.observeProducts().collectLatest {
                products = it
                if (activeTab == "المخزون") showInventory()
            }
        }
    }

    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pale)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 12)
            setBackgroundColor(-1)
        }
        header.addView(label("Good Girl", 25f, blue, true))
        header.addView(label("إدارة المبيعات والمخزون", 13f, 0xFF777777.toInt()))
        root.addView(header)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(-1) }
        listOf("الفواتير", "الحسابات", "التقارير", "الإعدادات").forEach { name ->
            val tab = TextView(this).apply {
                text = name; textSize = 14f; gravity = Gravity.CENTER
                setTextColor(if (name == activeTab) blue else 0xFF888888.toInt())
                setPadding(3, 16, 3, 16)
                setTypeface(typeface, if (name == activeTab) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener { activeTab = name; buildShell() }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 18) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "↶       الصندوق الرئيسي"
            textSize = 17f; setTextColor(-1); setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(12, 15, 12, 15); setBackgroundColor(blue)
            setOnClickListener { showCashbox() }
        })
        setContentView(root)
        when (activeTab) {
            "الفواتير" -> showInvoices()
            "الحسابات" -> showAccounts()
            "التقارير" -> showReports()
            "الإعدادات" -> showSettings()
        }
    }

    private fun section(title: String) {
        content.addView(label(title, 19f, ink, true).apply { setPadding(4, 10, 4, 14) })
    }

    private fun card(title: String, icon: String = "◆", action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(18, 17, 18, 17)
            setBackgroundColor(-1); elevation = 3f; setOnClickListener { action() }
        }
        row.addView(label(icon, 24f, blue, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(48, 48))
        row.addView(label(title, 17f, blue, true).apply { setPadding(10, 0, 8, 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12; marginStart = 2; marginEnd = 2 })
    }

    private fun info(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("حسناً", null).show()
    }

    private fun showInvoices() {
        content.removeAllViews(); section("الفواتير والمخزون")
        card("إنشاء فاتورة مبيع", "🛒") { createSale() }
        card("إنشاء فاتورة شراء", "📦") { createPurchase() }
        card("المخزون وقائمة المواد", "▦") { activeTab = "المخزون"; showInventory() }
        card("سجل الفواتير السابقة", "▤") { showInvoiceHistory() }
        card("المرتجعات", "↩") { info("المرتجعات", "إدارة مرتجعات البيع والشراء.") }
        card("عروض الأسعار وطلبات الشراء", "◆") { info("العروض والطلبات", "تظهر ضمن القوائم الفرعية.") }
    }


    private fun showInvoiceHistory() {
        lifecycleScope.launch {
            try {
                val sales = dao.observeSales().first()
                val purchases = dao.observePurchases().first()
                val entries = (sales.map { sale ->
                    Triple(sale.createdAt, "مبيع #${sale.id}", "إجمالي: ${sale.total} ${sale.currency}")
                } + purchases.map { purchase ->
                    Triple(purchase.createdAt, "شراء #${purchase.id}", "إجمالي: ${purchase.total} ${purchase.currency}")
                }).sortedByDescending { it.first }
                if (entries.isEmpty()) {
                    info("سجل الفواتير", "لا توجد فواتير محفوظة حتى الآن.")
                    return@launch
                }
                val labels = entries.map { entry ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(entry.first))
                    "${entry.second} — ${entry.third}\n$date"
                }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("سجل فواتير البيع والشراء")
                    .setItems(labels) { _, which ->
                        val selected = entries[which]
                        lifecycleScope.launch {
                            val detail = if (selected.second.startsWith("مبيع")) {
                                val id = selected.second.substringAfter("#").toLong()
                                dao.getSaleItems(id).joinToString("\n") {
                                    "${it.productNameSnapshot}: ${it.quantityInSelectedUnit} ${it.selectedUnit} × ${it.unitPrice} = ${it.lineTotal}"
                                }
                            } else {
                                val id = selected.second.substringAfter("#").toLong()
                                dao.getPurchaseItems(id).joinToString("\n") {
                                    "${it.productNameSnapshot}: ${it.quantity} × ${it.unitCost} = ${it.lineTotal} (سعر البيع المحدد: ${it.salePriceSet})"
                                }
                            }
                            info(selected.second, if (detail.isBlank()) "لا توجد تفاصيل لهذه الفاتورة." else detail)
                        }
                    }
                    .setPositiveButton("إغلاق", null)
                    .show()
            } catch (e: Exception) {
                info("سجل الفواتير", e.message ?: "تعذر تحميل سجل الفواتير.")
            }
        }
    }

    private fun showInventory() {
        content.removeAllViews(); section("المخزون")
        card("إضافة مادة جديدة", "＋") { addProduct() }
        card("قائمة المواد (${products.size})", "▤") {
            val msg = if (products.isEmpty()) "لا توجد مواد مسجلة." else products.joinToString("\n\n") {
                "${it.name}\nالكمية: ${it.quantity} ${it.baseUnit} | سعر البيع: ${it.salePrice} ${it.currency}"
            }
            info("قائمة المواد", msg)
        }
        card("إدخال بضاعة وتسوية المخزون", "▦") { info("حركة المخزون", "إدخال البضاعة والتسوية مع سجل حركة لكل مادة.") }
        card("جرد المستودع", "▣") { info("جرد المستودع", "جرد الكميات المسجلة ومراجعتها.") }
        card("المواد منخفضة الكمية", "↓") {
            val low = products.filter { it.lowStockLimit > 0 && it.quantity <= it.lowStockLimit }
            info("تنبيه المخزون", if (low.isEmpty()) "لا توجد مواد مسجلة تحت حد التنبيه." else low.joinToString("\n") { "${it.name}: ${it.quantity}" })
        }
    }


    private fun showCashbox() {
        lifecycleScope.launch {
            try {
                val movements = dao.observeCashMovements().first()
                if (movements.isEmpty()) {
                    info("الصندوق الرئيسي", "لا توجد حركات مالية مسجلة حتى الآن.")
                    return@launch
                }
                val currencies = movements.map { it.currency }.distinct().sorted()
                val summary = currencies.joinToString("\\n") { currency ->
                    val incoming = movements.filter { it.currency == currency && it.direction == "IN" }.sumOf { it.amount }
                    val outgoing = movements.filter { it.currency == currency && it.direction == "OUT" }.sumOf { it.amount }
                    "$currency — المقبوضات: $incoming | المدفوعات: $outgoing | الرصيد: ${incoming - outgoing}"
                }
                val latest = movements.take(30).joinToString("\\n\\n") { movement ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(movement.createdAt))
                    val direction = if (movement.direction == "IN") "قبض" else "دفع"
                    "$direction: ${movement.amount} ${movement.currency}\\n${movement.reason}\\n$date"
                }
                info("الصندوق الرئيسي", "$summary\\n\\nآخر الحركات:\\n$latest")
            } catch (e: Exception) {
                info("الصندوق الرئيسي", e.message ?: "تعذر تحميل حركة الصندوق.")
            }
        }
    }


    private fun createExpense() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val titleInput = EditText(this).apply { hint = "بيان المصروف، مثال: أجرة أو كهرباء" }
        val amountInput = EditText(this).apply { hint = "قيمة المصروف"; inputType = 8194 }
        val noteInput = EditText(this).apply { hint = "ملاحظات (اختياري)" }
        val currency = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP"))
        }
        box.addView(titleInput); box.addView(amountInput); box.addView(currency); box.addView(noteInput)
        val dialog = AlertDialog.Builder(this).setTitle("تسجيل مصروف نقدي")
            .setView(box).setNegativeButton("إلغاء", null).setPositiveButton("حفظ المصروف", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull()
                if (title.isBlank()) { titleInput.error = "أدخل بيان المصروف"; return@setOnClickListener }
                if (amount == null || amount <= 0.0) { amountInput.error = "أدخل قيمة صحيحة أكبر من صفر"; return@setOnClickListener }
                lifecycleScope.launch {
                    try {
                        val expense = com.example.retailaccounting.data.Expense(
                            title = title, amount = amount, currency = currency.selectedItem.toString(),
                            note = noteInput.text.toString().trim()
                        )
                        dao.saveExpenseAndCashMovement(expense)
                        dialog.dismiss()
                        info("تم تسجيل المصروف", "$title — $amount ${expense.currency}\\nتم خصم المبلغ من حركة الصندوق.")
                    } catch (e: Exception) {
                        info("تعذر تسجيل المصروف", e.message ?: "حدث خطأ غير متوقع.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showAccounts() {
        content.removeAllViews(); section("الحسابات والصندوق")
        card("حركة الصندوق", "▣") { showCashbox() }
        card("الديون والحسابات", "♙") { info("الحسابات", "قوائم العملاء والموردين وأرصدتهم.") }
        card("قبض من حساب / دفع لحساب", "⇄") { info("القبض والدفع", "تسجيل الحركات وربطها برصيد الحساب.") }
        card("تسجيل المصاريف", "−") { createExpense() }
        card("الإيرادات الأخرى", "+") { info("الإيرادات", "تسجيل الإيرادات غير الناتجة عن المبيعات.") }
    }


    private fun showProfitReport() {
        lifecycleScope.launch {
            try {
                val summaries = dao.getProfitSummary()
                if (summaries.isEmpty()) {
                    info("الأرباح والخسائر", "لا توجد مبيعات مسجلة بعد.")
                    return@launch
                }
                val report = summaries.joinToString("\\n\\n") { item ->
                    val profit = item.revenue - item.cost
                    "العملة: ${item.currency}\\nإجمالي المبيعات: ${item.revenue}\\nكلفة البضاعة المباعة: ${item.cost}\\nمجمل الربح: $profit"
                }
                info("مجمل الأرباح حسب العملة", "$report\\n\\nملاحظة: صافي الربح هنا بعد خصم المصاريف المسجلة فقط، ولا يشمل أي مصاريف أو التزامات لم تُسجل.")
            } catch (e: Exception) {
                info("الأرباح والخسائر", e.message ?: "تعذر حساب الأرباح.")
            }
        }
    }

    private fun showReports() {
        content.removeAllViews(); section("التقارير")
        card("ملخص الحركة اليومية", "▤") { info("الحركة اليومية", "ملخص المبيعات والمشتريات وحركة الصندوق.") }
        card("الأرباح والخسائر", "⌁") { showProfitReport() }
        card("رأس المال والموجودات", "◉") { info("رأس المال", "قيمة المخزون والصندوق والالتزامات.") }
        card("المواد الأكثر والأقل حركة", "▥") { info("حركة المواد", "ترتيب المواد حسب المبيعات خلال فترة محددة.") }
    }

    private fun showSettings() {
        content.removeAllViews(); section("الإعدادات")
        card("العملة وسعر الصرف", "$") { info("العملة", "إعداد العملة وسعر الصرف.") }
        card("النسخ الاحتياطي والاستعادة", "☁") { info("النسخ الاحتياطي", "إضافة تصدير واستعادة قاعدة البيانات والتحقق من سلامتها.") }
        card("حول التطبيق", "ⓘ") { info("Good Girl", "تطبيق محاسبة وإدارة مخزون للمحل.") }
    }



    private fun createSale() {
        if (products.isEmpty()) {
            info("فاتورة مبيع", "أضف مادة إلى المخزون أولاً حتى تتمكن من تسجيل عملية بيع.")
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val rows = mutableListOf<Triple<Spinner, EditText, EditText>>()
        fun addRow() {
            val rowBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val picker = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    products.map { "${it.name} — المتوفر: ${it.quantity} ${it.baseUnit}" })
            }
            val qty = EditText(this).apply { hint = "الكمية المباعة"; inputType = 8194 }
            val price = EditText(this).apply { hint = "سعر بيع الوحدة"; inputType = 8194 }
            price.setText(products.first().salePrice.toString())
            picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    price.setText(products[position].salePrice.toString())
                }
            }
            rowBox.addView(label("المادة", 14f, ink, true))
            rowBox.addView(picker); rowBox.addView(qty); rowBox.addView(price)
            box.addView(rowBox)
            rows.add(Triple(picker, qty, price))
        }
        addRow()
        val addButton = Button(this).apply { text = "＋ إضافة مادة للفاتورة"; setOnClickListener { addRow() } }
        box.addView(addButton)
        val dialog = AlertDialog.Builder(this).setTitle("فاتورة مبيع متعددة المواد").setView(box)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ الفاتورة", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = mutableListOf<Pair<Product, Pair<Double, Double>>>()
                for ((picker, qtyInput, priceInput) in rows) {
                    val product = products.getOrNull(picker.selectedItemPosition) ?: continue
                    val qty = qtyInput.text.toString().toDoubleOrNull()
                    val price = priceInput.text.toString().toDoubleOrNull()
                    if (qty == null || qty <= 0.0) { qtyInput.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                    if (price == null || price < 0.0) { priceInput.error = "أدخل سعر بيع صحيح"; return@setOnClickListener }
                    parsed.add(product to (qty to price))
                }
                if (parsed.isEmpty()) { info("فاتورة مبيع", "أضف مادة واحدة على الأقل."); return@setOnClickListener }
                val currency = parsed.first().first.currency
                if (parsed.any { it.first.currency != currency }) {
                    info("اختلاف العملة", "يجب أن تكون جميع مواد الفاتورة بنفس العملة."); return@setOnClickListener
                }
                val requestedByProduct = parsed.groupBy { it.first.id }.mapValues { entry -> entry.value.sumOf { it.second.first } }
                for ((productId, requested) in requestedByProduct) {
                    val product = products.first { it.id == productId }
                    if (requested > product.quantity) {
                        info("المخزون غير كافٍ", "الكمية المطلوبة من ${product.name} أكبر من المتوفر."); return@setOnClickListener
                    }
                }
                val total = parsed.sumOf { it.second.first * it.second.second }
                val items = parsed.map { (product, values) ->
                    val (qty, price) = values
                    SaleItem(saleId = 0, productId = product.id, productNameSnapshot = product.name,
                        quantityInSelectedUnit = qty, selectedUnit = product.baseUnit,
                        baseUnitsPerSelectedUnit = 1.0, quantityInBaseUnit = qty,
                        unitPrice = price, unitCostSnapshot = product.costPrice, lineTotal = qty * price)
                }
                lifecycleScope.launch {
                    try {
                        val sale = Sale(currency = currency,
                            exchangeRateAtSale = dao.getExchangeRate()?.usdToSyp ?: 1.0,
                            subtotal = total, total = total, paymentType = "CASH")
                        dao.saveSaleAndDeductStock(sale, items)
                        dialog.dismiss()
                        info("تم حفظ الفاتورة", "عدد المواد: ${items.size}\nإجمالي الفاتورة: $total $currency\nتم تحديث المخزون وحفظ كلفة كل مادة وقت البيع.")
                    } catch (e: Exception) {
                        info("تعذر حفظ الفاتورة", e.message ?: "حدث خطأ غير متوقع.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun createPurchase() {
        if (products.isEmpty()) {
            info("فاتورة شراء", "أضف المادة إلى قائمة المواد أولاً، ثم سجّل شراءها من هنا.")
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val rows = mutableListOf<Triple<Spinner, EditText, Pair<EditText, EditText>>>()
        fun addRow() {
            val rowBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
            val picker = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                    products.map { "${it.name} — الرصيد الحالي: ${it.quantity} ${it.baseUnit}" })
            }
            val qty = EditText(this).apply { hint = "الكمية المشتراة"; inputType = 8194 }
            val cost = EditText(this).apply { hint = "كلفة شراء الوحدة"; inputType = 8194 }
            val salePrice = EditText(this).apply { hint = "سعر البيع الجديد (أدخله يدوياً)"; inputType = 8194 }
            cost.setText(products.first().costPrice.toString())
            salePrice.setText(products.first().salePrice.toString())
            picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    cost.setText(products[position].costPrice.toString())
                    salePrice.setText(products[position].salePrice.toString())
                }
            }
            rowBox.addView(label("المادة", 14f, ink, true))
            rowBox.addView(picker); rowBox.addView(qty); rowBox.addView(cost); rowBox.addView(salePrice)
            box.addView(rowBox)
            rows.add(Triple(picker, qty, cost to salePrice))
        }
        addRow()
        box.addView(Button(this).apply { text = "＋ إضافة مادة للفاتورة"; setOnClickListener { addRow() } })
        val dialog = AlertDialog.Builder(this).setTitle("فاتورة شراء متعددة المواد").setView(box)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ فاتورة الشراء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = mutableListOf<PurchaseItem>()
                var currency: String? = null
                for ((picker, qtyInput, priceInputs) in rows) {
                    val product = products.getOrNull(picker.selectedItemPosition) ?: continue
                    val qty = qtyInput.text.toString().toDoubleOrNull()
                    val unitCost = priceInputs.first.text.toString().toDoubleOrNull()
                    val salePrice = priceInputs.second.text.toString().toDoubleOrNull()
                    if (qty == null || qty <= 0.0) { qtyInput.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                    if (unitCost == null || unitCost < 0.0) { priceInputs.first.error = "أدخل كلفة صحيحة"; return@setOnClickListener }
                    if (salePrice == null || salePrice < 0.0) { priceInputs.second.error = "أدخل سعر بيع صحيح"; return@setOnClickListener }
                    if (currency != null && currency != product.currency) {
                        info("اختلاف العملة", "يجب أن تكون جميع مواد الفاتورة بنفس العملة."); return@setOnClickListener
                    }
                    currency = product.currency
                    parsed.add(PurchaseItem(purchaseId = 0, productId = product.id,
                        productNameSnapshot = product.name, quantity = qty, unitCost = unitCost,
                        salePriceSet = salePrice, lineTotal = qty * unitCost))
                }
                if (parsed.isEmpty()) { info("فاتورة شراء", "أضف مادة واحدة على الأقل."); return@setOnClickListener }
                val total = parsed.sumOf { it.lineTotal }
                lifecycleScope.launch {
                    try {
                        val purchase = Purchase(currency = currency ?: "USD", subtotal = total, total = total)
                        dao.savePurchaseAndAddStock(purchase, parsed)
                        dialog.dismiss()
                        info("تم حفظ فاتورة الشراء", "عدد المواد: ${parsed.size}\nإجمالي الشراء: $total ${currency}\nتمت إضافة الكميات وتحديث الكلفة وسعر البيع اليدوي لكل مادة.")
                    } catch (e: Exception) {
                        info("تعذر حفظ فاتورة الشراء", e.message ?: "حدث خطأ غير متوقع.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun addProduct() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        fun input(h: String) = EditText(this).apply { hint = h; textSize = 16f }
        val name = input("اسم المادة")
        val category = input("التصنيف (اختياري)")
        val unit = input("وحدة القياس، مثال: قطعة أو مل")
        val quantity = input("الكمية الافتتاحية").apply { inputType = 8194 }
        val cost = input("سعر التكلفة").apply { inputType = 8194 }
        val price = input("سعر البيع").apply { inputType = 8194 }
        val currency = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP"))
        }
        listOf<View>(name, category, unit, quantity, cost, price, currency).forEach { box.addView(it) }
        val dialog = AlertDialog.Builder(this).setTitle("إضافة مادة").setView(box)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                if (n.isBlank()) { name.error = "اكتب اسم المادة"; return@setOnClickListener }
                val item = Product(
                    name = n, category = category.text.toString().trim(),
                    baseUnit = unit.text.toString().trim().ifBlank { "قطعة" },
                    quantity = quantity.text.toString().toDoubleOrNull() ?: 0.0,
                    costPrice = cost.text.toString().toDoubleOrNull() ?: 0.0,
                    salePrice = price.text.toString().toDoubleOrNull() ?: 0.0,
                    currency = currency.selectedItem.toString()
                )
                lifecycleScope.launch { dao.insertProduct(item); dialog.dismiss() }
            }
        }
        dialog.show()
    }
}