// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException

/** HTTPS, cabeçalhos, validação de entrada, limites e conexões lentas. */
class LanSecurityTest {
    private val f = LanFixture(LanConfig(httpPort = LanFixture.nextPort(), httpsPort = LanFixture.nextPort(),
        workers = 2, queue = 1, readTimeoutMillis = 800, connectionDeadlineMillis = 1500))
    @After fun stop() = f.close()

    @Test fun sohHttpsComCertificadoDaCa() {
        assertTrue(f.server.url.startsWith("https://"))
        assertEquals(200, f.call(f.server.url + "/").status)
        try { f.call(f.server.url + "/", ctx = SSLContext.getDefault()); fail("deveria recusar") } catch (e: SSLHandshakeException) { }
    }

    @Test fun apiExigePareamento() {
        assertEquals(401, f.api("/api/remote/state").status)
        assertEquals(401, f.api("/api/remote/state", token = "f".repeat(64)).status)
        assertEquals(401, f.api("/api/rev", token = "nao-e-token").status)
    }

    @Test fun portaHttpNaoTemDados() {
        val setup = f.server.setupUrl
        val page = f.call("$setup/")
        assertEquals(200, page.status)
        assertTrue(page.body.contains(f.ca.sha256))
        assertTrue(page.body.contains("U=\"${f.server.url}\"")) // URL dentro do <script> como JSON
        assertTrue(page.headers["content-security-policy"]!!.contains("connect-src 'self' ${f.server.url}"))
        assertEquals(403, f.call("$setup/api/remote/state").status)
        assertEquals(403, f.call("$setup/api/pair", "POST", """{"code":"${f.server.code}"}""").status)
        assertEquals(405, f.call("$setup/", "POST", "{}").status)
    }

    @Test fun hostEOrigemDeOutroSite() {
        val t = f.pair()
        assertTrue(f.raw("GET /api/remote/state HTTP/1.1\r\nHost: evil.example\r\nAuthorization: Bearer $t\r\n\r\n").startsWith("HTTP/1.1 403"))
        assertTrue(f.raw("POST /api/logout HTTP/1.1\r\nHost: ${f.server.host}\r\nOrigin: https://evil.example\r\nAuthorization: Bearer $t\r\nContent-Length: 0\r\n\r\n").startsWith("HTTP/1.1 403"))
        assertTrue(f.raw("GET /api/remote/state HTTP/1.1\r\nHost: ${f.server.host}\r\nOrigin: ${f.server.url}\r\nAuthorization: Bearer $t\r\n\r\n").startsWith("HTTP/1.1 200"))
    }

    @Test fun requisicoesMalFormadas() {
        val h = "Host: ${f.server.host}\r\n"
        assertTrue(f.raw("LIXO\r\n\r\n").startsWith("HTTP/1.1 400"))
        assertTrue(f.raw("TRACE / HTTP/1.1\r\n$h\r\n").startsWith("HTTP/1.1 405"))
        assertTrue(f.raw("GET / HTTP/2.0\r\n$h\r\n").startsWith("HTTP/1.1 400"))
        assertTrue(f.raw("GET / HTTP/1.1\r\n${h}Host: outro\r\n\r\n").startsWith("HTTP/1.1 400")) // Host repetido
        assertTrue(f.raw("POST /api/pair HTTP/1.1\r\n${h}Transfer-Encoding: chunked\r\n\r\n").startsWith("HTTP/1.1 411"))
        assertTrue(f.raw("POST /api/pair HTTP/1.1\r\n${h}Content-Length: -1\r\n\r\n").startsWith("HTTP/1.1 400"))
        assertTrue(f.raw("GET / HTTP/1.1\r\n${h}Bad Header: x\r\n\r\n").startsWith("HTTP/1.1 400"))
        assertEquals(400, f.api("/api/pair", "POST", "[1,2]").status)
        assertEquals(400, f.api("/api/pair", "POST", """{"code":"12345"}""").status)
        assertEquals(400, f.api("/api/pair", "POST", """{"code":123456}""").status)
        assertEquals(404, f.api("/api/pair/naoehex").status)
    }

