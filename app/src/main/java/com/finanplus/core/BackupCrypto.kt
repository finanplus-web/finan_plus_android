// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup criptografado com senha (usado pelo backup automático). Formato aberto, para o Finan+ web e o Linux
 * poderem ler com bibliotecas comuns (WebCrypto, OpenSSL):
 *
 * ```
 * {"format":"finanplus-backup-cifrado","v":1,"kdf":"PBKDF2-HMAC-SHA256","iter":310000,
 *  "salt":"<16 bytes, base64>","cipher":"AES-256-GCM","iv":"<12 bytes, base64>","data":"<texto cifrado + tag, base64>"}
 * ```
 *
 * Dentro de "data" está o backup JSON normal (o mesmo do "Backup JSON"). Detalhes em BACKUP-AUTOMATICO.md.
 */
object BackupCrypto {
    const val FORMAT = "finanplus-backup-cifrado"
    const val ITER = 310_000
    const val MIN_PASSWORD = 8
    private const val MIN_ITER = 100_000
    private const val MAX_ITER = 5_000_000
    private const val SALT = 16
    private const val IV = 12

    /** O texto é um backup criptografado do Finan+ (só olha o envelope; não precisa da senha). */
    fun isEncrypted(text: String): Boolean = try {
        (Json.parse(text.removePrefix("﻿")) as? Map<*, *>)?.get("format") == FORMAT
    } catch (_: Exception) { false }

    fun encrypt(plain: String, password: String, iter: Int = ITER, rnd: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(SALT).also(rnd::nextBytes)
        val iv = ByteArray(IV).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt, iter), GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return Json.stringify(linkedMapOf(
            "format" to FORMAT, "v" to 1, "kdf" to "PBKDF2-HMAC-SHA256", "iter" to iter,
            "salt" to b64.encodeToString(salt), "cipher" to "AES-256-GCM", "iv" to b64.encodeToString(iv), "data" to b64.encodeToString(data),
        ))
    }

    /** Devolve o backup JSON de dentro. Lança [BackupException] com senha errada ou arquivo danificado. */
    fun decrypt(text: String, password: String): String {
        val o = try { Json.parse(text.removePrefix("﻿")) as? Map<*, *> } catch (_: Exception) { null }
            ?: throw BackupException("Arquivo danificado")
        if (o["format"] != FORMAT) throw BackupException("Não é um backup criptografado do Finan+")
        val v = (o["v"] as? Json.NumberToken)?.text?.toIntOrNull()
        if (v != 1) throw BackupException("Este backup foi feito por uma versão mais nova do Finan+")
        val iter = (o["iter"] as? Json.NumberToken)?.text?.toIntOrNull()?.takeIf { it in MIN_ITER..MAX_ITER }
            ?: throw BackupException("Arquivo danificado")
        val b64 = Base64.getDecoder()
        fun bytes(k: String) = try { b64.decode(o[k] as? String ?: "") } catch (_: IllegalArgumentException) { ByteArray(0) }
        val salt = bytes("salt"); val iv = bytes("iv"); val data = bytes("data")
        if (salt.size != SALT || iv.size != IV || data.size < 16) throw BackupException("Arquivo danificado")
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt, iter), GCMParameterSpec(128, iv))
            String(c.doFinal(data), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw BackupException("Senha incorreta ou arquivo danificado")
        }
    }

    private fun key(password: String, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iter, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }
}
