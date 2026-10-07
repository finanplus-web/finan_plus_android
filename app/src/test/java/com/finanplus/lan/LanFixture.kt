// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import com.finanplus.core.AppState
import com.finanplus.core.Json
import com.finanplus.core.Outcome
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Servidor real em 127.0.0.1 com uma CA de teste (a do app só cobre IPs privados; aqui inclui 127/8),
 * um "celular" de mentira e um cliente HTTPS que confia só nessa CA.
 */
class LanFixture(config: LanConfig = LanConfig(httpPort = nextPort(), httpsPort = nextPort())) : Closeable {
    val loop: InetAddress = InetAddress.getLoopbackAddress()
    private val caKeys = X509.newKeyPair()
    val ca = X509.createCa(caKeys.public, X509.signer(caKeys.private), "Teste", X509.PRIVATE_RANGES + ("127.0.0.0" to "255.0.0.0"))
    val events = CopyOnWriteArrayList<LanEvent>()
    val backend = FakePhone()
    val server = LanServer(backend, loop, LanTls(ca, X509.issueServer(ca, loop)), config, onEvent = { events += it })

    init { server.start() }
    override fun close() = server.stop()

    class FakePhone : LanBackend {
        @Volatile var state = AppState()
        @Volatile var themeId = "tokyo"
        @Volatile var blocked: String? = null
        override fun state() = state
        @Synchronized override fun apply(op: (AppState) -> Outcome): Outcome.Err? =
            when (val o = op(state)) { is Outcome.Ok -> { state = o.state; null }; is Outcome.Err -> o }
        override fun writeBlocked() = blocked
        override fun theme() = linkedMapOf<String, Any?>("id" to themeId, "light" to mapOf("bg" to "rgba(26,27,38,1.000)"), "dark" to mapOf("bg" to "rgba(26,27,38,1.000)"))
        override fun asset(name: String): ByteArray? = when (name) {
            "pwa/index.html" -> "<!doctype html><html><head><meta charset=\"utf-8\"></head><body>pwa</body></html>".toByteArray()
            "pwa/js/app.bundle.js" -> "console.log(1)".toByteArray()
            "pwa/sw.js" -> "self".toByteArray()
            "setup.html" -> "<script>T=/*{{THEME}}*/null;U=\"{{HTTPS_URL_JS}}\"</script><p>{{SHA256}}</p><a href=\"{{HTTPS_URL}}\">abrir</a>".toByteArray()
            else -> null
        }
    }

    val trusting: SSLContext by lazy {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("ca", ca.certificate) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
        SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }
    }

    data class Res(val status: Int, val body: String, val headers: Map<String, String> = emptyMap()) {
        @Suppress("UNCHECKED_CAST")
        val json: Map<String, Any?> get() = Json.parse(body) as Map<String, Any?>
    }

    fun call(url: String, method: String = "GET", body: String? = null, token: String? = null, ctx: SSLContext? = trusting): Res {
        val c = URL(url).openConnection() as HttpURLConnection
        if (c is HttpsURLConnection && ctx != null) c.sslSocketFactory = ctx.socketFactory
        c.requestMethod = method
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        val headers = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(",") }
        return Res(code, text, headers)
    }

    fun api(path: String, method: String = "GET", body: String? = null, token: String? = null) = call(server.url + path, method, body, token)

    /** Requisição escrita à mão (para cabeçalhos que o HttpURLConnection não deixa mudar). Devolve a 1ª linha. */
    fun raw(request: String, port: Int = server.port, tls: Boolean = true): String {
        val s: Socket = if (tls) trusting.socketFactory.createSocket(loop, port) else Socket(loop, port)
        return s.use {
            it.soTimeout = 5000
            it.getOutputStream().write(request.toByteArray(Charsets.ISO_8859_1))
            it.getInputStream().bufferedReader(Charsets.ISO_8859_1).readLine() ?: ""
        }
    }

    /** Pareia de verdade: código → pedido → "Permitir" → token. */
    fun pair(): String {
        val r = api("/api/pair", "POST", """{"code":"${server.code}"}""")
        check(r.status == 202) { "pareamento: ${r.status} ${r.body}" }
        val id = r.json["pending"] as String
        check(server.approve(id))
        return api("/api/pair/$id").json["token"] as String
    }

    companion object {
        private val port = java.util.concurrent.atomic.AtomicInteger(19000 + (ProcessHandle.current().pid() % 500).toInt() * 20)
        fun nextPort(): Int = port.getAndAdd(11)
    }
}
