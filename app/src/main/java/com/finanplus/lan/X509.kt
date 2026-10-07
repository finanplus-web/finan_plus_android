// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Autoridade certificadora (CA) própria do Finan+ para o acesso pela rede.
 *
 * A chave privada nunca precisa sair de onde foi criada: quem cria a CA só fornece a chave pública
 * e uma função que assina ([sign]). No Android a chave fica no Android Keystore (sem exportação);
 * nos testes, é uma chave comum em memória.
 */
class LanCaKey(
    /** certificado da CA em DER (o arquivo .crt que o usuário instala no navegador) */
    val certDer: ByteArray,
    val publicKey: PublicKey,
    private val sign: (ByteArray) -> ByteArray,
) {
    /** chave pública da CA em SubjectPublicKeyInfo; identifica a CA nos certificados emitidos */
    internal val keyId: ByteArray = X509.keyId(publicKey)
    internal fun signTbs(tbs: ByteArray): ByteArray = sign(tbs)

    val certificate: X509Certificate get() = X509.parse(certDer)
    /** impressão digital SHA-256 do certificado da CA, para conferir no navegador */
    val sha256: String get() = X509.fingerprint(certDer, "SHA-256")
    val sha1: String get() = X509.fingerprint(certDer, "SHA-1")
    val pem: String get() = X509.pem(certDer)
    /** nome legível da autoridade (como aparece na lista do navegador) */
    val commonName: String get() = Regex("(?:^|,)CN=((?:\\\\.|[^,])+)").find(certificate.subjectX500Principal.getName("RFC2253"))
        ?.groupValues?.get(1)?.replace(Regex("\\\\(.)"), "$1") ?: "Finan+"
}

/** Certificado do servidor (emitido pela CA para o IP atual) e sua chave, só em memória. */
class LanServerCert(val chain: List<X509Certificate>, val keyPair: KeyPair)

/**
 * Gera certificados X.509 v3 com um codificador DER mínimo, sem bibliotecas (o Android não
 * expõe um gerador de certificados, e o Bouncy Castle aumentaria muito o APK).
 *
 * Segurança da CA:
 *  - "Name Constraints" crítico: a CA só vale para IPs de rede privada (10/8, 172.16/12, 192.168/16)
 *    e para nomes ".invalid" (que não existem na internet). Mesmo instalada no navegador, ela não
 *    consegue se passar por nenhum site real (banco, e-mail…).
 *  - pathLen 0: não pode criar outras autoridades.
 *  - o certificado do servidor é novo a cada vez que o servidor liga (chave nova, válido por 30 dias,
 *    só para o IP daquele momento).
 */
object X509 {
    /** Faixas privadas (RFC 1918): endereço + máscara. */
    val PRIVATE_RANGES: List<Pair<String, String>> = listOf(
        "10.0.0.0" to "255.0.0.0",
        "172.16.0.0" to "255.240.0.0",
        "192.168.0.0" to "255.255.0.0",
    )

    private const val CA_NAME = "Finan+ Rede Local (CA deste celular)"
    private const val ORG = "Finan+"

    // ------------------------------------------------------------------ API

