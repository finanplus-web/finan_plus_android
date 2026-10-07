// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor

/** A CA do celular e a configuração. */
class X509Test {
    private val keys = X509.newKeyPair()
    private val ca = X509.createCa(keys.public, X509.signer(keys.private), "Teste")

    /**
     * Valida como um navegador: Chrome, Firefox (NSS) e OpenSSL aplicam as restrições do próprio certificado raiz.
     * O Java não aplica restrições de raiz, então a CA entra no caminho como intermediária (autoassinada,
     * conferida pela mesma chave pública) e as restrições dela são processadas normalmente.
     */
    private fun validates(c: LanServerCert): Boolean {
        val path = CertificateFactory.getInstance("X.509").generateCertPath(listOf(c.chain[0], ca.certificate))
        val params = PKIXParameters(setOf(TrustAnchor(ca.certificate.subjectX500Principal, ca.publicKey, null))).apply { isRevocationEnabled = false }
        return try { CertPathValidator.getInstance("PKIX").validate(path, params); true } catch (e: CertPathValidatorException) { false }
    }

    @Test fun caSoValeParaIpsPrivados() {
        assertTrue(validates(X509.issueServer(ca, InetAddress.getByName("192.168.0.12"))))
        assertTrue(validates(X509.issueServer(ca, InetAddress.getByName("10.1.2.3"))))
        assertTrue(validates(X509.issueServer(ca, InetAddress.getByName("172.20.0.5"))))
        // IP público e nome de site real: recusados mesmo com a CA instalada
        assertFalse(validates(X509.issueServer(ca, InetAddress.getByName("8.8.8.8"))))
        assertFalse(validates(X509.issueServer(ca, InetAddress.getByName("192.168.0.12"), extraDns = "banco.com.br")))
        assertTrue(validates(X509.issueServer(ca, InetAddress.getByName("192.168.0.12"), extraDns = "finan.invalid")))
    }

    @Test fun certificadoDaCa() {
        val c = ca.certificate
        assertEquals(0, c.basicConstraints) // é CA, sem sub-autoridades
        assertTrue(c.keyUsage[5]) // keyCertSign
        assertTrue(c.criticalExtensionOIDs.contains("2.5.29.30")) // Name Constraints crítico
        c.verify(ca.publicKey)
        assertEquals(95, ca.sha256.length)
        assertEquals("Finan+ Rede Local (CA deste celular) · Teste", ca.commonName)
    }

    @Test fun configuracaoValidadaSemSegredos() {
        assertEquals(LanConfig(), LanConfig.parse(""))
        assertEquals(9443, LanConfig.parse("httpsPort=9443;idleMinutes=5").httpsPort)
        assertEquals(5 * 60_000L, LanConfig.parse("idleMinutes=5").idleMillis)
        assertEquals(9444, LanConfig.fromEnv(mapOf("FINAN_LAN_HTTPS_PORT" to "9444")).httpsPort)
        assertThrows(IllegalArgumentException::class.java) { LanConfig.parse("senha=123") }
        assertThrows(IllegalArgumentException::class.java) { LanConfig.parse("httpsPort=80") }
        assertThrows(IllegalArgumentException::class.java) { LanConfig.parse("idleMinutes=abc") }
        assertThrows(IllegalArgumentException::class.java) { LanConfig(httpPort = 9000, httpsPort = 9000) }
    }
}
