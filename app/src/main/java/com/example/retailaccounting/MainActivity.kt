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
        box.addView(name); box.addView(category); box.addView(unit)
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
