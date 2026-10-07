// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import java.net.ServerSocket
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.X509ExtendedKeyManager

/** HTTPS: a CA do celular e o certificado do servidor emitido por ela para o IP atual. */
class LanTls(val ca: LanCaKey, val cert: LanServerCert) {
    private val context: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(arrayOf(FixedKeyManager(cert)), null, SecureRandom()) }
    }

    /** Socket de servidor TLS 1.3/1.2 (o que a plataforma oferecer dentro disso), sem certificado de cliente, ainda sem endereço. */
    fun serverSocket(): ServerSocket =
        (context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            enabledProtocols = PROTOCOLS.filter { it in supportedProtocols }.toTypedArray()
            check(enabledProtocols.isNotEmpty()) { "TLS 1.2/1.3 indisponível" }
            needClientAuth = false
            wantClientAuth = false
        }

    /** Sempre apresenta o certificado do servidor (com a CA na cadeia). */
    private class FixedKeyManager(private val c: LanServerCert) : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(ALIAS)
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String = ALIAS
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String = ALIAS
        override fun getCertificateChain(alias: String?): Array<X509Certificate> = c.chain.toTypedArray()
        override fun getPrivateKey(alias: String?): PrivateKey = c.keyPair.private
    }

    companion object {
        private const val ALIAS = "finanplus"
        private val PROTOCOLS = listOf("TLSv1.3", "TLSv1.2")
    }
}