    /** Gera um par de chaves EC P-256 em software (certificado do servidor e testes). */
    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }.generateKeyPair()

    /** Função de assinatura ECDSA-SHA256 para uma chave privada (de software ou do Android Keystore). */
    fun signer(key: java.security.PrivateKey): (ByteArray) -> ByteArray = { data ->
        Signature.getInstance("SHA256withECDSA").run { initSign(key); update(data); sign() }
    }

    /**
     * Cria o certificado autoassinado da CA.
     * [ranges] existe só para os testes (que rodam em 127.0.0.1); o app usa sempre [PRIVATE_RANGES].
     */
    fun createCa(publicKey: PublicKey, sign: (ByteArray) -> ByteArray, deviceLabel: String = "", ranges: List<Pair<String, String>> = PRIVATE_RANGES, years: Long = 10): LanCaKey {
        val name = dn(if (deviceLabel.isBlank()) CA_NAME else "$CA_NAME · $deviceLabel".take(64), ORG)
        val now = ZonedDateTime.now(ZoneOffset.UTC).withNano(0)
        val kid = keyId(publicKey)
        val ext = listOf(
            extension(OID_BASIC_CONSTRAINTS, true, seq(bool(true), int(BigInteger.ZERO))),
            extension(OID_KEY_USAGE, true, keyUsage(keyCertSign = true, crlSign = true)),
            extension(OID_SKI, false, octets(kid)),
            extension(OID_NAME_CONSTRAINTS, true, nameConstraints(ranges)),
        )
        val tbs = tbs(serial(), name, now.minusHours(1), now.plusYears(years), name, publicKey.encoded, ext)
        val der = signed(tbs, sign)
        return LanCaKey(der, publicKey, sign)
    }

    /** Emite o certificado do servidor para [ip], assinado pela [ca]. */
    fun issueServer(ca: LanCaKey, ip: InetAddress, days: Long = 30, extraDns: String? = null): LanServerCert {
        val kp = newKeyPair()
        val now = ZonedDateTime.now(ZoneOffset.UTC).withNano(0)
        val names = ArrayList<ByteArray>()
        names += implicitPrim(7, ip.address) // iPAddress
        if (extraDns != null) names += implicitPrim(2, extraDns.toByteArray(Charsets.US_ASCII)) // só nos testes
        val ext = listOf(
            extension(OID_BASIC_CONSTRAINTS, true, seq()),
            extension(OID_KEY_USAGE, true, keyUsage(digitalSignature = true)),
            extension(OID_EXT_KEY_USAGE, false, seq(oid(OID_SERVER_AUTH))),
            extension(OID_SAN, true, seq(*names.toTypedArray())), // subject vazio → SAN crítico (RFC 5280)
            extension(OID_SKI, false, octets(keyId(kp.public))),
            extension(OID_AKI, false, seq(implicitPrim(0, ca.keyId))),
        )
        val issuer = ca.certificate.subjectX500Principal.encoded
        val tbs = tbs(serial(), issuer, now.minusHours(1), now.plusDays(days), seq(), kp.public.encoded, ext)
        val der = signed(tbs, ca::signTbs)
        return LanServerCert(listOf(parse(der), ca.certificate), kp)
    }

    fun parse(der: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate

    fun fingerprint(der: ByteArray, alg: String): String =
        MessageDigest.getInstance(alg).digest(der).joinToString(":") { "%02X".format(it) }

    fun pem(der: ByteArray): String =
        "-----BEGIN CERTIFICATE-----\n" + java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END CERTIFICATE-----\n"

    /** Identificador da chave (SHA-1 da chave pública codificada), usado em SKI/AKI. */
    internal fun keyId(pk: PublicKey): ByteArray = MessageDigest.getInstance("SHA-1").digest(pk.encoded)

    // ------------------------------------------------------------------ estrutura

    private fun tbs(serial: BigInteger, issuer: ByteArray, from: ZonedDateTime, to: ZonedDateTime, subject: ByteArray, spki: ByteArray, ext: List<ByteArray>) = seq(
        explicit(0, int(BigInteger.valueOf(2))), // v3
        int(serial),
        seq(oid(OID_ECDSA_SHA256)),
        issuer,
        seq(time(from), time(to)),
        subject,
        spki,
        explicit(3, seq(*ext.toTypedArray())),
    )

    private fun signed(tbs: ByteArray, sign: (ByteArray) -> ByteArray): ByteArray =
        seq(tbs, seq(oid(OID_ECDSA_SHA256)), bitString(sign(tbs)))

    private fun dn(cn: String, o: String) = seq(
        set(seq(oid(OID_O), utf8(o))),
        set(seq(oid(OID_CN), utf8(cn))),
    )

    private fun extension(id: String, critical: Boolean, value: ByteArray) =
        if (critical) seq(oid(id), bool(true), octets(value)) else seq(oid(id), octets(value))

    private fun keyUsage(digitalSignature: Boolean = false, keyCertSign: Boolean = false, crlSign: Boolean = false): ByteArray {
        // bits: 0 digitalSignature, 5 keyCertSign, 6 cRLSign (do mais significativo para o menos)
        var b = 0
        if (digitalSignature) b = b or 0x80
        if (keyCertSign) b = b or 0x04
        if (crlSign) b = b or 0x02
        val unused = Integer.numberOfTrailingZeros(b).coerceAtMost(7)
        return tlv(0x03, byteArrayOf(unused.toByte(), b.toByte()))
    }

    /** permittedSubtrees: as faixas de IP + dNSName "invalid" (bloqueia qualquer nome DNS real). */
    private fun nameConstraints(ranges: List<Pair<String, String>>): ByteArray {
        val subtrees = ranges.map { (addr, mask) ->
            seq(implicitPrim(7, InetAddress.getByName(addr).address + InetAddress.getByName(mask).address))
        } + seq(implicitPrim(2, "invalid".toByteArray(Charsets.US_ASCII)))
        return seq(implicitCons(0, subtrees.fold(ByteArray(0)) { a, b -> a + b }))
    }

    private val rnd = SecureRandom()
    private fun serial(): BigInteger = BigInteger(1, ByteArray(16).also(rnd::nextBytes)).setBit(126).clearBit(127)

    // ------------------------------------------------------------------ DER

    private fun len(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }

    private fun tlv(tag: Int, v: ByteArray): ByteArray = ByteArrayOutputStream().apply { write(tag); write(len(v.size)); write(v) }.toByteArray()
    private fun cat(parts: Array<out ByteArray>) = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
    private fun seq(vararg parts: ByteArray) = tlv(0x30, cat(parts))
    private fun set(vararg parts: ByteArray) = tlv(0x31, cat(parts))
    private fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
    private fun bool(v: Boolean) = tlv(0x01, byteArrayOf(if (v) 0xFF.toByte() else 0))
    private fun octets(v: ByteArray) = tlv(0x04, v)
    private fun bitString(v: ByteArray) = tlv(0x03, byteArrayOf(0) + v)
    private fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun explicit(n: Int, v: ByteArray) = tlv(0xA0 or n, v)
    private fun implicitPrim(n: Int, v: ByteArray) = tlv(0x80 or n, v)
    private fun implicitCons(n: Int, v: ByteArray) = tlv(0xA0 or n, v)

    private fun time(t: ZonedDateTime): ByteArray =
        if (t.year < 2050) tlv(0x17, t.format(DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")).toByteArray(Charsets.US_ASCII))
        else tlv(0x18, t.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")).toByteArray(Charsets.US_ASCII))

    private fun oid(dotted: String): ByteArray {
        val p = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((p[0] * 40 + p[1]).toInt())
        for (v in p.drop(2)) {
            val stack = ArrayList<Int>()
            var x = v
            stack += (x and 0x7F).toInt()
            x = x shr 7
            while (x > 0) { stack += ((x and 0x7F) or 0x80).toInt(); x = x shr 7 }
            for (b in stack.asReversed()) out.write(b)
        }
        return tlv(0x06, out.toByteArray())
    }

    private const val OID_ECDSA_SHA256 = "1.2.840.10045.4.3.2"
    private const val OID_CN = "2.5.4.3"
    private const val OID_O = "2.5.4.10"
    private const val OID_BASIC_CONSTRAINTS = "2.5.29.19"
    private const val OID_KEY_USAGE = "2.5.29.15"
    private const val OID_EXT_KEY_USAGE = "2.5.29.37"
    private const val OID_SERVER_AUTH = "1.3.6.1.5.5.7.3.1"
    private const val OID_SAN = "2.5.29.17"
    private const val OID_SKI = "2.5.29.14"
    private const val OID_AKI = "2.5.29.35"
    private const val OID_NAME_CONSTRAINTS = "2.5.29.30"
}
