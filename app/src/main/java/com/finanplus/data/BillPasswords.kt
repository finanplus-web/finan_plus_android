// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Senhas de PDF de fatura que a pessoa pediu para lembrar, uma por cartão.
 *
 * - Desligado por padrão: só guarda quando a pessoa marca "Lembrar para este cartão".
 * - Cifradas com AES-256-GCM por uma chave própria do Android Keystore (não sai do aparelho).
 * - Pertencem a ESTE aparelho: não vão para o backup JSON, nem para o acesso pela rede.
 * - Apagadas ao esquecer no cartão, ao excluir o cartão e em "Apagar todos os dados".
 */
class BillPasswords private constructor(context: Context) {
    private val sp = context.getSharedPreferences("finanplus_bill_passwords", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun has(cardId: String) = sp.contains(name(cardId))

    /** null se não houver senha lembrada, ou se ela não puder ser lida (chave perdida: é apagada). */
    fun get(cardId: String): String? {
        val blob = sp.getString(name(cardId), null) ?: return null
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV))
            String(c.doFinal(raw, IV, raw.size - IV), Charsets.UTF_8)
        } catch (e: Exception) {
            remove(cardId); null
        }
    }

    fun set(cardId: String, password: String) {
        try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(password.toByteArray(Charsets.UTF_8))
            sp.edit(commit = true) { putString(name(cardId), Base64.encodeToString(out, Base64.NO_WRAP)) }
        } catch (_: Exception) { /* sem Keystore: simplesmente não lembra */ }
    }

    fun remove(cardId: String) = sp.edit(commit = true) { remove(name(cardId)) }

    fun wipe() {
        sp.edit(commit = true) { clear() }
        try { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) } catch (_: Exception) { }
    }

    private fun name(cardId: String) = "card:$cardId"

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "finanplus_bill_passwords_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV = 12

        @Volatile private var inst: BillPasswords? = null
        fun get(context: Context): BillPasswords = inst ?: synchronized(this) { inst ?: BillPasswords(context.applicationContext).also { inst = it } }
    }
}
