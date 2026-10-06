// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.report

import com.finanplus.core.AppState
import com.finanplus.core.Goal
import com.finanplus.core.Kind
import com.finanplus.core.Tx
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private var n = 0
private fun d(s: String) = LocalDate.parse(s)
private fun ex(desc: String, cat: String, v: Long, date: String, paid: Boolean = true, card: String = "", pay: String = "") =
    Tx("r${n++}", Kind.EXPENSE, v, d(date), desc, cat, paid, "main", card, pay)
private fun inc(desc: String, cat: String, v: Long, date: String, paid: Boolean = true) = Tx("r${n++}", Kind.INCOME, v, d(date), desc, cat, paid, "main")

class ReportTest {
    private val s = AppState(
        txs = listOf(
            inc("Salário", "Salário", 5000_00, "2026-10-05"), inc("Freela", "Extra", 800_00, "2026-10-20", paid = false),
            ex("Aluguel", "Moradia", 1500_00, "2026-10-10"), ex("Mercado", "Alimentação", 600_00, "2026-10-06"),
            ex("iFood", "Alimentação", 150_00, "2026-10-12"), ex("Luz", "Moradia", 200_00, "2026-10-28", paid = false),
            ex("Pagamento fatura", AppState.CARD_PAYMENT_CAT, 900_00, "2026-10-15", pay = "c1"), // não conta como despesa
            ex("Mercado", "Alimentação", 500_00, "2026-09-06"), inc("Salário", "Salário", 5000_00, "2026-09-05"), // período anterior
            ex("Fora", "Lazer", 99_00, "2026-11-02"),
        ),
        limits = mapOf("Alimentação" to 700_00),
        goals = listOf(Goal("g", "Viagem", 10000_00, 2500_00, null, 0)),
    )
    private val r = Reports.build(s, d("2026-10-01"), d("2026-10-31"), d("2026-10-31"))

    @Test fun totaisRealizadosEPendentes() {
        assertEquals(31, r.days)
        assertEquals(5000_00, r.income); assertEquals(2250_00, r.expense); assertEquals(2750_00, r.balance)
        assertEquals(800_00, r.pendingIncome); assertEquals(200_00, r.pendingExpense)
        assertEquals(55.0, r.savingsRate!!, 0.001)
        assertEquals(2250_00 / 31, r.dailyAverage)
    }

    @Test fun periodoAnteriorDeMesmoTamanho() {
        assertEquals(d("2026-08-31"), r.prevFrom); assertEquals(d("2026-09-30"), r.prevTo)
        assertEquals(500_00, r.prevExpense)
        assertEquals(350.0, r.change(r.expense, r.prevExpense)!!, 0.001)
        assertNull(r.change(100, 0))
    }

    @Test fun categoriasComPercentualELimite() {
        val c = r.expenseByCategory
        assertEquals(listOf("Moradia", "Alimentação"), c.map { it.name })
        assertEquals(1500_00, c[0].value); assertEquals(66.67, c[0].percent, 0.01)
        val food = c[1]
        assertEquals(2, food.count); assertEquals(750_00, food.monthlyAverage); assertEquals(700_00, food.monthlyLimit)
        assertTrue(food.overLimit)
        assertEquals(listOf("Salário"), r.incomeByCategory.map { it.name })
    }

    @Test fun mesesMaioresDespesasELista() {
        assertEquals(listOf(MonthRow(YearMonth.of(2026, 10), 5000_00, 2250_00)), r.months)
        assertEquals(listOf("Aluguel", "Mercado", "iFood"), r.topExpenses.map { it.desc })
        assertEquals(7, r.txs.size) // inclui pendentes e o pagamento de fatura; exclui setembro e novembro
        assertEquals("Salário", r.txs.first().desc)
        assertEquals(25.0, r.goals[0].percent, 0.001)
    }

    @Test fun periodoDeVariosMesesETiposDeAtalho() {
        val y = Reports.build(s, d("2026-09-01"), d("2026-11-30"), d("2026-10-31"))
        assertEquals(3.0, y.monthSpan, 1e-9); assertEquals(0.5, Reports.build(s, d("2026-11-01"), d("2026-11-15"), d("2026-11-15")).monthSpan, 1e-9)
        assertEquals(3, y.months.size); assertEquals(500_00, y.months[0].expense); assertEquals(99_00, y.months[2].expense)
        val today = d("2026-10-03")
        assertEquals(d("2026-09-01") to d("2026-09-30"), Reports.preset("anterior", today, null))
        assertEquals(d("2025-11-01") to d("2026-10-31"), Reports.preset("12m", today, null))
        assertEquals(d("2024-02-03") to d("2026-12-01"), Reports.preset("tudo", today, d("2024-02-03"), d("2026-12-01")))
    }

    @Test fun formatosDoGrafico() {
        assertEquals("R$ 950", Reports.compact(950_00)); assertEquals("R$ 1,2 mil", Reports.compact(1234_00))
        assertEquals("R$ 15 mil", Reports.compact(15000_00)); assertEquals("R$ 2,5 mi", Reports.compact(2_500_000_00))
        assertEquals(1000_00, Reports.niceStep(3200_00)); assertEquals(250_00, Reports.niceStep(1000_00))
        assertEquals("relatorio-finan-plus-2026-10-01-a-2026-10-31.pdf", Reports.fileName(d("2026-10-01"), d("2026-10-31")))
    }
}
