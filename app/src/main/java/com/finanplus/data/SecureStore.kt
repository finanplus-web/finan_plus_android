// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.AEADBadTagException
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
 * Gravação atômica: escreve em .tmp, sincroniza, move a versão atual para .bak e o .tmp para o lugar
 * (as duas trocas com ATOMIC_MOVE). Na leitura, se o principal faltar ou não decifrar, tenta o .bak.
 */
class SecureStore(context: Context) {
    sealed class ReadResult {
        data object Empty : ReadResult()
        data class Ok(val json: String) : ReadResult()
        /** não decifrou: chave perdida (ex.: sistema restaurado) ou arquivo adulterado. Cópia guardada em [copy]. */
        data class Unreadable(val copy: File?) : ReadResult()
        /**
         * Erro do Keystore que pode ser passageiro (comum logo após ligar o aparelho ou atualizar o sistema).
         * O arquivo continua intacto: nada deve ser gravado por cima; o usuário pode tentar de novo.
         */
        data class KeystoreError(val message: String) : ReadResult()
    }

    private val dir = context.filesDir
    private val file = File(dir, "finanplus.dat")
    private val bak = File(dir, "finanplus.dat.bak")

    /** Chave existente, ou null se não houver (não cria: quem lê não deve trocar a chave de dados já gravados). */
    private fun existingKey(): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return ks.getKey(ALIAS, null) as? SecretKey
    }

    private fun key(): SecretKey {
        existingKey()?.let { return it }
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

    private sealed class Decoded {
        data class Ok(val json: String) : Decoded()
        /** conteúdo definitivamente ilegível (cabeçalho ruim, tag GCM não confere, sem chave) */
        data object Bad : Decoded()
        data class Transient(val e: Exception) : Decoded()
    }

    private fun decode(bytes: ByteArray): Decoded {
        if (bytes.size <= 5 || String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) return Decoded.Bad
        val ivLen = bytes[4].toInt()
        if (ivLen !in 12..16 || bytes.size <= 5 + ivLen) return Decoded.Bad
        val iv = bytes.copyOfRange(5, 5 + ivLen)
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val k = existingKey() ?: return Decoded.Bad // chave não existe neste aparelho
                val c = Cipher.getInstance(TRANSFORM)
                c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
                return Decoded.Ok(String(c.doFinal(bytes, 5 + ivLen, bytes.size - 5 - ivLen), Charsets.UTF_8))
            } catch (e: AEADBadTagException) {
                return Decoded.Bad
            } catch (e: Exception) {
                // KeyStoreException, ProviderException, UnrecoverableKeyException…: tenta de novo em instantes
                last = e
                if (attempt < 2) Thread.sleep(400L * (attempt + 1))
            }
        }
        return Decoded.Transient(last ?: IllegalStateException("Keystore"))
    }

    @Synchronized
    fun read(): ReadResult {
        val main = if (file.exists() && file.length() > 0) file.readBytes() else null
        if (main == null) {
            // sem o principal (ex.: processo morreu entre as duas trocas da gravação): usa a versão anterior
            if (bak.exists() && bak.length() > 0) (decode(bak.readBytes()) as? Decoded.Ok)?.let { return ReadResult.Ok(it.json) }
            return ReadResult.Empty
        }
        return when (val d = decode(main)) {
            is Decoded.Ok -> ReadResult.Ok(d.json)
            is Decoded.Transient -> ReadResult.KeystoreError(d.e.javaClass.simpleName + (d.e.message?.let { ": $it" } ?: ""))
            is Decoded.Bad -> {
                val copy = keepCopy(main)
                val prev = if (bak.exists() && bak.length() > 0) decode(bak.readBytes()) else null
                if (prev is Decoded.Ok) ReadResult.Ok(prev.json) else ReadResult.Unreadable(copy)
            }
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
        if (file.exists()) Files.move(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Copia o arquivo cifrado atual como está (continua cifrado; nada em texto puro no disco). */
    @Synchronized
    fun keepEncryptedCopy(): File? = try { if (file.exists()) keepCopy(file.readBytes()) else null } catch (e: Exception) { null }

    /** Guarda uma cópia do arquivo que não pôde ser lido (nunca é sobrescrito sem cópia). Mantém só as 3 mais recentes. */
    fun keepCopy(bytes: ByteArray): File? = try {
        val f = File(dir, "finanplus.danificado-${System.currentTimeMillis()}.dat").apply { writeBytes(bytes) }
        dir.listFiles()?.filter { it.name.startsWith("finanplus.danificado-") }?.sortedByDescending { it.name }?.drop(MAX_COPIES)?.forEach { it.delete() }
        f
    } catch (e: Exception) { null }

    /** Apaga dados, versão anterior, cópias danificadas e a chave. */
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
        private const val MAX_COPIES = 3
    }
}
