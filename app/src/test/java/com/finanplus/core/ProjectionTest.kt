// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun d(s: String) = LocalDate.parse(s)
private fun rec(id: String, kind: Kind, v: Long, day: Int, start: String?, last: YearMonth?, active: Boolean = true, card: String = "") =
    Recurring(id, kind, id, v, "Salário", "main", card, day, active, start?.let(::d), last)

/** Recorrências previstas nos meses que ainda não chegaram (o caso relatado: adiantamento no dia 15 sem aparecer em novembro). */
class ProjectionTest {
    private val today = d("2026-10-10")
    private val adiant = rec("adiant", Kind.INCOME, 121362, 15, "2026-10-15", YearMonth.of(2026, 10))

    @Test fun nextMonthsOnlyNeverTheCurrentOne() {
        val s = AppState(recurring = listOf(adiant))
        val l = Projection.between(s, d("2026-10-01"), d("2026-12-31"), today)
        assertEquals(listOf(d("2026-11-15"), d("2026-12-15")), l.map { it.date }) // outubro já foi gerado de verdade
        assertTrue(l.all { it.isProjected && !it.paid && it.kind == Kind.INCOME && it.value == 121362L && it.recurringId == "adiant" })
        assertEquals("prev:adiant:2026-11", l[0].id)
        assertFalse(Ops.canTogglePaid(l[0]))
    }

    @Test fun pausedStartAndShortMonths() {
        assertTrue(Projection.between(AppState(recurring = listOf(adiant.copy(active = false))), today, d("2027-12-31"), today).isEmpty())
        // começa em fevereiro de 2027: nada antes disso
        val later = rec("r", Kind.EXPENSE, 5000, 31, "2027-02-10", null)
        val l = Projection.between(AppState(recurring = listOf(later)), today, d("2027-04-30"), today)
        assertEquals(listOf(d("2027-02-28"), d("2027-03-31"), d("2027-04-30")), l.map { it.date }) // dia 31 vira o último dia
        // ainda não gerada no mês atual (last atrasado): a previsão também só começa no mês que vem
        val stale = rec("s", Kind.INCOME, 1000, 5, null, YearMonth.of(2026, 8))
        assertEquals(d("2026-11-05"), Projection.between(AppState(recurring = listOf(stale)), today, d("2026-11-30"), today).single().date)
    }

    @Test fun calendarPendingAndForecast() {
        val s = AppState(recurring = listOf(adiant), txs = listOf(Tx("alug", Kind.EXPENSE, 12140, d("2026-11-30"), "Parcela", "Outros", false, "main")))
        val nov = YearMonth.of(2026, 11)
        val day = MonthCalendar.build(s, nov, today).getValue(d("2026-11-15"))
        assertEquals(121362, day.income)
        assertEquals(setOf(DayMark.INCOME), day.marks)
        assertEquals(Pending(121362, 12140), Period.monthPending(s, nov, today))
        // saldo previsto ao fim de 30/11: entra o adiantamento previsto, sai a parcela
        assertEquals(121362 - 12140L, Finance.futureBalance(s, d("2026-11-30"), today) - Finance.currentBalance(s))
        // o saldo previsto do mês atual não muda (outubro não tem previstos)
        assertEquals(Finance.futureBalance(AppState(), d("2026-10-31"), today), Finance.futureBalance(AppState(recurring = listOf(adiant)), d("2026-10-31"), today))
    }

    @Test fun whenTheMonthArrivesTheRealOneReplacesTheProjection() {
        val nov1 = d("2026-11-01")
        val (s, n) = Finance.generateRecurring(AppState(recurring = listOf(adiant)), nov1)
        assertEquals(1, n)
        assertEquals(d("2026-11-15"), s.txs.single().date)
        // novembro agora é o mês atual: sem previsto duplicado; dezembro continua previsto
        assertEquals(listOf(d("2026-12-15")), Projection.between(s, nov1, d("2026-12-31"), nov1).map { it.date })
        assertEquals(121362, MonthCalendar.build(s, YearMonth.of(2026, 11), nov1).getValue(d("2026-11-15")).income)
    }
}
