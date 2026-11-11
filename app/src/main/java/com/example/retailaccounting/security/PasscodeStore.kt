package com.example.retailaccounting.security

import android.content.Context
import java.security.MessageDigest

object PasscodeStore {
    private const val PREFS = "goodgirl_security"
    private const val KEY_HASH = "passcode_sha256"

    fun hasPasscode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_HASH)

    fun save(context: Context, passcode: String) {
        require(passcode.length >= 4 && passcode.all { it in '0'..'9' }) {
            "الرمز السري يجب أن يتكون من 4 أرقام على الأقل"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HASH, hash(passcode))
            .apply()
    }

    fun verify(context: Context, passcode: String): Boolean {
        val expected = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HASH, null) ?: return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            hash(passcode).toByteArray(Charsets.UTF_8)
        )
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
