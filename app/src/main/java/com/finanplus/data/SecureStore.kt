// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Arquivo de dados criptografado com AES-256-GCM.
 * A chave é gerada e guardada no Android Keystore (em hardware quando o aparelho tem),
 * nunca sai dele e não pode ser copiada para outro aparelho.
 *
 * Formato: "FNP1" | tamanho do IV (1 byte) | IV | texto cifrado + tag GCM.
 * Gravação atômica: escreve em .tmp, sincroniza e renomeia por cima.
 */
class SecureStore(context: Context) {
    sealed class ReadResult {
        data object Empty : ReadResult()
        data class Ok(val json: String) : ReadResult()
        /** não decifrou (chave perdida ou arquivo adulterado) */
        data class Unreadable(val copy: File?) : ReadResult()
    }

    private val dir = context.filesDir
    private val file = File(dir, "finanplus.dat")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    fun read(): ReadResult {
        if (!file.exists() || file.length() == 0L) return ReadResult.Empty
        val bytes = file.readBytes()
        return try {
            require(bytes.size > 5 && String(bytes, 0, 4, Charsets.US_ASCII) == MAGIC) { "cabeçalho" }
            val ivLen = bytes[4].toInt()
            require(ivLen in 12..16 && bytes.size > 5 + ivLen) { "iv" }
            val iv = bytes.copyOfRange(5, 5 + ivLen)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            ReadResult.Ok(String(c.doFinal(bytes, 5 + ivLen, bytes.size - 5 - ivLen), Charsets.UTF_8))
        } catch (e: Exception) {
            ReadResult.Unreadable(keepCopy(bytes))
        }
    }

    @Synchronized
    fun write(json: String) {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val ct = c.doFinal(json.toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "finanplus.dat.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(iv.size)
            out.write(iv)
            out.write(ct)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "Não foi possível salvar" } }
    }

    /** Copia o arquivo cifrado atual como está (continua cifrado; nada em texto puro no disco). */
    @Synchronized
    fun keepEncryptedCopy(): File? = try { if (file.exists()) keepCopy(file.readBytes()) else null } catch (e: Exception) { null }

    /** Guarda uma cópia do arquivo que não pôde ser lido (nunca é sobrescrito sem cópia). */
    fun keepCopy(bytes: ByteArray): File? = try {
        File(dir, "finanplus.danificado-${System.currentTimeMillis()}.dat").apply { writeBytes(bytes) }
    } catch (e: Exception) { null }

    /** Apaga dados, cópias danificadas e a chave. */
    @Synchronized
    fun wipe() {
        dir.listFiles()?.filter { it.name.startsWith("finanplus.") }?.forEach { it.delete() }
        try { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) } catch (_: Exception) { }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "finanplus_data_v1"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val MAGIC = "FNP1"
    }
}
