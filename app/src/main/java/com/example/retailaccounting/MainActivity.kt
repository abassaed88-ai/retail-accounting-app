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
            text = "↶       0                         الصندوق الرئيسي"
            textSize = 17f; setTextColor(-1); setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(12, 15, 12, 15); setBackgroundColor(blue)
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

    private fun showAccounts() {
        content.removeAllViews(); section("الحسابات والصندوق")
        card("حركة الصندوق", "▣") { info("الصندوق", "ملخص المقبوضات والمدفوعات.") }
        card("الديون والحسابات", "♙") { info("الحسابات", "قوائم العملاء والموردين وأرصدتهم.") }
        card("قبض من حساب / دفع لحساب", "⇄") { info("القبض والدفع", "تسجيل الحركات وربطها برصيد الحساب.") }
        card("تسجيل المصاريف", "−") { info("المصاريف", "تسجيل المصروفات وتصنيفها.") }
        card("الإيرادات الأخرى", "+") { info("الإيرادات", "تسجيل الإيرادات غير الناتجة عن المبيعات.") }
    }

    private fun showReports() {
        content.removeAllViews(); section("التقارير")
        card("ملخص الحركة اليومية", "▤") { info("الحركة اليومية", "ملخص المبيعات والمشتريات وحركة الصندوق.") }
        card("الأرباح والخسائر", "⌁") { info("الأرباح والخسائر", "يعتمد التقرير على كلفة المادة المحفوظة وقت البيع.") }
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
        val productPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                products.map { "${it.name} — المتوفر: ${it.quantity} ${it.baseUnit}" })
        }
        fun numberInput(hintText: String) = EditText(this).apply {
            hint = hintText
            textSize = 16f
            inputType = 8194
        }
        val quantityInput = numberInput("الكمية المباعة")
        val priceInput = numberInput("سعر بيع الوحدة")
        val currencyLabel = label("العملة: ${products.first().currency}", 14f, ink)
        productPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currencyLabel.text = "العملة: ${products[position].currency}"
                priceInput.setText(products[position].salePrice.toString())
            }
        }
        box.addView(label("اختر المادة", 14f))
        box.addView(productPicker)
        box.addView(quantityInput)
        box.addView(priceInput)
        box.addView(currencyLabel)
        val dialog = AlertDialog.Builder(this).setTitle("فاتورة مبيع").setView(box)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ الفاتورة", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val product = products.getOrNull(productPicker.selectedItemPosition) ?: return@setOnClickListener
                val qty = quantityInput.text.toString().toDoubleOrNull()
                val price = priceInput.text.toString().toDoubleOrNull()
                if (qty == null || qty <= 0.0) { quantityInput.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                if (price == null || price < 0.0) { priceInput.error = "أدخل سعر بيع صحيح"; return@setOnClickListener }
                if (qty > product.quantity) { quantityInput.error = "الكمية أكبر من المتوفر في المخزون"; return@setOnClickListener }
                val total = qty * price
                lifecycleScope.launch {
                    try {
                        val sale = Sale(
                            currency = product.currency,
                            exchangeRateAtSale = dao.getExchangeRate()?.usdToSyp ?: 1.0,
                            subtotal = total,
                            total = total,
                            paymentType = "CASH"
                        )
                        val item = SaleItem(
                            saleId = 0, productId = product.id,
                            productNameSnapshot = product.name,
                            quantityInSelectedUnit = qty,
                            selectedUnit = product.baseUnit,
                            baseUnitsPerSelectedUnit = 1.0,
                            quantityInBaseUnit = qty,
                            unitPrice = price,
                            unitCostSnapshot = product.costPrice,
                            lineTotal = total
                        )
                        dao.saveSaleAndDeductStock(sale, listOf(item))
                        dialog.dismiss()
                        info("تم حفظ الفاتورة", "إجمالي الفاتورة: $total ${product.currency}\nتم تحديث المخزون وحفظ كلفة المادة وقت البيع.")
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
        val picker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                products.map { "${it.name} — الرصيد الحالي: ${it.quantity} ${it.baseUnit}" })
        }
        fun numeric(h: String) = EditText(this).apply { hint = h; textSize = 16f; inputType = 8194 }
        val quantity = numeric("الكمية المشتراة")
        val cost = numeric("كلفة شراء الوحدة")
        val salePrice = numeric("سعر البيع الجديد (أدخله يدوياً)")
        val currencyLabel = label("العملة: ${products.first().currency}", 14f, ink)
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val product = products[position]
                currencyLabel.text = "العملة: ${product.currency}"
                cost.setText(product.costPrice.toString())
                salePrice.setText(product.salePrice.toString())
            }
        }
        box.addView(label("اختر المادة", 14f))
        box.addView(picker); box.addView(quantity); box.addView(cost); box.addView(salePrice); box.addView(currencyLabel)
        val dialog = AlertDialog.Builder(this).setTitle("فاتورة شراء").setView(box)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ فاتورة الشراء", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val product = products.getOrNull(picker.selectedItemPosition) ?: return@setOnClickListener
                val qty = quantity.text.toString().toDoubleOrNull()
                val unitCost = cost.text.toString().toDoubleOrNull()
                val newSalePrice = salePrice.text.toString().toDoubleOrNull()
                if (qty == null || qty <= 0.0) { quantity.error = "أدخل كمية صحيحة"; return@setOnClickListener }
                if (unitCost == null || unitCost < 0.0) { cost.error = "أدخل كلفة صحيحة"; return@setOnClickListener }
                if (newSalePrice == null || newSalePrice < 0.0) { salePrice.error = "أدخل سعر بيع صحيح"; return@setOnClickListener }
                val total = qty * unitCost
                lifecycleScope.launch {
                    try {
                        val purchase = Purchase(currency = product.currency, subtotal = total, total = total)
                        val item = PurchaseItem(purchaseId = 0, productId = product.id,
                            productNameSnapshot = product.name, quantity = qty, unitCost = unitCost,
                            salePriceSet = newSalePrice, lineTotal = total)
                        dao.savePurchaseAndAddStock(purchase, item)
                        dialog.dismiss()
                        info("تم حفظ فاتورة الشراء", "إجمالي الشراء: $total ${product.currency}\nتمت إضافة الكمية للمخزون وتحديث كلفة الوحدة وسعر البيع الذي أدخلته.")
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