// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.util.Date

/**
 * A autoridade certificadora (CA) deste celular.
 *
 * - A chave privada é criada DENTRO do Android Keystore e não pode ser exportada: nem o app consegue
 *   lê-la, só pedir assinaturas. Ela só assina o certificado do servidor quando o acesso é ligado.
 * - O certificado da CA (público) fica em files/lan/ca.crt. É ele que o usuário instala no navegador.
 * - Uma falha MOMENTÂNEA do Keystore nunca gera uma CA nova: isso desfaria, em silêncio, a confiança
 *   de todos os computadores. Só se cria outra quando não existe nenhuma, quando o arquivo não
 *   corresponde à chave (corrompido/trocado) ou quando expirou — ou em "Gerar novo".
 */
object LanCa {
    private const val ALIAS = "finanplus_lan_ca"
    private const val KEYSTORE = "AndroidKeyStore"

    /** O Keystore não respondeu agora (tente de novo); nada foi apagado. */
    class KeystoreUnavailable(cause: Throwable) : Exception("Keystore indisponível", cause)

    private sealed interface Found {
        data class Valid(val ca: LanCaKey) : Found
        data object Missing : Found
        /** existe, mas não serve (arquivo de outra chave, assinatura inválida, expirada) */
        data object Unusable : Found
    }

    private fun file(ctx: Context) = File(ctx.filesDir, "lan/ca.crt")

    private fun keyStore(): KeyStore = try {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }
    } catch (e: GeneralSecurityException) { throw KeystoreUnavailable(e) } catch (e: IOException) { throw KeystoreUnavailable(e) }

    private fun find(ctx: Context): Found {
        val f = file(ctx)
        val ks = keyStore()
        val key = try { ks.getKey(ALIAS, null) as? PrivateKey } catch (e: GeneralSecurityException) { throw KeystoreUnavailable(e) }
        val pub = try { ks.getCertificate(ALIAS)?.publicKey } catch (e: GeneralSecurityException) { throw KeystoreUnavailable(e) }
        if (key == null || pub == null || !f.exists()) return if (key == null && !f.exists()) Found.Missing else Found.Unusable
        val der = try { f.readBytes() } catch (e: IOException) { throw KeystoreUnavailable(e) }
        return try {
            val cert = X509.parse(der)
            cert.verify(pub)
            // o arquivo precisa ser da mesma chave (proteção contra um arquivo trocado) e ainda válido
            if (!cert.publicKey.encoded.contentEquals(pub.encoded) || cert.notAfter.before(Date())) Found.Unusable
            else Found.Valid(LanCaKey(der, pub, X509.signer(key)))
        } catch (e: GeneralSecurityException) {
            Found.Unusable
        }
    }

    /** CA existente e válida, ou null (não cria nada). Lento (Keystore): chamar fora da thread principal. */
    @Synchronized
    fun peek(ctx: Context): LanCaKey? = try { (find(ctx) as? Found.Valid)?.ca } catch (e: KeystoreUnavailable) { null }

    /** A CA deste celular; cria quando não existe ou não serve. [KeystoreUnavailable] = tente de novo depois. */
    @Synchronized
    fun get(ctx: Context): LanCaKey = when (val f = find(ctx)) {
        is Found.Valid -> f.ca
        Found.Missing, Found.Unusable -> create(ctx)
    }

    @Synchronized
    fun reset(ctx: Context) {
        try { keyStore().deleteEntry(ALIAS) } catch (e: GeneralSecurityException) { Lan.log.error("ca_reset_failed", e) } catch (e: KeystoreUnavailable) { Lan.log.error("ca_reset_failed", e) }
        file(ctx).delete()
    }

    private fun create(ctx: Context): LanCaKey {
        reset(ctx)
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        kpg.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        val kp = kpg.generateKeyPair()
        // o modelo do celular no nome ajuda a reconhecer a autoridade na lista do navegador
        val ca = X509.createCa(kp.public, X509.signer(kp.private), Build.MODEL ?: "")
        val f = file(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "ca.crt.tmp")
        tmp.writeBytes(ca.certDer)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("não foi possível gravar o certificado") }
        Lan.log.info("ca_created")
        return ca
    }
}
