// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Radar, "Posso comprar?" e alternativas, com o cenário de RADAR.md:
 * R$ 4.200 hoje (18/10), internet, aluguel e luz a pagar, fatura de R$ 1.440 em 3/11 e salário de R$ 3.500 em 5/11.
 */
class RadarTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-10-18")

    private fun tx(id: String, kind: Kind, v: Long, date: String, paid: Boolean = false, card: String = "") =
        Tx(id, kind, v, d(date), id, "Outros", paid || card.isNotEmpty(), "main", card)

    private val base = AppState(
        accounts = listOf(Account("main", "Conta principal", 420000)),
        cards = listOf(Card("azul", "Azul", 500000, 25, 3)),
        txs = listOf(
            tx("Internet", Kind.EXPENSE, 12000, "2026-10-20"),
            tx("Aluguel", Kind.EXPENSE, 160000, "2026-10-25"),
            tx("Luz", Kind.EXPENSE, 30000, "2026-10-30"),
            tx("Mercado no cartão", Kind.EXPENSE, 144000, "2026-10-10", card = "azul"),
        ),
        recurring = listOf(Recurring("sal", Kind.INCOME, "Salário", 350000, "Salário", "main", "", 5, true, d("2026-01-05"), YearMonth.of(2026, 10))),
        reserve = 70000,
    )

    @Test fun serieDiariaBateComOSaldoPrevisto() {
        val r = Radar.run(base, today)
        assertEquals(31, r.days.size) // hoje + 30 dias
        assertEquals(420000L, r.days[0].balance)
        assertEquals(Finance.futureBalance(base, today.plusDays(30), today), r.end.balance)
        // cada dia é igual ao saldo previsto naquela data
        for (day in r.days) assertEquals(Finance.futureBalance(base, day.date, today), day.balance, "dia ${day.date}")
        assertEquals(d("2026-11-03"), r.min.date)
        assertEquals(74000L, r.min.low)
        assertEquals(Radar.Level.OK, r.level)
        assertEquals(4000L, r.margin)
        assertNull(r.firstNegative)
        assertTrue(r.hasIncome)
        val fatura = r.events.single { it.source == Radar.Source.INVOICE }
        assertEquals(d("2026-11-03"), fatura.date); assertEquals(-144000L, fatura.delta); assertEquals("Fatura Azul", fatura.desc)
        assertEquals(Radar.Source.RECURRING, r.events.single { it.delta > 0 }.source)
    }

    @Test fun reservaMaiorViraAtencao() {
        val r = Radar.run(base, today, reserve = 80000)
        assertEquals(Radar.Level.LOW, r.level)
        assertEquals(d("2026-11-03"), r.firstBelowReserve?.date)
        assertEquals(Radar.Level.OK, Radar.run(base, today, reserve = 0).level)
    }

    @Test fun pendenteAtrasadoEntraHoje() {
        val s = base.copy(txs = base.txs + tx("Água atrasada", Kind.EXPENSE, 5000, "2026-10-10"))
        val r = Radar.run(s, today)
        val e = r.events.single { it.desc == "Água atrasada" }
        assertEquals(today, e.date); assertEquals(Radar.Source.OVERDUE, e.source)
        assertEquals(415000L, r.days[0].balance)
        assertEquals(Finance.futureBalance(s, today.plusDays(30), today), r.end.balance)
    }

    @Test fun compraAVistaMostraRiscoEExplicaPorQue() {
        val c = Radar.check(base, today, Radar.Purchase(90000, today, "main"))
        assertEquals(Radar.Level.OK, c.base.level)
        assertEquals(Radar.Level.NEGATIVE, c.sim.level)
        assertEquals(d("2026-11-03"), c.sim.firstNegative?.date)
        assertEquals(-16000L, c.sim.min.low)
        assertEquals(-90000L, c.impact)
        assertFalse(c.noIncome)
        // nada foi gravado
        assertEquals(4, base.txs.size)
        val why = Radar.causes(c.sim)
        assertTrue(why.any { it.desc == "Fatura Azul" } && why.any { it.desc == "Aluguel" })
        assertEquals("Salário", why.last().desc) // a primeira entrada depois do aperto
    }

    @Test fun compraNoCartaoCaiNaFaturaCerta() {
        // dia 26: depois do fechamento (25), vai para a fatura de novembro, que vence em 3/12
        val c = Radar.check(base, today, Radar.Purchase(90000, d("2026-10-26"), cardId = "azul"))
        assertEquals(d("2026-12-03"), c.sim.events.single { it.source == Radar.Source.SIMULATED }.date)
        assertTrue(c.sim.days.size > 31) // olha até depois da fatura
        assertEquals(Radar.Level.OK, c.sim.level)
        // parcelada em 3: dez, jan e fev
        val p = Radar.purchaseEvents(base, Radar.Purchase(90000, d("2026-10-26"), cardId = "azul", parcels = 3))
        assertEquals(listOf(d("2026-12-03"), d("2027-01-03"), d("2027-02-03")), p.map { it.date })
        assertEquals(90000L, p.sumOf { -it.delta })
    }

    @Test fun saidaDoDiaContaAntesDaEntrada() {
        // comprar no dia do salário ainda deixa a manhã negativa
        val noSalario = Radar.check(base, today, Radar.Purchase(90000, d("2026-11-05"), "main"))
        assertEquals(Radar.Level.NEGATIVE, noSalario.sim.level)
        assertEquals(d("2026-11-05"), noSalario.sim.firstNegative?.date)
    }

    @Test fun alternativasAdiarParcelarEMeta() {
        val s = base.copy(goals = listOf(Goal("viagem", "Viagem", 600000, 50000, null, 0)))
        val alts = Radar.alternatives(s, today, Radar.Purchase(90000, today, "main"))
        val adiar = alts.single { it.kind == Radar.AltKind.POSTPONE }
        assertEquals(d("2026-11-06"), adiar.purchase.date) // depois do salário
        assertEquals(Radar.Level.OK, adiar.result.level)
        val parcelar = alts.single { it.kind == Radar.AltKind.PARCELS }
        assertEquals("azul", parcelar.purchase.cardId)
        assertEquals(2, parcelar.purchase.parcels)
        assertEquals(Radar.Level.LOW, parcelar.result.level) // não fica negativo, mas fica abaixo da reserva
        val meta = alts.single { it.kind == Radar.AltKind.GOAL }
        assertEquals("viagem", meta.goalId); assertEquals(50000L, meta.amount)
        assertEquals(34000L, meta.result.min.low)
        // compra que cabe: nenhuma alternativa
        assertTrue(Radar.alternatives(s, today, Radar.Purchase(1000, today, "main")).isEmpty())
    }

    @Test fun semReceitaPrevistaAvisa() {
        val s = base.copy(recurring = emptyList())
        val c = Radar.check(s, today, Radar.Purchase(1000, today, "main"))
        assertTrue(c.noIncome)
        assertNotNull(c.sim.min)
    }

    @Test fun reservaNoBackup() {
        val json = Backup.toJson(base)
        assertTrue("\"reserve\":700.00" in json)
        assertEquals(70000L, Backup.parse(json).state.reserve)
        // sem reserva: o campo nem aparece (backups antigos continuam idênticos) e a leitura dá 0
        val none = Backup.toJson(base.copy(reserve = 0))
        assertFalse("reserve" in none)
        assertEquals(0L, Backup.parse(none).state.reserve)
        assertEquals(0L, Backup.parse(none.replace("\"theme\"", "\"reserve\":-5,\"theme\"")).state.reserve)
    }
}
