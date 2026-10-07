// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pareamento sem rede: código, bloqueio por endereço, aprovação, expiração. */
class PairingTest {
    private var now = 1_000_000L
    private val cfg = LanConfig()
    private val p = Pairing(cfg, { now })
    private val ev = ArrayList<LanEvent>()
    private fun wrong() = if (p.code == "000000") "111111" else "000000"

    @Test fun codigoCertoCriaPedidoETrocaOCodigo() {
        val code = p.code
        val r = p.attempt("10.0.0.2", code, "Firefox no Linux", ev)
        assertTrue(r is PairAttempt.Pending)
        assertNotEquals(code, p.code)
        assertTrue(ev.any { it is LanEvent.PairRequested } && ev.any { it is LanEvent.CodeChanged })
        assertEquals(PairAttempt.WrongCode, p.attempt("10.0.0.2", code, "x", ev)) // o código visto não serve de novo
    }

    @Test fun bloqueioEPorEnderecoENaoTravaOutros() {
        repeat(cfg.maxFailsPerAddress - 1) { assertEquals(PairAttempt.WrongCode, p.attempt("10.0.0.66", wrong(), "x", ev)) }
        assertTrue(p.attempt("10.0.0.66", wrong(), "x", ev) is PairAttempt.Locked)
        assertTrue(p.attempt("10.0.0.66", p.code, "x", ev) is PairAttempt.Locked) // nem o certo, durante a espera
        // outro aparelho continua conseguindo parear (antes o bloqueio era global)
        assertTrue(p.attempt("10.0.0.2", p.code, "x", ev) is PairAttempt.Pending)
        now += cfg.lockoutMillis + 1
        assertEquals(PairAttempt.WrongCode, p.attempt("10.0.0.66", wrong(), "x", ev))
    }

    @Test fun tokenSoDepoisDePermitirEUmaVez() {
        val req = (p.attempt("10.0.0.2", p.code, "x", ev) as PairAttempt.Pending).request
        assertEquals(PairStatus.Waiting, p.status(req.id))
        assertTrue(p.approve(req.id, ev))
        assertFalse(p.approve(req.id, ev))
        val token = (p.status(req.id) as PairStatus.Approved).token
        assertTrue(Pairing.TOKEN_FORMAT.matches(token))
        assertEquals(PairStatus.Expired, p.status(req.id)) // entregue uma única vez
        assertTrue(p.authenticate(token))
        assertFalse(p.authenticate("x".repeat(64)))
        assertFalse(p.authenticate(null))
    }

    @Test fun recusado() {
        val req = (p.attempt("10.0.0.2", p.code, "x", ev) as PairAttempt.Pending).request
        assertTrue(p.deny(req.id, ev))
        assertFalse(p.approve(req.id, ev))
        assertEquals(PairStatus.Denied, p.status(req.id))
        assertEquals(0, p.devices().size)
    }

    @Test fun aprovadoNuncaBuscadoEAnulado() {
        val req = (p.attempt("10.0.0.2", p.code, "x", ev) as PairAttempt.Pending).request
        p.approve(req.id, ev)
        assertEquals(1, p.devices().size)
        now += cfg.pairTimeoutMillis + 1
        p.expire(ev)
        assertEquals(0, p.devices().size)
        assertEquals(PairStatus.Expired, p.status(req.id))
    }

    @Test fun pedidoSemRespostaExpira() {
        val req = (p.attempt("10.0.0.2", p.code, "x", ev) as PairAttempt.Pending).request
        ev.clear()
        now += cfg.pairTimeoutMillis + 1
        p.expire(ev)
        assertTrue(ev.contains(LanEvent.PairClosed(req.id)))
        assertTrue(p.pendingRequests().isEmpty())
    }

    @Test fun limitesDePedidosEAparelhos() {
        repeat(cfg.maxPending) { assertTrue(p.attempt("10.0.0.$it", p.code, "x", ev) is PairAttempt.Pending) }
        assertEquals(PairAttempt.Busy, p.attempt("10.0.0.9", p.code, "x", ev))
        p.pendingRequests().forEach { p.approve(it.id, ev) }
        val small = Pairing(LanConfig(maxDevices = 1), { now })
        val r = (small.attempt("10.0.0.2", small.code, "x", ev) as PairAttempt.Pending).request
        small.approve(r.id, ev)
        assertEquals(PairAttempt.DeviceLimit, small.attempt("10.0.0.3", small.code, "x", ev))
    }

    @Test fun desconectarPeloCelular() {
        val req = (p.attempt("10.0.0.2", p.code, "x", ev) as PairAttempt.Pending).request
        p.approve(req.id, ev)
        val token = (p.status(req.id) as PairStatus.Approved).token
        assertFalse(p.revoke("zz", ev)) // formato inválido
        assertTrue(p.revoke(p.devices()[0].id, ev))
        assertFalse(p.authenticate(token))
    }

    @Test fun rotulosDeNavegador() {
        assertEquals("Firefox no Linux", UserAgent.label("Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"))
        assertEquals("Chrome no Windows", UserAgent.label("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"))
        assertEquals("Edge no Windows", UserAgent.label("Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130.0 Safari/537.36 Edg/130.0"))
        assertEquals("Navegador", UserAgent.label("<script>alert(1)</script>")) // nunca o texto do navegador
    }
}
