// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun d(s: String) = LocalDate.parse(s)
private fun tx(id: String, kind: Kind, value: Long, date: String, paid: Boolean = true, card: String = "", pay: String = "") =
    Tx(id, kind, value, d(date), id, "Outros", paid, "main", card, pay)

class PeriodTest {
    private val today = d("2026-10-08")

    @Test fun labels() {
        assertEquals("Outubro de 2026", Period.label(d("2026-10-01"), d("2026-10-31")))
        assertEquals("Fevereiro de 2028", Period.label(d("2028-02-01"), d("2028-02-29")))
        assertEquals("01/10/2026 a 15/10/2026", Period.label(d("2026-10-01"), d("2026-10-15")))
        assertEquals("Todo o período", Period.label(null, null))
        assertEquals("Desde 01/10/2026", Period.label(d("2026-10-01"), null))
        assertEquals("Até 15/10/2026", Period.label(null, d("2026-10-15")))
        assertEquals("08/10/2026", Period.label(today, today))
        assertNull(Period.fullMonth(d("2026-10-02"), d("2026-10-31")))
    }

    @Test fun arrowsMoveWholeMonths() {
        assertEquals(d("2026-11-01") to d("2026-11-30"), Period.shift(d("2026-10-01"), d("2026-10-31"), 1, today))
        assertEquals(d("2026-09-01") to d("2026-09-30"), Period.shift(d("2026-10-01"), d("2026-10-31"), -1, today))
        // período livre: vai para o mês vizinho de onde começa
        assertEquals(d("2026-10-01") to d("2026-10-31"), Period.shift(d("2026-09-10"), d("2026-10-09"), 1, today))
        // sem datas: a partir do mês de hoje
        assertEquals(d("2026-09-01") to d("2026-09-30"), Period.shift(null, null, -1, today))
        // virada de ano
        assertEquals(d("2027-01-01") to d("2027-01-31"), Period.shift(d("2026-12-01"), d("2026-12-31"), 1, today))
    }

    @Test fun monthPendingIncludesInvoicesNotCardPurchases() {
        val s = AppState(
            txs = listOf(
                tx("sal", Kind.INCOME, 121362, "2026-10-15", paid = false),
                tx("alug", Kind.EXPENSE, 70000, "2026-10-15", paid = false),
                tx("pago", Kind.EXPENSE, 5000, "2026-10-02"),
                tx("nov", Kind.EXPENSE, 9999, "2026-11-02", paid = false),
                tx("compra", Kind.EXPENSE, 20000, "2026-09-25", card = "c1"), // fatura vence 15/10
            ),
            cards = listOf(Card("c1", "Roxo", 500000, close = 5, due = 15)),
        )
        assertEquals(Pending(121362, 70000 + 20000), Period.monthPending(s, YearMonth.of(2026, 10), today))
        assertEquals(Pending(121362, 70000), Period.pending(s.txs.filter { it.date.ym() == YearMonth.of(2026, 10) }))
    }

    @Test fun dayNetMatchesCalendar() {
        val txs = listOf(
            tx("a", Kind.INCOME, 121362, "2026-10-15", paid = false),
            tx("b", Kind.EXPENSE, 113240, "2026-10-15", paid = false),
            tx("c", Kind.EXPENSE, 9990, "2026-10-15", card = "c1"),
            tx("d", Kind.EXPENSE, 1000, "2026-10-15", pay = "c1"),
        )
        assertEquals(121362 - 113240 - 1000L, Period.cashNet(txs))
        val s = AppState(txs = txs, cards = listOf(Card("c1", "Roxo", 500000, close = 20, due = 28)))
        assertEquals(MonthCalendar.build(s, YearMonth.of(2026, 10), today).getValue(d("2026-10-15")).net, Period.cashNet(txs))
    }
}