    @Test fun arquivosSoComNomesSimples() {
        val r = f.call(f.server.url + "/")
        assertTrue(r.body.contains(PwaAssets.REMOTE_MARKER))
        assertTrue(r.headers["content-security-policy"]!!.contains("script-src 'self';"))
        listOf("X-Frame-Options", "X-Content-Type-Options", "Referrer-Policy", "Cache-Control").forEach { assertTrue(it, r.headers.containsKey(it.lowercase())) }
        assertEquals(200, f.call(f.server.url + "/js/app.bundle.js").status)
        assertEquals(404, f.call(f.server.url + "/sw.js").status)
        assertTrue(f.raw("GET /js/../../segredo.txt HTTP/1.1\r\nHost: ${f.server.host}\r\n\r\n").startsWith("HTTP/1.1 404"))
        assertTrue(f.raw("GET /%2e%2e/segredo.txt HTTP/1.1\r\nHost: ${f.server.host}\r\n\r\n").startsWith("HTTP/1.1 404"))
        assertTrue(f.raw("GET /js%5capp.bundle.js HTTP/1.1\r\nHost: ${f.server.host}\r\n\r\n").startsWith("HTTP/1.1 404"))
        assertEquals("cross-origin", f.api("/api/ping").headers["cross-origin-resource-policy"])
        assertEquals("same-origin", f.api("/api/pair/" + "0".repeat(32)).headers["cross-origin-resource-policy"])
    }

    @Test fun corpoGrandeSoComToken() {
        val big = "x".repeat(LanConfig().maxBodyBytes + 10)
        val semToken = try { f.api("/api/remote/state", "PUT", """{"rev":1,"data":"$big"}""").status } catch (e: java.io.IOException) { 413 }
        assertEquals(413, semToken) // recusa antes de ler (o cliente pode nem conseguir terminar de enviar)
        val t = f.pair()
        assertEquals(400, f.api("/api/remote/state", "PUT", """{"rev":1,"data":"$big"}""", t).status) // lê e valida
    }

    @Test fun conexoesLentasSaoDerrubadasEOServidorContinua() {
        // mais conexões paradas do que threads + fila: antes travava tudo (e o TLS rodava na thread que aceita)
        val stalled = (1..6).map { Socket(f.loop, f.server.port) }
        Thread.sleep(2600) // passa do prazo por conexão (1,5 s) + folga do vigia
        assertEquals(200, f.call(f.server.url + "/").status)
        // todas foram encerradas pelo servidor (no máximo um alerta TLS antes do fim), nenhuma ficou pendurada
        stalled.forEach { s -> s.soTimeout = 2000; assertTrue(closedByPeer(s)); s.close() }
    }

    private fun closedByPeer(s: Socket): Boolean {
        return try {
            val input = s.getInputStream()
            var n = 0
            while (input.read() != -1) if (++n > 64) return false
            true
        } catch (e: java.net.SocketTimeoutException) { false } catch (e: java.io.IOException) { true }
    }

    @Test fun clienteQueParaNoMeioDoCabecalho() {
        val s = f.trusting.socketFactory.createSocket(f.loop, f.server.port)
        s.getOutputStream().write("GET / HTTP/1.1\r\nHost: ${f.server.host}\r\n".toByteArray()) // nunca termina
        s.soTimeout = 4000
        val t0 = System.currentTimeMillis()
        val r = runCatching { s.getInputStream().read() }
        assertTrue(r.isFailure || r.getOrNull() == -1) // fechado pelo servidor
        assertTrue(System.currentTimeMillis() - t0 < 3500)
        s.close()
    }

    @Test fun naoAceitaDeOutrasInterfaces() {
        assertFalse(f.server.url.contains("0.0.0.0"))
    }
}
