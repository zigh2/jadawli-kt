package com.jadawli.kt.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun Context.findActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

object Bio {
    private const val AUTH = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    /** هل في الجهاز بصمة مسجّلة وجاهزة؟ */
    fun can(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** onFail يُستدعى عند اختيار «استخدام الرمز» أو أي خطأ (إلغاء، قفل مؤقت...). */
    fun prompt(act: FragmentActivity, onOk: () -> Unit, onFail: () -> Unit) {
        val exec = ContextCompat.getMainExecutor(act)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("جداولي")
            .setSubtitle("المس مستشعر البصمة للدخول")
            .setAllowedAuthenticators(AUTH)
            .setNegativeButtonText("استخدام الرمز")
            .build()
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail() }
        }
        try {
            BiometricPrompt(act, exec, cb).authenticate(info)
        } catch (e: Exception) {
            onFail()
        }
    }
}
