package com.example.retailaccounting.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseBackup {
    private const val DATABASE_NAME = "retail_accounting.db"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun export(context: Context, destination: Uri) {
        AppDatabase.closeInstanceForBackup()
        val database = context.getDatabasePath(DATABASE_NAME)
        require(database.exists() && database.length() > 0L) { "قاعدة البيانات غير موجودة" }
        context.contentResolver.openOutputStream(destination, "w").use { output ->
            requireNotNull(output) { "تعذر فتح ملف النسخة الاحتياطية" }
            FileInputStream(database).use { input -> input.copyTo(output) }
            output.flush()
        }
        AppDatabase.getInstance(context)
    }

    fun restore(context: Context, source: Uri) {
        val temporary = File(context.cacheDir, "goodgirl_restore.tmp")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "تعذر قراءة ملف النسخة الاحتياطية" }
            FileOutputStream(temporary).use { output -> input.copyTo(output) }
        }
        require(temporary.length() > SQLITE_HEADER.size) { "ملف النسخة الاحتياطية فارغ أو غير صالح" }
        val header = ByteArray(SQLITE_HEADER.size)
        FileInputStream(temporary).use { input ->
            require(input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)) {
                "الملف المحدد ليس نسخة قاعدة بيانات Good Girl صالحة"
            }
        }
        AppDatabase.closeInstanceForBackup()
        val database = context.getDatabasePath(DATABASE_NAME)
        database.parentFile?.mkdirs()
        val replacement = File(database.parentFile, "$DATABASE_NAME.restore")
        temporary.copyTo(replacement, overwrite = true)
        check(replacement.renameTo(database)) { "تعذر استبدال قاعدة البيانات؛ لم يتم اعتماد النسخة" }
        File(database.parentFile, "$DATABASE_NAME-wal").delete()
        File(database.parentFile, "$DATABASE_NAME-shm").delete()
        temporary.delete()
        AppDatabase.getInstance(context)
    }
}
