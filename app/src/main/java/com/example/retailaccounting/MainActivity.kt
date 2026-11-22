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
import com.example.retailaccounting.data.Customer
import com.example.retailaccounting.data.Supplier
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
    private val BACKUP_CREATE_REQUEST = 7101
    private val BACKUP_RESTORE_REQUEST = 7102

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
            setBackgroundColor(0xFFF4F6F7.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 16)
            setBackgroundColor(-1)
        }
        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = label("Good Girl", 26f, blue, true)
        brandRow.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        brandRow.addView(label("●", 12f, 0xFF35A66F.toInt(), true))
        header.addView(brandRow)
        header.addView(label("نظام إدارة المبيعات والمخزون", 13f, 0xFF7B858A.toInt()))
        root.addView(header)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 0, 8, 0)
            setBackgroundColor(-1)
        }
        listOf("الفواتير", "الحسابات", "التقارير", "الإعدادات").forEach { name ->
            val selected = name == activeTab
            val tab = TextView(this).apply {
                text = name
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (selected) blue else 0xFF7C858A.toInt())
                setPadding(4, 13, 4, 13)
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (selected) 0xFFE7F1F5.toInt() else android.graphics.Color.TRANSPARENT)
                    cornerRadius = 18f
                }
                setOnClickListener { activeTab = name; buildShell() }
            }
            val params = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(3, 7, 3, 7) }
            tabs.addView(tab, params)
        }
        root.addView(tabs)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 16, 15, 20)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "▣     الصندوق الرئيسي"
            textSize = 16f
            setTextColor(-1)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(12, 16, 12, 16)
            setBackgroundColor(blue)
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
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(4, 8, 4, 15)
        }
        val accent = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF438CA5.toInt())
                cornerRadius = 5f
            }
        }
        heading.addView(accent, LinearLayout.LayoutParams(5, 27))
        heading.addView(label(title, 19f, ink, true).apply { setPadding(10, 0, 0, 0) })
        content.addView(heading)
    }

    private fun card(title: String, icon: String = "◆", action: () -> Unit) {
        val background = android.graphics.drawable.GradientDrawable().apply {
            setColor(-1)
            cornerRadius = 22f
            setStroke(1, 0xFFE5EAEC.toInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(17, 14, 17, 14)
            this.background = background
            elevation = 2.5f
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val iconBox = TextView(this).apply {
            text = icon
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(blue)
            this.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFEAF3F6.toInt())
                cornerRadius = 16f
            }
        }
        row.addView(iconBox, LinearLayout.LayoutParams(52, 52))
        row.addView(label(title, 16f, blue, true).apply { setPadding(13, 0, 8, 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("‹", 25f, 0xFF9AA7AC.toInt(), true))
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = 11
            marginStart = 2
            marginEnd = 2
        })
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
        card("مرتجعات المبيعات", "↩") { createSalesReturn() }\n        card("مرتجعات المشتريات", "↪") { createPurchaseReturn() }
        card("عروض الأسعار وطلبات الشراء", "◆") { info("العروض والطلبات", "تظهر ضمن القوائم الفرعية.") }
    }


    private fun showInvoiceHistory() {
        lifecycleScope.launch {
            try {
                val sales = dao.observeSales().first()
                val purchases = dao.observePurchases().first()
                val entries = (sales.map { sale ->
                    Triple(sale.createdAt, "مبيع #${sale.id}", "إجمالي: ${sale.total} ${sale.currency} — ${if (sale.paymentType == "CREDIT") "آجل: ${sale.customerName ?: "عميل"}" else "نقدي"}")
                } + purchases.map { purchase ->
                    Triple(purchase.createdAt, "شراء #${purchase.id}", "إجمالي: ${purchase.total} ${purchase.currency} — ${if (purchase.paymentType == "CREDIT") "آجل: ${purchase.supplierName}" else "نقدي"}")
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
        card("إدخال بضاعة أو إخراج وتسوية", "▦") { createInventoryAdjustment() }
        card("سجل حركة المخزون", "↕") { showInventoryMovementHistory() }
        card("جرد المستودع", "▣") { showStockCount() }
        card("المواد منخفضة الكمية", "↓") {
            val low = products.filter { it.lowStockLimit > 0 && it.quantity <= it.lowStockLimit }
            info("تنبيه المخزون", if (low.isEmpty()) "لا توجد مواد مسجلة تحت حد التنبيه." else low.joinToString("\n") { "${it.name}: ${it.quantity}" })
        }
    }


    private fun createInventoryAdjustment() {
        if (products.isEmpty()) {
            info("حركة المخزون", "أضف مادة أولاً حتى تسجل حركة مخزون.")
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 8, 22, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val productPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                products.map { "${it.name} — المتوفر: ${it.quantity} ${it.baseUnit}" })
        }
        val directionPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("إدخال إلى المستودع", "إخراج من المستودع"))
        }
        val quantity = EditText(this).apply { hint = "الكمية"; inputType = 8194 }
        val note = EditText(this).apply { hint = "البيان أو سبب التسوية" }
        box.addView(label("المادة", 14f, ink, true)); box.addView(productPicker)
        box.addView(label("نوع الحركة", 14f, ink, true)); box.addView(directionPicker)
        box.addView(quantity); box.addView(note)
        val dialog = AlertDialog.Builder(this).setTitle("حركة مخزون جديدة").setView(box)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ الحركة", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val product = products.getOrNull(productPicker.selectedItemPosition) ?: return@setOnClickListener
                val qty = quantity.text.toString().toDoubleOrNull()
                if (qty == null || qty <= 0.0) {
                    quantity.error = "أدخل كمية أكبر من صفر"
                    return@setOnClickListener
                }
                val delta = if (directionPicker.selectedItemPosition == 0) qty else -qty
                lifecycleScope.launch {
                    try {
                        dao.saveInventoryAdjustment(product, delta, note.text.toString().trim().ifBlank {
                            if (delta > 0) "إدخال مخزون يدوي" else "إخراج مخزون يدوي"
                        })
                        dialog.dismiss()
                        info("تم تحديث المخزون", "تم تسجيل الحركة وتحديث رصيد المادة.")
                    } catch (e: Exception) {
                        info("تعذر تسجيل الحركة", e.message ?: "تحقق من الكمية المتوفرة.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showInventoryMovementHistory() {
        lifecycleScope.launch {
            try {
                val movements = dao.getInventoryMovements()
                if (movements.isEmpty()) {
                    info("سجل حركة المخزون", "لا توجد حركات مسجلة حتى الآن.")
                    return@launch
                }
                val lines = movements.joinToString("\\n\\n") { movement ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(movement.createdAt))
                    val direction = if (movement.direction == "IN") "إدخال" else "إخراج"
                    "$direction — ${movement.productName}: ${movement.quantity}\\n${movement.note}\\n$date"
                }
                info("سجل حركة المخزون", lines)
            } catch (e: Exception) {
                info("سجل حركة المخزون", e.message ?: "تعذر تحميل سجل الحركات.")
            }
        }
    }

    private fun showStockCount() {
        if (products.isEmpty()) {
            info("جرد المستودع", "لا توجد مواد مسجلة.")
            return
        }
        val lines = products.joinToString("\\n\\n") { product ->
            "${product.name}\\nالرصيد: ${product.quantity} ${product.baseUnit}\\nقيمة التكلفة التقديرية: ${product.quantity * product.costPrice} ${product.currency}"
        }
        info("جرد المستودع — ${products.size} مادة", lines)
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
                val summary = currencies.joinToString("\n") { currency ->
                    val incoming = movements.filter { it.currency == currency && it.direction == "IN" }.sumOf { it.amount }
                    val outgoing = movements.filter { it.currency == currency && it.direction == "OUT" }.sumOf { it.amount }
                    "$currency — المقبوضات: $incoming | المدفوعات: $outgoing | الرصيد: ${incoming - outgoing}"
                }
                val latest = movements.take(30).joinToString("\n\n") { movement ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(movement.createdAt))
                    val direction = if (movement.direction == "IN") "قبض" else "دفع"
                    "$direction: ${movement.amount} ${movement.currency}\n${movement.reason}\n$date"
                }
                info("الصندوق الرئيسي", "$summary\n\nآخر الحركات:\n$latest")
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
                        info("تم تسجيل المصروف", "$title — $amount ${expense.currency}\nتم خصم المبلغ من حركة الصندوق.")
                    } catch (e: Exception) {
                        info("تعذر تسجيل المصروف", e.message ?: "حدث خطأ غير متوقع.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showAccounts() {
        content.removeAllViews(); section("الحسابات والذمم المالية")
        card("العملاء وأرصدتهم", "♙") { showPartyList(true) }
        card("الموردون وأرصدتهم", "♧") { showPartyList(false) }
        card("حركة الصندوق", "▣") { showCashbox() }
        card("حركة قبض أو دفع نقدية", "⇄") { createOtherCashMovement() }
        card("تسجيل المصاريف", "−") { createExpense() }
        card("الإيرادات الأخرى", "+") { createOtherCashMovement(defaultDirection = "IN") }
    }

    private fun showPartyList(customers: Boolean) {
        lifecycleScope.launch {
            try {
                val entries: List<Triple<Long, String, String>> = if (customers) {
                    dao.observeCustomers().first().map { Triple(it.id, it.name, it.currency) }
                } else {
                    dao.observeSuppliers().first().map { Triple(it.id, it.name, it.currency) }
                }
                val balances: Map<Long, Double> = if (customers) {
                    dao.observeCustomers().first().associate { it.id to it.balance }
                } else {
                    dao.observeSuppliers().first().associate { it.id to it.balance }
                }
                if (entries.isEmpty()) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(if (customers) "قائمة العملاء" else "قائمة الموردين")
                        .setMessage(if (customers) "لا يوجد عملاء مسجلون." else "لا يوجد موردون مسجلون.")
                        .setPositiveButton(if (customers) "إضافة عميل" else "إضافة مورد") { _, _ -> addParty(customers) }
                        .setNegativeButton("إغلاق", null).show()
                    return@launch
                }
                val names = entries.map { entry ->
                    "${entry.second} — الرصيد: ${balances[entry.first] ?: 0.0} ${entry.third}"
                }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(if (customers) "قائمة العملاء" else "قائمة الموردين")
                    .setItems(names) { _, which ->
                        val selected = entries[which]
                        showPartyActions(customers, selected.first, selected.second, selected.third)
                    }
                    .setPositiveButton(if (customers) "إضافة عميل" else "إضافة مورد") { _, _ -> addParty(customers) }
                    .setNegativeButton("إغلاق", null).show()
            } catch (e: Exception) {
                info("الحسابات", e.message ?: "تعذر تحميل القائمة.")
            }
        }
    }

    private fun showPartyActions(customer: Boolean, partyId: Long, partyName: String, currencyCode: String) {
        val actions = arrayOf("تسجيل دين جديد", "تسجيل دفعة / تسديد", "كشف الحساب")
        AlertDialog.Builder(this).setTitle(partyName).setItems(actions) { _, which ->
            when (which) {
                0 -> createPartyTransaction(customer, partyId, currencyCode, "DEBT")
                1 -> createPartyTransaction(customer, partyId, currencyCode, "PAYMENT")
                2 -> showPartyStatement(customer, partyId, partyName)
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun createPartyTransaction(customer: Boolean, partyId: Long, currencyCode: String, action: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 8, 22, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val amount = EditText(this).apply {
            hint = "المبلغ"
            inputType = 8194
        }
        val note = EditText(this).apply {
            hint = if (action == "DEBT") "بيان الدين أو الفاتورة" else "بيان الدفعة"
        }
        box.addView(amount); box.addView(note)
        AlertDialog.Builder(this)
            .setTitle(if (action == "DEBT") "تسجيل دين على الحساب" else "تسجيل دفعة / تسديد")
            .setView(box)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = amount.text.toString().toDoubleOrNull()
                        if (value == null || value <= 0.0) {
                            amount.error = "أدخل مبلغاً أكبر من صفر"
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            try {
                                dao.savePartyTransaction(com.example.retailaccounting.data.PartyTransaction(
                                    partyType = if (customer) "CUSTOMER" else "SUPPLIER",
                                    partyId = partyId,
                                    action = action,
                                    amount = value,
                                    currency = currencyCode,
                                    note = note.text.toString().trim()
                                ))
                                dialog.dismiss()
                                info("تم الحفظ", if (action == "DEBT") "تم تسجيل الدين وتحديث الرصيد." else "تم تسجيل الدفعة وتحديث الرصيد وحركة الصندوق.")
                            } catch (e: Exception) {
                                info("تعذر الحفظ", e.message ?: "حدث خطأ أثناء حفظ الحركة.")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showPartyStatement(customer: Boolean, partyId: Long, partyName: String) {
        lifecycleScope.launch {
            try {
                val transactions = dao.getPartyTransactions(if (customer) "CUSTOMER" else "SUPPLIER", partyId)
                val lines = transactions.joinToString("\\n") { item ->
                    val actionText = when (item.action) {
                        "DEBT" -> "دين"
                        "PAYMENT" -> "دفعة"
                        "OPENING" -> "رصيد افتتاحي"
                        else -> item.action
                    }
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(item.createdAt))
                    "$date — $actionText: ${item.amount} ${item.currency} — ${item.note}"
                }
                val balance = if (customer) dao.observeCustomers().first().firstOrNull { it.id == partyId }?.balance
                    else dao.observeSuppliers().first().firstOrNull { it.id == partyId }?.balance
                info("كشف حساب: $partyName", (if (lines.isBlank()) "لا توجد حركات مسجلة." else lines) +
                    "\\n\\nالرصيد الحالي: ${balance ?: 0.0}")
            } catch (e: Exception) {
                info("كشف الحساب", e.message ?: "تعذر تحميل كشف الحساب.")
            }
        }
    }

    private fun addParty(customer: Boolean) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 8, 22, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val name = EditText(this).apply { hint = if (customer) "اسم العميل" else "اسم المورد" }
        val phone = EditText(this).apply { hint = "رقم الهاتف (اختياري)"; inputType = 3 }
        val balance = EditText(this).apply { hint = "الرصيد الافتتاحي (صفر إذا لا يوجد)"; inputType = 8194 }
        val currency = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP"))
        }
        box.addView(name); box.addView(phone); box.addView(balance); box.addView(currency)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (customer) "إضافة عميل" else "إضافة مورد")
            .setView(box).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                val opening = balance.text.toString().toDoubleOrNull() ?: 0.0
                if (n.isBlank()) { name.error = "الاسم مطلوب"; return@setOnClickListener }
                if (opening < 0.0) { balance.error = "أدخل رصيداً غير سالب"; return@setOnClickListener }
                lifecycleScope.launch {
                    try {
                        val currencyCode = currency.selectedItem.toString()
                        val id = if (customer) dao.insertCustomer(com.example.retailaccounting.data.Customer(
                            name = n, phone = phone.text.toString().trim(), balance = opening, currency = currencyCode
                        )) else dao.insertSupplier(com.example.retailaccounting.data.Supplier(
                            name = n, phone = phone.text.toString().trim(), balance = opening, currency = currencyCode
                        ))
                        if (opening > 0.0) {
                            dao.insertPartyTransaction(com.example.retailaccounting.data.PartyTransaction(
                                partyType = if (customer) "CUSTOMER" else "SUPPLIER",
                                partyId = id, action = "OPENING", amount = opening,
                                currency = currencyCode, note = "الرصيد الافتتاحي"
                            ))
                        }
                        dialog.dismiss()
                        info("تم الحفظ", "تمت إضافة ${if (customer) "العميل" else "المورد"}: $n")
                    } catch (e: Exception) {
                        info("تعذر الحفظ", e.message ?: "حدث خطأ.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun createOtherCashMovement(defaultDirection: String? = null) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val reason = EditText(this).apply { hint = "بيان الحركة" }
        val amount = EditText(this).apply { hint = "المبلغ"; inputType = 8194 }
        val direction = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("قبض", "دفع"))
            setSelection(if (defaultDirection == "IN") 0 else 1)
            isEnabled = defaultDirection == null
        }
        val currency = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("USD", "SYP"))
        }
        box.addView(label("نوع الحركة", 14f, ink, true))
        box.addView(direction); box.addView(reason); box.addView(amount); box.addView(currency)
        val dialog = AlertDialog.Builder(this).setTitle("حركة نقدية أخرى")
            .setView(box).setNegativeButton("إلغاء", null).setPositiveButton("حفظ الحركة", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = reason.text.toString().trim()
                val value = amount.text.toString().toDoubleOrNull()
                if (title.isBlank()) { reason.error = "أدخل بيان الحركة"; return@setOnClickListener }
                if (value == null || value <= 0.0) { amount.error = "أدخل مبلغاً صحيحاً"; return@setOnClickListener }
                lifecycleScope.launch {
                    try {
                        val incoming = direction.selectedItemPosition == 0
                        dao.insertCashMovement(com.example.retailaccounting.data.CashMovement(
                            direction = if (incoming) "IN" else "OUT",
                            amount = value,
                            currency = currency.selectedItem.toString(),
                            reason = title
                        ))
                        dialog.dismiss()
                        info("تم حفظ الحركة", "${if (incoming) "قبض" else "دفع"}: $title — $value ${currency.selectedItem}")
                    } catch (e: Exception) {
                        info("تعذر حفظ الحركة", e.message ?: "حدث خطأ غير متوقع.")
                    }
                }
            }
        }
        dialog.show()
    }


    private fun showProfitReport() {
        lifecycleScope.launch {
            try {
                val summaries = dao.getProfitSummary()
                if (summaries.isEmpty()) {
                    info("الأرباح والخسائر", "لا توجد مبيعات مسجلة بعد.")
                    return@launch
                }
                val report = summaries.joinToString("\n\n") { item ->
                    val profit = item.revenue - item.cost
                    "العملة: ${item.currency}\nإجمالي المبيعات: ${item.revenue}\nكلفة البضاعة المباعة: ${item.cost}\nمجمل الربح: $profit\nالمصاريف المسجلة: ${item.expenses}\nصافي الربح التقديري: ${profit - item.expenses}"
                }
                info("الأرباح والخسائر حسب العملة", "$report\n\nملاحظة: صافي الربح تقديري بعد خصم المصاريف المسجلة فقط، ولا يشمل أي مصاريف أو التزامات غير مسجلة.")
            } catch (e: Exception) {
                info("الأرباح والخسائر", e.message ?: "تعذر حساب الأرباح.")
            }
        }
    }

    private fun showReports() {
        content.removeAllViews(); section("التقارير")
        card("ملخص الحركة اليومية", "▤") { showDailySummary() }
        card("الأرباح والخسائر", "⌁") { showProfitReport() }
        card("رأس المال والموجودات", "◉") { showCapitalSummary() }
        card("المواد الأكثر والأقل حركة", "▥") { showProductMovementReport() }
    }

    private fun showDailySummary() {
        lifecycleScope.launch {
            try {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                val sales = dao.observeSales().first().filter { it.createdAt >= start }
                val purchases = dao.observePurchases().first().filter { it.createdAt >= start }
                val movements = dao.observeCashMovements().first().filter { it.createdAt >= start }
                val currencies = (sales.map { it.currency } + purchases.map { it.currency } +
                    movements.map { it.currency }).distinct().sorted()
                if (currencies.isEmpty()) {
                    info("ملخص الحركة اليومية", "لا توجد حركة مسجلة اليوم.")
                    return@launch
                }
                val summary = currencies.joinToString("\\n\\n") { currency ->
                    val sold = sales.filter { it.currency == currency }.sumOf { it.total }
                    val bought = purchases.filter { it.currency == currency }.sumOf { it.total }
                    val incoming = movements.filter { it.currency == currency && it.direction == "IN" }.sumOf { it.amount }
                    val outgoing = movements.filter { it.currency == currency && it.direction == "OUT" }.sumOf { it.amount }
                    "$currency\\nالمبيعات: $sold\\nالمشتريات: $bought\\nالمقبوضات: $incoming\\nالمدفوعات: $outgoing\\nصافي حركة الصندوق: ${incoming - outgoing}"
                }
                info("ملخص الحركة اليومية", summary)
            } catch (e: Exception) {
                info("ملخص الحركة اليومية", e.message ?: "تعذر إعداد الملخص.")
            }
        }
    }

    private fun showCapitalSummary() {
        lifecycleScope.launch {
            try {
                val movements = dao.observeCashMovements().first()
                val customers = dao.observeCustomers().first()
                val suppliers = dao.observeSuppliers().first()
                val currencies = (products.map { it.currency } + movements.map { it.currency } +
                    customers.map { it.currency } + suppliers.map { it.currency }).distinct().sorted()
                val summary = currencies.joinToString("\\n\\n") { currency ->
                    val stock = products.filter { it.currency == currency }.sumOf { it.quantity * it.costPrice }
                    val incoming = movements.filter { it.currency == currency && it.direction == "IN" }.sumOf { it.amount }
                    val outgoing = movements.filter { it.currency == currency && it.direction == "OUT" }.sumOf { it.amount }
                    val receivables = customers.filter { it.currency == currency }.sumOf { it.balance }
                    val payables = suppliers.filter { it.currency == currency }.sumOf { it.balance }
                    "$currency\\nقيمة المخزون بالتكلفة: $stock\\nرصيد الصندوق: ${incoming - outgoing}\\nأرصدة العملاء: $receivables\\nأرصدة الموردين: $payables"
                }
                info("رأس المال والموجودات — تقديري", if (summary.isBlank()) "لا توجد بيانات كافية." else summary)
            } catch (e: Exception) {
                info("رأس المال والموجودات", e.message ?: "تعذر حساب الموجودات.")
            }
        }
    }

    private fun showProductMovementReport() {
        lifecycleScope.launch {
            try {
                val sales = dao.observeSales().first().filter {
                    it.createdAt >= System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                }
                val totals = mutableMapOf<String, Double>()
                sales.forEach { sale ->
                    dao.getSaleItems(sale.id).forEach { item ->
                        totals[item.productNameSnapshot] = (totals[item.productNameSnapshot] ?: 0.0) + item.quantityInBaseUnit
                    }
                }
                if (totals.isEmpty()) {
                    info("حركة المواد", "لا توجد مبيعات مسجلة خلال آخر 30 يوماً.")
                    return@launch
                }
                val sorted = totals.entries.sortedByDescending { it.value }
                val top = sorted.take(5).joinToString("\\n") { "${it.key}: ${it.value}" }
                val low = sorted.takeLast(5).asReversed().joinToString("\\n") { "${it.key}: ${it.value}" }
                info("حركة المواد — آخر 30 يوماً", "الأكثر حركة:\\n$top\\n\\nالأقل حركة:\\n$low")
            } catch (e: Exception) {
                info("حركة المواد", e.message ?: "تعذر إعداد التقرير.")
            }
        }
    }

    private fun showSettings() {
        content.removeAllViews(); section("الإعدادات")
        card("العملة وسعر الصرف", "$") { editExchangeRate() }
        card("إنشاء نسخة احتياطية", "☁") { createBackupFile() }
        card("استعادة نسخة احتياطية", "↻") { chooseBackupFile() }
        card("حول التطبيق", "ⓘ") { info("Good Girl", "تطبيق محاسبة وإدارة مخزون للمحل.") }
    }




    private fun createBackupFile() {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(android.content.Intent.EXTRA_TITLE, "GoodGirl-Backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())}.db")
        }
        startActivityForResult(intent, BACKUP_CREATE_REQUEST)
    }

    private fun chooseBackupFile() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, BACKUP_RESTORE_REQUEST)
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == BACKUP_CREATE_REQUEST) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val database = AppDatabase.getInstance(this@MainActivity)
                    database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
                    val source = getDatabasePath("retail_accounting.db")
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("تعذر فتح ملف النسخة الاحتياطية")
                    runOnUiThread { info("اكتملت النسخة الاحتياطية", "تم تصدير قاعدة البيانات إلى الموقع الذي اخترته.") }
                } catch (e: Exception) {
                    runOnUiThread { info("فشل النسخ الاحتياطي", e.message ?: "تعذر إنشاء النسخة.") }
                }
            }
        } else if (requestCode == BACKUP_RESTORE_REQUEST) {
            AlertDialog.Builder(this).setTitle("تأكيد الاستعادة")
                .setMessage("سيتم استبدال بيانات التطبيق الحالية بمحتويات النسخة المختارة. يُفضّل إنشاء نسخة احتياطية حالية أولاً.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("استعادة") { _, _ -> restoreBackup(uri) }.show()
        }
    }

    private fun restoreBackup(uri: android.net.Uri) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val temp = java.io.File(cacheDir, "restore-check.db")
            try {
                contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                    ?: error("تعذر قراءة ملف النسخة")
                require(temp.length() > 0L) { "ملف النسخة فارغ" }
                val checkDb = android.database.sqlite.SQLiteDatabase.openDatabase(temp.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                try {
                    checkDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                        require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "قاعدة البيانات داخل الملف غير سليمة" }
                    }
                    checkDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='products'", null).use { cursor ->
                        require(cursor.moveToFirst()) { "الملف لا يبدو نسخة من تطبيق Good Girl" }
                    }
                } finally { checkDb.close() }
                AppDatabase.closeInstance()
                val target = getDatabasePath("retail_accounting.db")
                temp.copyTo(target, overwrite = true)
                val reopened = AppDatabase.getInstance(this@MainActivity)
                reopened.openHelper.writableDatabase
                dao = reopened.storeDao()
                runOnUiThread {
                    buildShell()
                    lifecycleScope.launch {
                        dao.observeProducts().collectLatest {
                            products = it
                            if (activeTab == "المخزون") showInventory()
                        }
                    }
                    info("تمت الاستعادة", "تم التحقق من سلامة قاعدة البيانات واستعادة النسخة بنجاح.")
                }
            } catch (e: Exception) {
                runOnUiThread { info("تعذرت الاستعادة", e.message ?: "ملف النسخة غير صالح.") }
            } finally { temp.delete() }
        }
    }

    private fun editExchangeRate() {
        lifecycleScope.launch {
            val current = dao.getExchangeRate()?.usdToSyp
            val input = EditText(this@MainActivity).apply {
                hint = "عدد الليرات السورية مقابل 1 دولار"
                inputType = 8194
                if (current != null) setText(current.toString())
            }
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 8, 24, 0)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(label("سعر صرف 1 USD مقابل SYP", 14f, ink, true))
                addView(input)
            }
            val dialog = AlertDialog.Builder(this@MainActivity)
                .setTitle("إعداد سعر الصرف")
                .setView(box).setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val rate = input.text.toString().toDoubleOrNull()
                    if (rate == null || rate <= 0.0) {
                        input.error = "أدخل سعر صرف أكبر من صفر"
                        return@setOnClickListener
                    }
                    lifecycleScope.launch {
                        try {
                            dao.saveExchangeRate(com.example.retailaccounting.data.ExchangeRate(usdToSyp = rate))
                            dialog.dismiss()
                            info("تم الحفظ", "تم تحديث سعر الصرف. سيُحفظ السعر المستخدم مع فواتير البيع الجديدة.")
                        } catch (e: Exception) {
                            info("تعذر الحفظ", e.message ?: "حدث خطأ أثناء حفظ سعر الصرف.")
                        }
                    }
                }
            }
            dialog.show()
        }
    }


    private fun createPurchaseReturn() {
        lifecycleScope.launch {
            try {
                val purchases = dao.observePurchases().first()
                if (purchases.isEmpty()) { info("مرتجع شراء", "لا توجد فواتير شراء سابقة."); return@launch }
                val labels = purchases.map { "فاتورة #${it.id} — ${it.total} ${it.currency} — ${it.supplierName}" }
                val picker = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels) }
                AlertDialog.Builder(this@MainActivity).setTitle("اختيار فاتورة الشراء").setView(picker)
                    .setNegativeButton("إلغاء", null).setPositiveButton("متابعة") { _, _ ->
                        lifecycleScope.launch {
                            val purchase = purchases[picker.selectedItemPosition]
                            val items = dao.getPurchaseItems(purchase.id)
                            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
                            val inputs = items.map { item ->
                                val already = dao.getReturnedPurchaseQuantity(item.id)
                                box.addView(label("${item.productNameSnapshot} — المشتَرى: ${item.quantity} — المرتجع سابقاً: $already", 14f, ink, true))
                                EditText(this@MainActivity).apply { hint = "الكمية المرتجعة"; inputType = 8194 }.also { box.addView(it) }
                            }
                            val reason = EditText(this@MainActivity).apply { hint = "سبب المرتجع (اختياري)" }; box.addView(reason)
                            val dialog = AlertDialog.Builder(this@MainActivity).setTitle("تفاصيل مرتجع الشراء").setView(ScrollView(this@MainActivity).apply { addView(box) }).setNegativeButton("إلغاء", null).setPositiveButton("حفظ المرتجع", null).create()
                            dialog.setOnShowListener {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                    val chosen = mutableListOf<Pair<PurchaseItem, Double>>()
                                    items.forEachIndexed { i, item ->
                                        val raw = inputs[i].text.toString().trim()
                                        if (raw.isNotEmpty()) {
                                            val qty = raw.toDoubleOrNull()
                                            if (qty == null || qty <= 0.0) { inputs[i].error = "أدخل كمية صحيحة"; return@setOnClickListener }
                                            chosen.add(item to qty)
                                        }
                                    }
                                    if (chosen.isEmpty()) { info("مرتجع شراء", "أدخل كمية مرتجعة لمادة واحدة على الأقل."); return@setOnClickListener }
                                    lifecycleScope.launch {
                                        try {
                                            val id = dao.savePurchaseReturn(purchase.id, chosen, reason.text.toString().trim())
                                            dialog.dismiss()
                                            info("تم حفظ المرتجع", "رقم المرتجع: $id\\nتم تخفيض المخزون وتحديث الصندوق أو حساب المورد.")
                                        } catch (e: Exception) { info("تعذر حفظ المرتجع", e.message ?: "تحقق من الكميات والمخزون المتاح.") }
                                    }
                                }
                            }
                            dialog.show()
                        }
                    }.show()
            } catch (e: Exception) { info("مرتجع شراء", e.message ?: "تعذر تحميل فواتير الشراء.") }
        }
    }

    private fun createSalesReturn() {
        lifecycleScope.launch {
            try {
                val sales = dao.observeSales().first()
                if (sales.isEmpty()) { info("مرتجع مبيع", "لا توجد فواتير مبيعات سابقة."); return@launch }
                val labels = sales.map { "فاتورة #${it.id} — ${it.total} ${it.currency} — ${java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US).format(java.util.Date(it.createdAt))}" }
                val picker = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels) }
                AlertDialog.Builder(this@MainActivity).setTitle("اختيار فاتورة المبيع").setView(picker).setNegativeButton("إلغاء", null)
                    .setPositiveButton("متابعة") { _, _ -> lifecycleScope.launch {
                        val sale = sales[picker.selectedItemPosition]
                        val items = dao.getSaleItems(sale.id)
                        val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
                        val inputs = items.map { item ->
                            box.addView(label("${item.productNameSnapshot} — الكمية الأصلية: ${item.quantityInBaseUnit} — المرتجع سابقاً: ${dao.getReturnedQuantity(item.id)}", 14f, ink, true))
                            EditText(this@MainActivity).apply { hint = "الكمية المرتجعة"; inputType = 8194 }.also { box.addView(it) }
                        }
                        val reason = EditText(this@MainActivity).apply { hint = "سبب المرتجع (اختياري)" }; box.addView(reason)
                        val dialog = AlertDialog.Builder(this@MainActivity).setTitle("تفاصيل المرتجع").setView(ScrollView(this@MainActivity).apply { addView(box) }).setNegativeButton("إلغاء", null).setPositiveButton("حفظ المرتجع", null).create()
                        dialog.setOnShowListener {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                val chosen = mutableListOf<Pair<SaleItem, Double>>()
                                items.forEachIndexed { i, item ->
                                    val raw = inputs[i].text.toString().trim()
                                    if (raw.isNotEmpty()) {
                                        val qty = raw.toDoubleOrNull()
                                        if (qty == null || qty <= 0.0) { inputs[i].error = "أدخل كمية صحيحة"; return@setOnClickListener }
                                        chosen.add(item to qty)
                                    }
                                }
                                if (chosen.isEmpty()) { info("مرتجع مبيع", "أدخل كمية مرتجعة لمادة واحدة على الأقل."); return@setOnClickListener }
                                lifecycleScope.launch {
                                    try {
                                        val id = dao.saveSalesReturn(sale.id, chosen, reason.text.toString().trim())
                                        dialog.dismiss()
                                        info("تم حفظ المرتجع", "رقم المرتجع: $id\nتم تحديث المخزون والحسابات وفق طريقة دفع الفاتورة.")
                                    } catch (e: Exception) { info("تعذر حفظ المرتجع", e.message ?: "تحقق من الكميات المدخلة.") }
                                }
                            }
                        }
                        dialog.show()
                    } }.show()
            } catch (e: Exception) { info("مرتجع مبيع", e.message ?: "تعذر تحميل الفواتير.") }
        }
    }

    private fun createSale() {
        lifecycleScope.launch { createSaleWithCustomers(dao.observeCustomers().first()) }
    }

    private fun createSaleWithCustomers(customers: List<Customer>) {
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
        val paymentPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدي", "آجل على حساب عميل"))
        }
        val customerPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                customers.map { "${it.name} — ${it.balance} ${it.currency}" }.ifEmpty { listOf("لا يوجد عملاء — أضف عميلاً أولاً") })
        }
        box.addView(label("طريقة الدفع", 14f, ink, true)); box.addView(paymentPicker)
        box.addView(label("العميل (للفواتير الآجلة)", 14f, ink, true)); box.addView(customerPicker)
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
                val creditSale = paymentPicker.selectedItemPosition == 1
                val selectedCustomer = customers.getOrNull(customerPicker.selectedItemPosition)
                if (creditSale && selectedCustomer == null) {
                    info("العميل مطلوب", "أضف عميلاً وحدده قبل حفظ فاتورة آجلة.")
                    return@setOnClickListener
                }
                if (creditSale && selectedCustomer?.currency != currency) {
                    info("اختلاف العملة", "عملة حساب العميل يجب أن تطابق عملة الفاتورة.")
                    return@setOnClickListener
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
                            subtotal = total, total = total,
                            paymentType = if (creditSale) "CREDIT" else "CASH",
                            customerName = selectedCustomer?.name,
                            customerId = if (creditSale) selectedCustomer?.id else null)
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
        lifecycleScope.launch { createPurchaseWithSuppliers(dao.observeSuppliers().first()) }
    }

    private fun createPurchaseWithSuppliers(suppliers: List<Supplier>) {
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
        val paymentPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("نقدي", "آجل للمورد"))
        }
        val supplierPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                suppliers.map { "${it.name} — ${it.balance} ${it.currency}" }.ifEmpty { listOf("لا يوجد موردون — أضف مورداً أولاً") })
        }
        box.addView(label("طريقة الدفع", 14f, ink, true)); box.addView(paymentPicker)
        box.addView(label("المورد (للفواتير الآجلة)", 14f, ink, true)); box.addView(supplierPicker)
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
                val creditPurchase = paymentPicker.selectedItemPosition == 1
                val selectedSupplier = suppliers.getOrNull(supplierPicker.selectedItemPosition)
                if (creditPurchase && selectedSupplier == null) {
                    info("المورد مطلوب", "أضف مورداً وحدده قبل حفظ فاتورة شراء آجلة.")
                    return@setOnClickListener
                }
                if (creditPurchase && selectedSupplier?.currency != currency) {
                    info("اختلاف العملة", "عملة حساب المورد يجب أن تطابق عملة الفاتورة.")
                    return@setOnClickListener
                }
                val total = parsed.sumOf { it.lineTotal }
                lifecycleScope.launch {
                    try {
                        val purchase = Purchase(currency = currency ?: "USD", subtotal = total, total = total,
                            supplierName = selectedSupplier?.name ?: "",
                            paymentType = if (creditPurchase) "CREDIT" else "CASH",
                            supplierId = if (creditPurchase) selectedSupplier?.id else null)
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