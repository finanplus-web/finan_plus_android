// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import com.finanplus.core.Json
import com.finanplus.core.Outcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modo remoto do PWA: ler e gravar o estado inteiro com versão. */
class LanRemoteTest {
    private val f = LanFixture()
    @After fun stop() = f.close()

    @Suppress("UNCHECKED_CAST")
    private fun number(value: Any?) = when (value) {
        is Json.NumberToken -> value.text.toDouble()
        is Number -> value.toDouble()
        else -> error("número JSON esperado")
    }

    private fun read(t: String): Pair<Long, Map<String, Any?>> {
        val st = f.api("/api/remote/state", token = t).json
        return number(st["rev"]).toLong() to (st["data"] as Map<String, Any?>)
    }

    private fun withTx(data: Map<String, Any?>, id: String, desc: String): String {
        val tx = """{"id":"$id","kind":"expense","value":12.5,"date":"2026-10-07","desc":"$desc","category":"Alimentação","paid":true,"accountId":"main","cardId":""}"""
        val json = Json.stringify(data)
        return if ((data["txs"] as List<*>).isEmpty()) json.replaceFirst("\"txs\":[]", "\"txs\":[$tx]") else json.replaceFirst("\"txs\":[", "\"txs\":[$tx,")
    }

    @Test fun leEGravaComVersao() {
        val t = f.pair()
        val (rev, data) = read(t)
        val ok = f.api("/api/remote/state", "PUT", """{"rev":$rev,"data":${withTx(data, "a1", "Feira")}}""", t)
        assertEquals(200, ok.status)
        val rev2 = number(ok.json["rev"]).toLong()
        assertTrue(rev2 > rev)
        assertEquals(1250L, f.backend.state.txs.single().value)
        assertEquals(rev2, read(t).first) // a versão devolvida é a do estado gravado
    }

    @Test fun versaoVelhaEConflitoENadaEGravado() {
        val t = f.pair()
        val (rev, data) = read(t)
        f.backend.apply { s -> Outcome.Ok(s.copy(privacy = true)) } // alteração no celular
        val r = f.api("/api/remote/state", "PUT", """{"rev":$rev,"data":${withTx(data, "a1", "Feira")}}""", t)
        assertEquals(409, r.status)
        assertEquals(true, r.json["conflict"])
        assertTrue(f.backend.state.txs.isEmpty())
        assertTrue(f.backend.state.privacy)
    }

    @Test fun itemInvalidoRecusaTudo() {
        val t = f.pair()
        val (rev, _) = read(t)
        val r = f.api("/api/remote/state", "PUT", """{"rev":$rev,"data":{"txs":[{"id":"x","kind":"???","value":"abc"}]}}""", t)
        assertEquals(422, r.status)
        assertTrue(f.backend.state.txs.isEmpty())
    }

    @Test fun acimaDoLimiteEditaMasNaoCresce() {
        val many = (1..com.finanplus.core.AppState.Limits.ACCOUNTS + 2).map { com.finanplus.core.Account("a$it", "Conta $it", 0) }
        f.backend.apply { s -> Outcome.Ok(s.copy(accounts = many)) }
        val t = f.pair()
        val (rev, data) = read(t)
        // editar (renomear uma conta) passa, mesmo com mais contas que o limite
        val renamed = Json.stringify(data).replaceFirst("\"Conta 1\"", "\"Conta renomeada\"")
        val ok = f.api("/api/remote/state", "PUT", """{"rev":$rev,"data":$renamed}""", t)
        assertEquals(200, ok.status)
        assertEquals("Conta renomeada", f.backend.state.account("a1")?.name)
        // criar mais uma conta é recusado
        val (rev2, data2) = read(t)
        val more = Json.stringify(data2).replaceFirst("\"accounts\":[", "\"accounts\":[{\"id\":\"novo\",\"name\":\"Nova\",\"initial\":0},")
        val r = f.api("/api/remote/state", "PUT", """{"rev":$rev2,"data":$more}""", t)
        assertEquals(422, r.status)
        assertEquals(com.finanplus.core.AppState.Limits.ACCOUNTS + 2, f.backend.state.accounts.size)
    }

    @Test fun entradaInvalida() {
        val t = f.pair()
        assertEquals(400, f.api("/api/remote/state", "PUT", """{"data":{}}""", t).status)
        assertEquals(400, f.api("/api/remote/state", "PUT", """{"rev":-1,"data":{}}""", t).status)
        assertEquals(400, f.api("/api/remote/state", "PUT", """{"rev":1.5,"data":{}}""", t).status)
        assertEquals(400, f.api("/api/remote/state", "PUT", """{"rev":1,"data":[]}""", t).status)
        assertEquals(404, f.api("/api/tx", "POST", "{}", t).status) // a API antiga não existe mais
    }

    @Test fun celularSemConseguirSalvarRecusaGravacao() {
        val t = f.pair()
        val (rev, data) = read(t)
        f.backend.blocked = "Sem espaço no celular."
        assertEquals(503, f.api("/api/remote/state", "PUT", """{"rev":$rev,"data":${withTx(data, "a1", "Feira")}}""", t).status)
        assertTrue(f.backend.state.txs.isEmpty())
    }

    @Test fun mudancaNoCelularOuNoTemaMudaARevisao() {
        val t = f.pair()
        val r1 = f.api("/api/rev", token = t).json
        assertEquals(r1["rev"], f.api("/api/rev", token = t).json["rev"])
        f.backend.themeId = "nord"
        val r2 = f.api("/api/rev", token = t).json
        assertTrue(r1["rev"] != r2["rev"])
        assertEquals(r1["data"], r2["data"]) // só as cores mudaram
        f.backend.apply { s -> Outcome.Ok(s.copy(privacy = !s.privacy)) }
        assertTrue(number(f.api("/api/rev", token = t).json["data"]) > number(r2["data"]))
    }

    @Test fun desconectarInvalidaOToken() {
        val t = f.pair()
        assertEquals(200, f.api("/api/logout", "POST", "", t).status)
        assertEquals(401, f.api("/api/remote/state", token = t).status)
    }

    @Test fun consultaDeMudancasNaoMantemOServidorLigado() {
        var offset = 0L
        val ev = java.util.concurrent.CopyOnWriteArrayList<LanEvent>()
        val cfg = LanConfig(httpPort = LanFixture.nextPort(), httpsPort = LanFixture.nextPort())
        val s = LanServer(f.backend, f.loop, LanTls(f.ca, X509.issueServer(f.ca, f.loop)), cfg, onEvent = { ev += it }, clock = { System.currentTimeMillis() + offset })
        s.start()
        try {
            val r = f.call(s.url + "/api/pair", "POST", """{"code":"${s.code}"}""")
            val id = r.json["pending"] as String
            s.approve(id)
            val t = f.call(s.url + "/api/pair/$id").json["token"] as String
            offset = cfg.idleMillis + 1000 // "11 minutos depois"
            val until = System.currentTimeMillis() + 8000
            while (s.running && System.currentTimeMillis() < until) { runCatching { f.call(s.url + "/api/rev", token = t) }; Thread.sleep(300) }
            assertTrue(ev.any { it is LanEvent.Stopped && it.reason == StopReason.IDLE })
        } finally { s.stop() }
    }
}
