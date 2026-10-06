// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import com.finanplus.core.assist.Ask
import com.finanplus.core.assist.Br
import com.finanplus.core.report.Reports
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes das correções da auditoria de 06/10/2026 (versão 1.1.1). Cada um reproduz o defeito encontrado. */
private fun d(s: String) = LocalDate.parse(s)
private fun AppState.ok(o: Outcome): AppState = when (o) { is Outcome.Ok -> o.state; is Outcome.Err -> error("esperava Ok: ${o.message}") }

class RegressionTest {
    private val today = d("2026-10-06")

    @Test fun reativarRecorrenciaPausadaNaoGeraMesesParados() {
        val r = Recurring("r1", Kind.EXPENSE, "Academia", 100_00, "Saúde", "main", "", 10, false, d("2026-01-10"), YearMonth.of(2026, 3))
        val s = AppState(recurring = listOf(r))
        val after = s.ok(Ops.saveRecurring(s, "r1", Kind.EXPENSE, "Academia", "100", "10", "Saúde", "main", "", true, null, today))
        assertEquals(1, after.txs.size, "só o mês atual")
        assertEquals(d("2026-10-10"), after.txs[0].date)
        assertEquals(YearMonth.of(2026, 10), after.recurring[0].last)
        // editar sem reativar não mexe no "last"
        val s2 = AppState(recurring = listOf(r.copy(active = true, last = YearMonth.of(2026, 10))))
        val e = s2.ok(Ops.saveRecurring(s2, "r1", Kind.EXPENSE, "Academia 2", "100", "10", "Saúde", "main", "", true, null, today))
        assertEquals(YearMonth.of(2026, 10), e.recurring[0].last)
        assertEquals(0, e.txs.size)
    }

    @Test fun pagamentoDeFaturaNaoAlternaPago() {
        val pay = Tx("p", Kind.EXPENSE, 300_00, d("2026-10-05"), "Pagamento fatura Nu", AppState.CARD_PAYMENT_CAT, true, "main", cardPayment = "c1")
        val s = AppState(cards = listOf(Card("c1", "Nu", 1000_00, 5, 12)), txs = listOf(pay))
        assertFalse(Ops.canTogglePaid(pay))
        assertTrue(Ops.togglePaid(s, "p").txs[0].paid)
        val normal = Tx("n", Kind.EXPENSE, 10_00, d("2026-10-05"), "Luz", "Moradia", true, "main")
        assertFalse(Ops.togglePaid(s.copy(txs = listOf(normal)), "n").txs[0].paid)
    }

    @Test fun backupRecusaValoresGigantesEBooleanos() {
        val json = """{"txs":[{"id":"a","kind":"income","value":1e300,"date":"2026-10-01"},
            {"id":"b","kind":"expense","value":true,"date":"2026-10-01"},
            {"id":"c","kind":"expense","value":"12.5","date":"2026-10-01"}],
            "accounts":[{"id":"main","name":"Conta","initial":-1e300}]}"""
        val n = Backup.parse(json)
        assertEquals(listOf("c"), n.state.txs.map { it.id })
        assertEquals(2, n.dropped.txs)
        assertEquals(0L, n.state.accounts[0].initial)
        assertEquals(1250L, n.state.txs[0].value)
        assertNull(Money.fromReaisOrNull(1e14)); assertEquals(Money.MAX_ABS, Money.fromReaisOrNull(9_999_999_999_999.99))
    }

    @Test fun backupComBomEVersaoNova() {
        val n = Backup.parse("﻿{\"txs\":[],\"backupVersion\":6}")
        assertEquals(6, n.version); assertTrue(n.newer)
        assertFalse(Backup.parse(Backup.toJson(AppState())).newer)
    }

    @Test fun idsInvalidosLevamAsReferencias() {
        val longId = "a".repeat(49)
        val json = """{"txs":[{"id":"t1","kind":"expense","value":10,"date":"2026-10-01","accountId":"$longId"},
            {"id":"t2","kind":"expense","value":50,"date":"2026-10-01","accountId":"main","cardId":"x"}],
            "accounts":[{"id":"main","name":"Principal"},{"id":"$longId","name":"Poupança"}],
            "cards":[{"id":"x","name":"Nu","close":5,"due":12}]}"""
        val s = Backup.parse(json).state
        val poup = s.accounts.first { it.name == "Poupança" }.id
        assertEquals(poup, s.txs.first { it.id == "t1" }.accountId)
        assertEquals(s.cards[0].id, s.txs.first { it.id == "t2" }.cardId, "compra continua no cartão")
        // cartão com o mesmo id de uma conta
        val json2 = """{"txs":[{"id":"t","kind":"expense","value":50,"date":"2026-10-01","accountId":"main","cardId":"main"}],
            "accounts":[{"id":"main","name":"Principal"}],"cards":[{"id":"main","name":"Nu","close":5,"due":12}]}"""
        val s2 = Backup.parse(json2).state
        assertEquals(s2.cards[0].id, s2.txs[0].cardId)
        assertEquals("main", s2.txs[0].accountId)
    }

    @Test fun descricoesLongasSobrevivemARreleitura() {
        val s = AppState()
        val long = "x".repeat(199)
        val st = s.ok(Ops.saveTx(s, null, TxDraft(Kind.EXPENSE, long, "100", "Outros", today, true, "main", reps = 10)))
        assertTrue(st.txs.all { it.desc.length <= Ops.TX_DESC_MAX })
        assertTrue(st.txs[9].desc.endsWith("(10/10)"))
        val rec = s.ok(Ops.saveTx(s, null, TxDraft(Kind.EXPENSE, "y".repeat(200), "100", "Outros", today, true, "main", recurring = true)))
        assertEquals(Ops.REC_DESC_MAX, rec.recurring[0].desc.length)
        val again = Backup.parse(Backup.toJson(st)).state
        assertEquals(st.txs.map { it.desc }, again.txs.map { it.desc })
    }

    @Test fun dinheiroCasosDaAuditoria() {
        assertEquals(50L, Money.parse("0.500"))
        assertNull(Money.parse("١٢٣"))
        assertEquals(1000L, Money.parse("r$ 10"))
        assertEquals(100000L, Money.parse("1.000"))
        assertEquals(123456L, Money.parse("R$1.234,56"))
    }

    @Test fun perguntasComNumeroPorExtenso() {
        val money: com.finanplus.core.assist.MoneyFmt = { Money.format(it) }
        val s = AppState()
        val p = Ask.answer("quanto gastei nos ultimos dez dias", s, today, money).parsed.period
        assertEquals(d("2026-09-27"), p.from); assertEquals(today, p.to)
        val w = Ask.answer("gastos das últimas 2 semanas", s, today, money).parsed.period
        assertEquals(d("2026-09-23"), w.from)
        val dez = Ask.answer("quanto gastei em dez", s, today, money).parsed.period
        assertEquals(d("2025-12-01"), dez.from)
    }

    @Test fun datasNaoDependemDoIdiomaDoAparelho() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("06/10/2026", Br.date(today))
        } finally { Locale.setDefault(old) }
    }

    @Test fun eixoArredondaNaFronteira() {
        assertEquals("R$ 1 mil", Reports.compact(999_60))
        assertEquals("R$ 1 mi", Reports.compact(999_999_96))
        assertEquals("R$ 999", Reports.compact(999_40))
    }

    @Test fun passoDoEixoNuncaZero() {
        for (c in 0L..499L) assertTrue(Reports.niceStep(c) >= 100, "max=$c")
    }
}
