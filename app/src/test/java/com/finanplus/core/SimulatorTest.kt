// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun d(s: String) = LocalDate.parse(s)
private fun t(id: String, kind: Kind, v: Long, date: String, paid: Boolean = true, card: String = "", group: String = "", n: Int = 0, total: Int = 0, desc: String = id) =
    Tx(id, kind, v, d(date), desc, "Outros", paid, "main", card, groupId = group, parcelN = n, parcelTotal = total)

class SimulatorTest {
    private val today = d("2026-10-09")

    @Test fun baseIsTheAverageOfTheLastMonthsWithData() {
        val s = AppState(txs = listOf(
            t("a", Kind.INCOME, 500000, "2026-09-05"), t("b", Kind.EXPENSE, 300000, "2026-09-10"),
            t("c", Kind.INCOME, 400000, "2026-08-05"), t("d", Kind.EXPENSE, 200000, "2026-08-10"),
            t("e", Kind.EXPENSE, 99999, "2026-10-02"),               // mês atual: fica de fora
            t("f", Kind.EXPENSE, 50000, "2026-09-20", paid = false), // pendente: fica de fora
        ))
        val b = Simulator.base(s, today)                             // julho sem dados: média de 2 meses
        assertEquals(Simulator.Base(450000, 250000, 2), b)
        assertEquals(200000, b.left)
        assertEquals(Simulator.Base(0, 0, 0), Simulator.base(AppState(), today))
    }

    @Test fun saveAndBuy() {
        val base = Simulator.Base(211362, 113240, 3)                 // sobra R$ 981,22
        val sv = Simulator.save(base, 20000, 12)
        assertEquals(240000, sv.total); assertEquals(78122, sv.newLeft); assertEquals(false, sv.overLeft)
        assertTrue(Simulator.save(base, 100000, 12).overLeft)
        // computador de R$ 4.500 guardando R$ 300: 15 meses, a partir de novembro → janeiro de 2028
        val b = Simulator.buy(450000, 0, 30000, today)!!
        assertEquals(15, b.months); assertEquals(YearMonth.of(2028, 1), b.doneYm)
        assertEquals("janeiro de 2028", Simulator.monthYear(b.doneYm))
        assertEquals(9, Simulator.buy(450000, 0, 50000, today)!!.months)   // julho de 2027
        assertEquals(23, Simulator.buy(450000, 0, 20000, today)!!.months)  // arredonda para cima
        assertEquals(0, Simulator.buy(450000, 500000, 0, today)!!.months)  // já tem o dinheiro
        assertNull(Simulator.buy(450000, 0, 0, today))
    }

    @Test fun incomeChange() {
        val base = Simulator.Base(500000, 400000, 3)
        val r = Simulator.income(base, -15.0, 60000)
        assertEquals(425000, r.newIncome); assertEquals(-75000, r.diff); assertEquals(25000, r.newLeft); assertEquals(-900000, r.yearDiff)
        assertEquals(60000, r.goalsMonthly)
        val s = AppState(goals = listOf(Goal("g1", "Viagem", 800000, 310000, null, 60000), Goal("g2", "Pronta", 1000, 1000, null, 5000)))
        assertEquals(60000, Simulator.goalsMonthly(s))               // meta concluída não pede mais nada
    }

    @Test fun debtsAndPayoff() {
        val s = AppState(txs = listOf(
            t("p1", Kind.EXPENSE, 12140, "2026-09-15", paid = true, group = "g", n = 1, total = 6, desc = "Mercado Pago 1/6"),
            t("p2", Kind.EXPENSE, 12140, "2026-10-15", paid = false, group = "g", n = 2, total = 6, desc = "Mercado Pago 2/6"),
            t("p3", Kind.EXPENSE, 12140, "2026-11-15", paid = false, group = "g", n = 3, total = 6, desc = "Mercado Pago 3/6"),
            t("c1", Kind.EXPENSE, 60000, "2026-09-02", card = "nu", group = "h", n = 1, total = 3, desc = "Notebook (1/3)"),
            t("c2", Kind.EXPENSE, 60000, "2026-10-02", card = "nu", group = "h", n = 2, total = 3, desc = "Notebook (2/3)"),
            t("c3", Kind.EXPENSE, 60000, "2026-11-02", card = "nu", group = "h", n = 3, total = 3, desc = "Notebook (3/3)"),
            t("q1", Kind.EXPENSE, 1000, "2026-09-01", paid = true, group = "z", n = 1, total = 2), t("q2", Kind.EXPENSE, 1000, "2026-09-30", paid = true, group = "z", n = 2, total = 2),
        ))
        val l = Simulator.debts(s, today)
        assertEquals(listOf("Notebook", "Mercado Pago"), l.map { it.name }) // quitada ("z") fica de fora; maior saldo primeiro
        assertEquals(Simulator.Debt("h", "Notebook", 60000, 1, 3, 60000, true), l[0])  // no cartão: só as parcelas que ainda não chegaram
        assertEquals(Simulator.Debt("g", "Mercado Pago", 12140, 2, 6, 24280, false), l[1])
        val p = Simulator.payoff(l[1], 22000, 100000)
        assertEquals(Simulator.Payoff(22000, 2280, 12140, 2, 78000), p)
    }

    @Test fun reportComparison() {
        val c = PeriodCompare.of(d("2026-10-01"), d("2026-10-31"), today)!!
        assertEquals(Compare(d("2026-09-01"), d("2026-09-09"), "vs. set (mesmos dias)"), c)
        assertEquals(Compare(d("2026-08-01"), d("2026-08-31"), "vs. agosto"), PeriodCompare.of(d("2026-09-01"), d("2026-09-30"), today))
        // 31 de março: fevereiro tem menos dias
        assertEquals(d("2026-02-28"), PeriodCompare.of(d("2026-03-01"), d("2026-03-31"), d("2026-03-31"))!!.to)
        assertEquals(Compare(d("2026-09-21"), d("2026-09-30"), "vs. período anterior"), PeriodCompare.of(d("2026-10-01"), d("2026-10-10"), today))
        assertNull(PeriodCompare.of(null, null, today)); assertNull(PeriodCompare.of(d("2026-10-01"), null, today))
        assertEquals("−11% vs. set (mesmos dias)", PeriodCompare.text(520000, 585000, c))
        assertEquals("+20% vs. set (mesmos dias)", PeriodCompare.text(120, 100, c))
        assertEquals("0% vs. set (mesmos dias)", PeriodCompare.text(100, 100, c))
        assertEquals("Sem base para comparar", PeriodCompare.text(100, 0, c))
    }
}
