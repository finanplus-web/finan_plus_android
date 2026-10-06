// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
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
    private val HEX_RE = Regex("^[0-9a-f]+$")
    private fun hex(b: ByteArray) = buildString { b.forEach { append(hexChars[(it.toInt() shr 4) and 15]); append(hexChars[it.toInt() and 15]) } }
    private fun unhex(s: String) = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun derive(pin: String, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iter, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    fun isValidFormat(pin: String) = pin.length in 4..8 && pin.all { it in '0'..'9' }

    suspend fun hash(pin: String): String = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        "pbkdf2\$$ITER\$${hex(salt)}\$${hex(derive(pin, salt, ITER))}"
    }

    /**
     * Confere o PIN. Um registro malformado (preferências corrompidas) devolve false em vez de lançar
     * exceção: antes, o app fechava a cada tentativa e o usuário ficava trancado fora.
     */
    suspend fun verify(pin: String, stored: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val p = stored.split('$')
            if (p.size != 4 || p[0] != "pbkdf2") return@withContext false
            val iter = p[1].toIntOrNull() ?: return@withContext false
            if (iter !in 10_000..2_000_000) return@withContext false
            if (p[2].length !in 16..128 || p[2].length % 2 != 0 || !HEX_RE.matches(p[2])) return@withContext false
            if (p[3].length != 64 || !HEX_RE.matches(p[3])) return@withContext false
            MessageDigest.isEqual(derive(pin, unhex(p[2]), iter), unhex(p[3]))
        } catch (e: Exception) { false }
    }
}

/**
 * Estado de bloqueio da tela. Ao abrir o app (processo novo) com PIN, começa bloqueado.
 * Ao voltar do segundo plano, bloqueia conforme o "bloqueio automático" (imediato, N minutos ou só ao abrir).
 *
 * Limite de tentativas: depois de 5 PINs errados seguidos, cada nova tentativa espera 30 s, 1 min, 2 min…
 * (dobrando, até 1 hora). O contador fica gravado no aparelho: fechar o app ou reiniciar o celular
 * não zera a espera (antes ficava só na memória e bastava fechar o app para tentar de novo).
 */
object AppLock {
    /** valores do bloqueio automático (guardado em DevicePrefs, não no backup) */
    const val AUTOLOCK_ON_OPEN = -1
    const val AUTOLOCK_IMMEDIATE = 0
    val AUTOLOCK_OPTIONS = listOf(AUTOLOCK_IMMEDIATE, 1, 5, 15, 30, AUTOLOCK_ON_OPEN)

    private const val FREE_TRIES = 5
    private const val MAX_WAIT_MS = 3_600_000L
    /** seletor de arquivos e afins: tempo fora do app que não conta como "saiu do app" */
    private const val EXTERNAL_GRACE_MS = 5 * 60_000L
    private const val K_FAILS = "lockFails"
    private const val K_AT_WALL = "lockFailAtWall"
    private const val K_AT_ELAPSED = "lockFailAtElapsed"
    private const val K_BOOT = "lockFailBoot"

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked
    private var backgroundAt = 0L
    private var externalUntil = 0L
    private var sp: SharedPreferences? = null
    private var bootCount = -1

    /** Chamado uma vez em FinanApp.onCreate. Usa o mesmo arquivo de DevicePrefs ("Apagar tudo" também zera o contador). */
    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("finanplus_device", Context.MODE_PRIVATE)
        bootCount = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) } catch (e: Exception) { -1 }
    }

    fun lockNow() { _locked.value = true }
    fun unlock() { resetFails(); _locked.value = false }

    /** Antes de abrir o seletor de arquivos do sistema: voltar dele não pede o PIN de novo (até 5 min). */
    fun allowExternalOnce() { externalUntil = SystemClock.elapsedRealtime() + EXTERNAL_GRACE_MS }

    fun onBackground() { backgroundAt = SystemClock.elapsedRealtime() }
    fun onForeground(lockEnabled: Boolean, autoLockMin: Int) {
        if (!lockEnabled) { _locked.value = false; return }
        val now = SystemClock.elapsedRealtime()
        val external = now < externalUntil
        externalUntil = 0L
        if (backgroundAt == 0L || autoLockMin == AUTOLOCK_ON_OPEN) return
        val away = now - backgroundAt
        val limit = maxOf(autoLockMin.coerceAtLeast(0) * 60_000L, if (external) EXTERNAL_GRACE_MS else 0L)
        if (away >= limit) _locked.value = true
    }

    // ---------------- tentativas de PIN ----------------

    private fun delayFor(fails: Int): Long =
        if (fails < FREE_TRIES) 0L else minOf(30_000L shl minOf(fails - FREE_TRIES, 10), MAX_WAIT_MS)

    private fun remainingMs(): Long {
        val p = sp ?: return 0L
        val fails = p.getInt(K_FAILS, 0)
        val delay = delayFor(fails)
        if (delay == 0L) return 0L
        // mesmo boot: relógio monotônico (mudar a hora do aparelho não adianta); outro boot: relógio de parede
        val rem = if (bootCount != -1 && p.getInt(K_BOOT, -2) == bootCount) p.getLong(K_AT_ELAPSED, 0L) + delay - SystemClock.elapsedRealtime()
        else p.getLong(K_AT_WALL, 0L) + delay - System.currentTimeMillis()
        return rem.coerceIn(0L, delay) // atrasar o relógio não aumenta a espera além do previsto
    }

    /** Segundos restantes de espera, ou 0 se pode tentar. */
    fun waitSeconds(): Int { val ms = remainingMs(); return if (ms > 0) ((ms + 999) / 1000).toInt() else 0 }

    /**
     * Registra uma tentativa ANTES de conferir o PIN (fechar o app no meio da conferência não escapa da contagem).
     * Um acerto chama [unlock]/[resetFails], que zera.
     */
    fun registerAttempt() {
        val p = sp ?: return
        p.edit().putInt(K_FAILS, p.getInt(K_FAILS, 0) + 1).putLong(K_AT_WALL, System.currentTimeMillis())
            .putLong(K_AT_ELAPSED, SystemClock.elapsedRealtime()).putInt(K_BOOT, bootCount).commit()
    }

    fun resetFails() { sp?.edit()?.remove(K_FAILS)?.remove(K_AT_WALL)?.remove(K_AT_ELAPSED)?.remove(K_BOOT)?.commit() }
}
