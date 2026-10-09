// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun d(s: String) = LocalDate.parse(s)
private fun tx(id: String, kind: Kind, value: Long, date: String, paid: Boolean = true, card: String = "", pay: String = "") =
    Tx(id, kind, value, d(date), id, "Outros", paid, "main", card, pay)

class CalendarTest {
    private val oct = YearMonth.of(2026, 10)
    private val today = d("2026-10-08")

    @Test fun gridStartsOnSundayAndHasFullWeeks() {
        val cells = MonthCalendar.cells(oct) // 01/10/2026 é quinta
        assertEquals(35, cells.size)
        assertEquals(listOf(null, null, null, null), cells.take(4))
        assertEquals(d("2026-10-01"), cells[4])
        assertEquals(d("2026-10-31"), cells[34])
        // fevereiro de 2026 começa no domingo e tem 28 dias: exatamente 4 semanas, sem vazios
        val feb = MonthCalendar.cells(YearMonth.of(2026, 2))
        assertEquals(28, feb.size)
        assertEquals(d("2026-02-01"), feb[0])
        // agosto de 2026 começa no sábado: 6 semanas
        assertEquals(42, MonthCalendar.cells(YearMonth.of(2026, 8)).size)
    }

    @Test fun dayTotalsCountAccountsNotCardPurchases() {
        val s = AppState(
            txs = listOf(
                tx("sal", Kind.INCOME, 520000, "2026-10-05"),
                tx("merc", Kind.EXPENSE, 18240, "2026-10-05"),
                tx("compra", Kind.EXPENSE, 9990, "2026-10-05", paid = true, card = "c1"),
                tx("outro", Kind.EXPENSE, 1000, "2026-11-05"),
            ),
            cards = listOf(Card("c1", "Roxo", 500000, close = 20, due = 28)),
        )
        val days = MonthCalendar.build(s, oct, today)
        val day = days.getValue(d("2026-10-05"))
        assertEquals(520000, day.income)
        assertEquals(18240, day.expense)            // a compra no cartão não sai da conta neste dia
        assertEquals(520000 - 18240L, day.net)
        assertEquals(setOf(DayMark.INCOME, DayMark.EXPENSE, DayMark.CARD), day.marks)
        assertEquals(3, day.txs.size)
        assertEquals("sal", day.txs.first().id)     // receitas primeiro
        assertNull(days[d("2026-11-05")])           // outro mês fica de fora
    }

    @Test fun openInvoiceAppearsOnDueDate() {
        val card = Card("c1", "Roxo", 500000, close = 5, due = 15)
        val s = AppState(
            txs = listOf(
                tx("c-a", Kind.EXPENSE, 40000, "2026-09-20", card = "c1"), // fecha 05/10, vence 15/10
                tx("c-b", Kind.EXPENSE, 20000, "2026-09-25", card = "c1"),
                tx("pg", Kind.EXPENSE, 10000, "2026-10-01", pay = "c1"),   // pagamento parcial
            ),
            cards = listOf(card),
        )
        val days = MonthCalendar.build(s, oct, today)
        val due = days.getValue(d("2026-10-15"))
        assertEquals(1, due.invoices.size)
        assertEquals(50000, due.invoices[0].amount)  // 600 − 100 pagos
        assertEquals(50000, due.expense)
        assertFalse(due.overdue)
        assertEquals(setOf(DayMark.CARD), due.marks)
        // o pagamento da fatura é dinheiro saindo da conta no dia em que foi feito
        assertEquals(10000, days.getValue(d("2026-10-01")).expense)
        // fatura vencida e em aberto fica em atraso
        val later = MonthCalendar.build(s, oct, d("2026-10-20"))
        assertTrue(later.getValue(d("2026-10-15")).overdue)
    }

    @Test fun overdueOnlyForPendingInThePast() {
        val s = AppState(txs = listOf(
            tx("net", Kind.EXPENSE, 11990, "2026-10-06", paid = false),
            tx("alug", Kind.EXPENSE, 150000, "2026-10-10", paid = false),
            tx("farm", Kind.EXPENSE, 4690, "2026-10-07", paid = true),
        ))
        val days = MonthCalendar.build(s, oct, today)
        assertTrue(days.getValue(d("2026-10-06")).overdue)
        assertFalse(days.getValue(d("2026-10-10")).overdue)
        assertFalse(days.getValue(d("2026-10-07")).overdue)
    }

    @Test fun monthTotalsAreTheSumOfTheDays() {
        val s = AppState(txs = listOf(
            tx("a", Kind.INCOME, 520000, "2026-10-05"),
            tx("b", Kind.INCOME, 90000, "2026-10-30", paid = false),
            tx("c", Kind.EXPENSE, 150000, "2026-10-10", paid = false),
            tx("d", Kind.EXPENSE, 34171, "2026-10-30", paid = false),
        ))
        val days = MonthCalendar.build(s, oct, today)
        val t = MonthCalendar.totals(days)
        assertEquals(610000, t.income)
        assertEquals(184171, t.expense)
        assertEquals(days.values.sumOf { it.net }, t.net)
        assertEquals(55829, days.getValue(d("2026-10-30")).net)
    }

    @Test fun compactValuesFitTheCell() {
        val cases = mapOf(
            0L to "0", 18240L to "182", 11990L to "120", 99949L to "999", 99999L to "1 mil", 100000L to "1 mil",
            150000L to "1,5 mil", 520000L to "5,2 mil", 159999L to "1,6 mil", 1500000L to "15 mil", 1549900L to "15 mil",
            99849999L to "999 mil", 99949999L to "1 mi", 99999999L to "1 mi", 100000000L to "1 mi", 120000000L to "1,2 mi", 2000000000000L to "20 bi",
            Money.MAX_ABS to "10000 bi",
        )
        for ((c, s) in cases) assertEquals(s, MonthCalendar.compact(c), "$c")
        assertEquals("+5,2 mil", MonthCalendar.signed(520000))
        assertEquals("−120", MonthCalendar.signed(-11990))
        assertEquals("0", MonthCalendar.signed(0))
    }

    @Test fun titlesAndScreenReaderText() {
        assertEquals("Outubro de 2026", MonthCalendar.monthTitle(oct))
        assertEquals("Março de 2027", MonthCalendar.monthTitle(YearMonth.of(2027, 3)))
        assertEquals("Quinta, 15 de outubro", MonthCalendar.dayTitle(d("2026-10-15"), today))
        assertEquals("Sábado, 2 de janeiro de 2027", MonthCalendar.dayTitle(d("2027-01-02"), today))
        assertEquals("Segunda, 5 de outubro", MonthCalendar.dayTitle(d("2026-10-05"), today))

        val s = AppState(txs = listOf(tx("net", Kind.EXPENSE, 11990, "2026-10-06", paid = false)))
        val day = MonthCalendar.build(s, oct, today)[d("2026-10-06")]
        assertEquals("6 de outubro, terça-feira, 1 lançamento, saldo do dia menos R$ 119,90, em atraso",
            MonthCalendar.describe(d("2026-10-06"), day, today, hide = false))
        // "Ocultar valores": não fala o valor
        assertEquals("6 de outubro, terça-feira, 1 lançamento, em atraso", MonthCalendar.describe(d("2026-10-06"), day, today, hide = true))
        assertEquals("8 de outubro, quinta-feira, hoje, sem lançamentos", MonthCalendar.describe(today, null, today, hide = false))
    }
}
