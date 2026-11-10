package com.example.retailaccounting

import android.app.Activity
import android.content.Intent
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun pinkForPdf()=Color.rgb(190,65,125)\n\nprivate data class InvoiceLine(val name:String,val qty:Double,val price:Double)

private class StoreDb(private val appContext: android.content.Context) : SQLiteOpenHelper(appContext, "good_girl.db", null, 1) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL COLLATE NOCASE UNIQUE,category TEXT NOT NULL DEFAULT '',qty REAL NOT NULL DEFAULT 0,cost REAL NOT NULL DEFAULT 0,price REAL NOT NULL DEFAULT 0)")
  db.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,product_name TEXT NOT NULL,qty REAL NOT NULL,unit_price REAL NOT NULL,unit_cost REAL NOT NULL,total REAL NOT NULL,profit REAL NOT NULL,currency TEXT NOT NULL,fx REAL NOT NULL,created_at TEXT NOT NULL)")
  db.execSQL("CREATE TABLE stock_moves(id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,kind TEXT NOT NULL,qty REAL NOT NULL,note TEXT NOT NULL,created_at TEXT NOT NULL)")
  db.execSQL("CREATE TABLE invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,number TEXT NOT NULL,created_at TEXT NOT NULL,total REAL NOT NULL,currency TEXT NOT NULL,fx REAL NOT NULL)")
  db.execSQL("CREATE TABLE invoice_lines(id INTEGER PRIMARY KEY AUTOINCREMENT,invoice_id INTEGER NOT NULL,product_name TEXT NOT NULL,qty REAL NOT NULL,unit_price REAL NOT NULL)")
  db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
  db.execSQL("INSERT INTO settings(key,value) VALUES('fx','15000')")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {}
 fun now()=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())
 private fun move(db:SQLiteDatabase,id:Long,kind:String,qty:Double,note:String) {
  val v=android.content.ContentValues().apply { put("product_id",id);put("kind",kind);put("qty",qty);put("note",note);put("created_at",now()) }
  db.insertOrThrow("stock_moves",null,v)
 }
 fun addStock(name:String,category:String,qty:Double,cost:Double,price:Double) {
  require(name.isNotBlank() && qty>0 && cost>=0 && price>=0)
  val d=writableDatabase;d.beginTransaction()
  try {
   val c=d.rawQuery("SELECT id FROM products WHERE name=?",arrayOf(name.trim()))
   val id=if(c.moveToFirst()) c.getLong(0) else -1L;c.close()
   if(id<0) {
    val v=android.content.ContentValues().apply {put("name",name.trim());put("category",category);put("qty",qty);put("cost",cost);put("price",price)}
    move(d,d.insertOrThrow("products",null,v),"إدخال بضاعة",qty,"إضافة مادة")
   } else {
    d.execSQL("UPDATE products SET qty=qty+?,cost=?,price=?,category=? WHERE id=?",arrayOf(qty,cost,price,category,id))
    move(d,id,"إدخال بضاعة",qty,"دفعة جديدة؛ تحديث التكلفة إلى آخر سعر")
   }
   d.setTransactionSuccessful()
  } finally {d.endTransaction()}
 }
 fun sell(id:Long,qty:Double,unitPrice:Double,currency:String,fx:Double) {
  require(qty>0 && unitPrice>=0 && fx>0)
  val d=writableDatabase;d.beginTransaction()
  try {
   val c=d.rawQuery("SELECT name,qty,cost FROM products WHERE id=?",arrayOf(id.toString()))
   if(!c.moveToFirst()){c.close();error("المادة غير موجودة")}
   val name=c.getString(0);val available=c.getDouble(1);val cost=c.getDouble(2);c.close()
   require(qty<=available){"الكمية المطلوبة أكبر من المتوفر بالمخزون"}
   d.execSQL("UPDATE products SET qty=qty-? WHERE id=?",arrayOf(qty,id))
   val costInSaleCurrency=if(currency=="USD") cost/fx else cost
   val v=android.content.ContentValues().apply {put("product_id",id);put("product_name",name);put("qty",qty);put("unit_price",unitPrice);put("unit_cost",cost);put("total",qty*unitPrice);put("profit",qty*(unitPrice-costInSaleCurrency));put("currency",currency);put("fx",fx);put("created_at",now())}
   d.insertOrThrow("sales",null,v);move(d,id,"بيع",-qty,"خصم تلقائي عند البيع");d.setTransactionSuccessful()
  } finally {d.endTransaction()}
 }
 fun fx():Double {val c=readableDatabase.rawQuery("SELECT value FROM settings WHERE key='fx'",null);val v=if(c.moveToFirst())c.getString(0).toDoubleOrNull()?:15000.0 else 15000.0;c.close();return v}
 fun setFx(v:Double){require(v>0);val x=android.content.ContentValues().apply{put("key","fx");put("value",v.toString())};writableDatabase.insertWithOnConflict("settings",null,x,SQLiteDatabase.CONFLICT_REPLACE)}
 fun products():List<Array<String>> {val a=mutableListOf<Array<String>>();val c=readableDatabase.rawQuery("SELECT id,name,category,qty,cost,price FROM products ORDER BY name",null);while(c.moveToNext())a.add(arrayOf(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5)));c.close();return a}
 fun createInvoice(lines:List<InvoiceLine>,currency:String,fx:Double):Pair<String,File>{
  require(lines.isNotEmpty() && fx>0)
  lines.forEach { require(it.name.isNotBlank() && it.qty>0 && it.price>=0) }
  val total=lines.sumOf{it.qty*it.price}
  val number="GG-"+SimpleDateFormat("yyMMdd-HHmmss",Locale.US).format(Date())
  val d=writableDatabase;d.beginTransaction()
  try{
   val h=android.content.ContentValues().apply{put("number",number);put("created_at",now());put("total",total);put("currency",currency);put("fx",fx)}
   val id=d.insertOrThrow("invoices",null,h)
   lines.forEach{line->val v=android.content.ContentValues().apply{put("invoice_id",id);put("product_name",line.name);put("qty",line.qty);put("unit_price",line.price)};d.insertOrThrow("invoice_lines",null,v)}
   d.setTransactionSuccessful()
  }finally{d.endTransaction()}

  val currencyName=if(currency=="USD")"دولار" else "ليرة سورية"
  val text=buildString {
   append("Good Girl\\n")
   append("فاتورة مبيعات\\n")
   append("رقم الفاتورة: $number\\n")
   append("التاريخ: ${now()}\\n")
   append("--------------------------------\\n")
   lines.forEachIndexed { index,line ->
    append("${index+1}. ${line.name}\\n")
    append("الكمية: ${String.format(Locale.US,"%.2f",line.qty)}    سعر الوحدة: ${String.format(Locale.US,"%.2f",line.price)}\\n")
    append("الإجمالي: ${String.format(Locale.US,"%.2f",line.qty*line.price)} $currencyName\\n\\n")
   }
   append("--------------------------------\\n")
   append("الإجمالي النهائي: ${String.format(Locale.US,"%.2f",total)} $currencyName\\n")
   append("سعر الصرف المعتمد: ${String.format(Locale.US,"%.2f",fx)} ليرة سورية لكل دولار")
  }
  val textPaint=android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
   textSize=16f
   color=Color.BLACK
   typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)
  }
  val layout=android.text.StaticLayout.Builder.obtain(text,0,text.length,textPaint,515)
   .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
   .setTextDirection(android.text.TextDirectionHeuristics.RTL)
   .setIncludePad(true)
   .setLineSpacing(6f,1f)
   .build()
  val pdf=PdfDocument()
  val pageContentHeight=730
  val pageCount=maxOf(1,(layout.height+pageContentHeight-1)/pageContentHeight)
  for(pageIndex in 0 until pageCount){
   val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageIndex+1).create())
   val canvas=page.canvas
   val titlePaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color=pinkForPdf()
    textSize=13f
    textAlign=android.graphics.Paint.Align.CENTER
   }
   canvas.drawText("Good Girl",297.5f,35f,titlePaint)
   canvas.save()
   canvas.translate(40f,55f-pageIndex*pageContentHeight)
   canvas.clipRect(0,0,515,pageContentHeight)
   layout.draw(canvas)
   canvas.restore()
   pdf.finishPage(page)
  }
  val file=File(appContext.cacheDir,"$number.pdf")
  FileOutputStream(file).use{pdf.writeTo(it)}
  pdf.close()
  return Pair(number,file)
 }
 fun summary(period:String):Pair<Double,Double>{val where=when(period){"اليوم"->"date(created_at)=date('now','localtime')";"الأسبوع"->"date(created_at)>=date('now','-6 days','localtime')";else->"strftime('%Y-%m',created_at)=strftime('%Y-%m','now','localtime')"};val c=readableDatabase.rawQuery("SELECT COALESCE(SUM(CASE WHEN currency='USD' THEN total*fx ELSE total END),0),COALESCE(SUM(CASE WHEN currency='USD' THEN profit*fx ELSE profit END),0) FROM sales WHERE $where",null);c.moveToFirst();val r=Pair(c.getDouble(0),c.getDouble(1));c.close();return r}
 fun salesText():String{val c=readableDatabase.rawQuery("SELECT product_name,qty,total,profit,currency,created_at FROM sales ORDER BY id DESC LIMIT 100",null);val b=StringBuilder();while(c.moveToNext())b.append(c.getString(5)).append(" • ").append(c.getString(0)).append(" × ").append(c.getString(1)).append(" • ").append(c.getString(2)).append(" ").append(c.getString(4)).append(" • الربح ").append(c.getString(3)).append('\n');c.close();return if(b.isEmpty())"لا توجد مبيعات مسجلة بعد" else b.toString()}
}

