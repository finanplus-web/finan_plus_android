// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.security

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN guardado como PBKDF2-HMAC-SHA256 com sal aleatório. Comparação em tempo constante. */
object Pin {
    private const val ITER = 120_000
    private val hexChars = "0123456789abcdef"
    private fun hex(b: ByteArray) = buildString { b.forEach { append(hexChars[(it.toInt() shr 4) and 15]); append(hexChars[it.toInt() and 15]) } }
    private fun unhex(s: String) = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun derive(pin: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iter, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    fun isValidFormat(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    suspend fun hash(pin: String): String = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        "pbkdf2\$$ITER\$${hex(salt)}\$${hex(derive(pin, salt, ITER))}"
    }

    suspend fun verify(pin: String, stored: String): Boolean = withContext(Dispatchers.Default) {
        val p = stored.split('$')
        if (p.size != 4 || p[0] != "pbkdf2") return@withContext false
        val iter = p[1].toIntOrNull() ?: return@withContext false
        MessageDigest.isEqual(derive(pin, unhex(p[2]), iter), unhex(p[3]))
    }
}

/**
 * Estado de bloqueio da tela. Ao abrir o app (processo novo) com PIN, começa bloqueado.
 * Ao voltar do segundo plano, bloqueia se passou o tempo de "bloqueio automático".
 * Após 5 erros seguidos, impõe espera crescente entre tentativas.
 */
object AppLock {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked
    private var backgroundAt = 0L
    private var fails = 0
    private var waitUntil = 0L

    fun lockNow() { _locked.value = true }
    fun unlock() { fails = 0; _locked.value = false }

    fun onBackground() { backgroundAt = SystemClock.elapsedRealtime() }
    fun onForeground(lockEnabled: Boolean, autoLockMin: Int) {
        if (!lockEnabled) { _locked.value = false; return }
        if (autoLockMin > 0 && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt >= autoLockMin * 60_000L) _locked.value = true
    }

    /** Segundos restantes de espera, ou 0 se pode tentar. */
    fun waitSeconds(): Int {
        val ms = waitUntil - SystemClock.elapsedRealtime()
        return if (ms > 0) ((ms + 999) / 1000).toInt() else 0
    }

    fun registerFail() {
        fails++
        if (fails >= 5) waitUntil = SystemClock.elapsedRealtime() + 30_000L * (fails - 4)
    }
}