class MainActivity:Activity(){
 private lateinit var db:StoreDb
 private val ink=Color.rgb(48,35,48);private val pink=Color.rgb(190,65,125);private val pale=Color.rgb(250,241,247)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);db=StoreDb(this);home()}
 private fun base(title:String):LinearLayout{
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(pale);layoutDirection=View.LAYOUT_DIRECTION_RTL}
  root.addView(TextView(this).apply{text=title;textSize=23f;setTextColor(Color.WHITE);setBackgroundColor(ink);gravity=Gravity.CENTER_VERTICAL or Gravity.RIGHT;setPadding(24,18,24,18)},LinearLayout.LayoutParams(-1,-2))
  val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
  listOf("الرئيسية","المواد والمخزون","مبيع","الفواتير","التقارير","الإعدادات").forEach{label->nav.addView(Button(this).apply{text=label;textSize=10f;setOnClickListener{when(label){"الرئيسية"->home();"المواد والمخزون"->products();"مبيع"->sale();"الفواتير"->invoices();"التقارير"->reports();else->settings()}}},LinearLayout.LayoutParams(0,-2,1f))}
  root.addView(nav);val sc=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,24)};sc.addView(body);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);return body
 }
 private fun label(s:String,size:Float=16f)=TextView(this).apply{text=s;textSize=size;setTextColor(ink);setPadding(8,8,8,8)}
 private fun field(h:String)=EditText(this).apply{hint=h;textSize=16f;setSingleLine(true);layoutDirection=View.LAYOUT_DIRECTION_RTL}
 private fun btn(s:String,fn:()->Unit)=Button(this).apply{text=s;setTextColor(Color.WHITE);setBackgroundColor(pink);setOnClickListener{fn()}}
 private fun fmt(v:Double)=String.format(Locale.US,"%,.2f",v)
 private fun home(){val b=base("Good Girl • إدارة المتجر");b.addView(label("أهلاً بكِ في Good Girl",22f));listOf("اليوم","الأسبوع","الشهر").forEach{p->val x=db.summary(p);b.addView(label("$p | المبيعات: ${fmt(x.first)} ل.س | الربح: ${fmt(x.second)} ل.س"))};b.addView(label("عدد الأصناف: ${db.products().size}",18f));b.addView(btn("إضافة مادة جديدة"){products()});b.addView(btn("تسجيل عملية مبيع"){sale()})}
 private fun products(){val b=base("المواد والمخزون • قسم موحد");val n=field("اسم المادة");val cat=field("التصنيف");val q=field("الكمية المدخلة");val cost=field("سعر التكلفة");val price=field("سعر البيع");listOf(n,cat,q,cost,price).forEach{b.addView(it)};b.addView(btn("حفظ المادة / إدخال كمية جديدة"){try{db.addStock(n.text.toString(),cat.text.toString(),q.text.toString().toDouble(),cost.text.toString().toDouble(),price.text.toString().toDouble());Toast.makeText(this,"تم تحديث المادة والمخزون",Toast.LENGTH_LONG).show();products()}catch(e:Exception){Toast.makeText(this,e.message?:"تحقق من البيانات",Toast.LENGTH_LONG).show()}});b.addView(label("الأصناف الحالية",19f));db.products().forEach{p->b.addView(label("${p[1]} | المتوفر: ${p[3]} | التكلفة: ${p[4]} | البيع: ${p[5]}",14f))}}
 private fun sale(){val b=base("تسجيل مبيع");val items=db.products();if(items.isEmpty()){b.addView(label("أضف مادة أولاً من قسم المواد والمخزون"));return};val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items.map{"${it[1]} (المتوفر ${it[3]})"});b.addView(sp);val q=field("الكمية المباعة");val p=field("سعر بيع الوحدة");b.addView(q);b.addView(p);val cur=Spinner(this);cur.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("ليرة سورية","دولار"));b.addView(cur);b.addView(label("سعر الصرف: ${fmt(db.fx())} ل.س لكل دولار"));b.addView(btn("تأكيد المبيع وخصم المخزون"){try{db.sell(items[sp.selectedItemPosition][0].toLong(),q.text.toString().toDouble(),p.text.toString().toDouble(),if(cur.selectedItemPosition==0)"SYP" else "USD",db.fx());Toast.makeText(this,"تم تسجيل المبيع وتحديث المخزون",Toast.LENGTH_LONG).show();sale()}catch(e:Exception){Toast.makeText(this,e.message?:"تحقق من البيانات",Toast.LENGTH_LONG).show()}})}
 private fun invoices(){
  val b=base("الفواتير • إنشاء وتصدير")
  val items=db.products();if(items.isEmpty()){b.addView(label("أضف المواد أولاً حتى تتمكن من إعداد فاتورة."));return}
  val lines=mutableListOf<InvoiceLine>();val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items.map{it[1]});b.addView(sp)
  val q=field("الكمية");val price=field("سعر الوحدة");b.addView(q);b.addView(price)
  val currency=Spinner(this);currency.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("ليرة سورية","دولار"));b.addView(currency)
  val list=TextView(this).apply{setTextColor(ink);textSize=14f};b.addView(list)
  b.addView(btn("إضافة بند للفاتورة"){try{val line=InvoiceLine(items[sp.selectedItemPosition][1],q.text.toString().toDouble(),price.text.toString().toDouble());require(line.qty>0&&line.price>=0);lines.add(line);list.text=lines.joinToString("\\n"){"${it.name} × ${it.qty} — ${it.qty*it.price}"};q.text.clear();price.text.clear()}catch(e:Exception){Toast.makeText(this,"أدخل كمية وسعراً صحيحين",Toast.LENGTH_SHORT).show()}})
  b.addView(btn("حفظ الفاتورة وتصدير PDF للمشاركة"){try{val result=db.createInvoice(lines,if(currency.selectedItemPosition==0)"SYP" else "USD",db.fx());val uri=FileProvider.getUriForFile(this,packageName+".provider",result.second);val send=Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_SUBJECT,"فاتورة Good Girl ${result.first}");putExtra(Intent.EXTRA_STREAM,uri);clipData=android.content.ClipData.newUri(contentResolver,"فاتورة",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};startActivity(Intent.createChooser(send,"مشاركة الفاتورة"));lines.clear();list.text=""}catch(e:Exception){Toast.makeText(this,e.message?:"تعذر إنشاء الفاتورة",Toast.LENGTH_LONG).show()}})
 }
 private fun reports(){val b=base("التقارير");listOf("اليوم","الأسبوع","الشهر").forEach{p->val x=db.summary(p);b.addView(label("$p — المبيعات: ${fmt(x.first)} ل.س — الربح: ${fmt(x.second)} ل.س"))};b.addView(label("آخر المبيعات",18f));b.addView(label(db.salesText(),13f))}
 private fun settings(){val b=base("الإعدادات");b.addView(label("سعر صرف الدولار مقابل الليرة السورية"));val r=field("سعر الصرف");r.setText(db.fx().toString());b.addView(r);b.addView(btn("حفظ سعر الصرف"){try{db.setFx(r.text.toString().toDouble());Toast.makeText(this,"تم حفظ سعر الصرف",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,"أدخل سعراً صحيحاً",Toast.LENGTH_SHORT).show()}})}
 override fun onDestroy(){db.close();super.onDestroy()}
}