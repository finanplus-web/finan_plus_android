// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Tipos de marca (pontinho) de um dia no calendário. */
enum class DayMark { INCOME, EXPENSE, CARD }

/** Fatura em aberto de um cartão, mostrada no dia do vencimento. */
data class InvoiceDue(val cardId: String, val cardName: String, val amount: Cents, val due: LocalDate, val overdue: Boolean)

/**
 * Um dia do calendário.
 * [income]/[expense] = dinheiro que entra/sai das contas nesse dia (realizado ou pendente):
 * receitas e despesas fora do cartão, pagamentos de fatura e faturas em aberto no vencimento.
 * Compras no cartão aparecem em [txs] (e marcam [DayMark.CARD]), mas não entram na soma:
 * o dinheiro só sai das contas quando a fatura é paga.
 */
data class CalDay(
    val date: LocalDate,
    val txs: List<Tx>,
    val invoices: List<InvoiceDue>,
    val income: Cents,
    val expense: Cents,
    val marks: Set<DayMark>,
    /** há conta pendente com data passada ou fatura vencida em aberto */
    val overdue: Boolean,
) {
    val net: Cents get() = income - expense
    val count: Int get() = txs.size + invoices.size
}

data class CalTotals(val income: Cents, val expense: Cents) { val net: Cents get() = income - expense }

/** Regras do calendário da aba Lançamentos. Sem Android: testado em JVM. */
object MonthCalendar {
    /** Primeiro dia da semana do calendário (padrão brasileiro). */
    val WEEK_START: DayOfWeek = DayOfWeek.SUNDAY

    /** Grade do mês: dias vazios (null) antes do dia 1 e no fim, sempre em semanas completas de 7. */
    fun cells(ym: YearMonth): List<LocalDate?> {
        val first = ym.atDay(1)
        val lead = (first.dayOfWeek.value - WEEK_START.value + 7) % 7
        val out = ArrayList<LocalDate?>(42)
        repeat(lead) { out.add(null) }
        for (d in 1..ym.lengthOfMonth()) out.add(ym.atDay(d))
        while (out.size % 7 != 0) out.add(null)
        return out
    }

    /** Faturas em aberto (de todos os cartões) que vencem no mês [ym]. */
    fun invoicesDue(s: AppState, ym: YearMonth, today: LocalDate): List<InvoiceDue> = s.cards.flatMap { c ->
        Finance.cardStatus(s, c, today).invoices
            .filter { it.open > 0 && it.due.ym() == ym }
            .map { InvoiceDue(c.id, c.name, it.open, it.due, it.due.isBefore(today)) }
    }

    /** Dias do mês [ym] que têm algo (os demais não aparecem no mapa). */
    fun build(s: AppState, ym: YearMonth, today: LocalDate): Map<LocalDate, CalDay> {
        val txsByDay = s.txs.filter { it.date.ym() == ym }.groupBy { it.date }
        val invByDay = invoicesDue(s, ym, today).groupBy { it.due }
        return (txsByDay.keys + invByDay.keys).sorted().associateWith { day ->
            val txs = (txsByDay[day] ?: emptyList()).sortedWith(compareBy<Tx> { it.kind != Kind.INCOME }.thenBy { it.isCard }.thenBy { it.id })
            val invs = invByDay[day] ?: emptyList()
            var inc = 0L; var exp = 0L
            val marks = LinkedHashSet<DayMark>()
            var overdue = false
            for (t in txs) {
                when {
                    t.isCard -> marks.add(DayMark.CARD) // compra no cartão: só na fatura
                    t.kind == Kind.INCOME -> { inc += t.value; marks.add(DayMark.INCOME) }
                    else -> { exp += t.value; marks.add(if (t.cardPayment.isNotEmpty()) DayMark.CARD else DayMark.EXPENSE) }
                }
                if (!t.paid && !t.isCard && t.date.isBefore(today)) overdue = true
            }
            for (i in invs) { exp += i.amount; marks.add(DayMark.CARD); if (i.overdue) overdue = true }
            CalDay(day, txs, invs, inc, exp, DayMark.entries.filter { it in marks }.toSet(), overdue)
        }
    }

    /** Totais do mês: a soma de todos os dias (entradas, saídas e resultado). */
    fun totals(days: Map<LocalDate, CalDay>): CalTotals =
        CalTotals(days.values.sumOf { it.income }, days.values.sumOf { it.expense })

    /**
     * Valor curto para caber no quadradinho do dia (sem "R$"), arredondado ao mais próximo:
     * 182 · 1,5 mil · 15 mil · 1,2 mi. R$ 119,90 vira 120; R$ 999,99 vira 1 mil.
     */
    fun compact(c: Cents): String {
        val reais = (kotlin.math.abs(c) + 50) / 100
        val sign = if (c < 0) "−" else ""
        fun short(unit: Long, suffix: String): String {
            val tenths = (reais * 10 + unit / 2) / unit
            if (tenths >= 100) return "${(reais + unit / 2) / unit} $suffix"
            return if (tenths % 10 == 0L) "${tenths / 10} $suffix" else "${tenths / 10},${tenths % 10} $suffix"
        }
        // a unidade é escolhida pelo valor já arredondado (999.999 vira "1 mi", não "1000 mil")
        return sign + when {
            reais < 1_000 -> reais.toString()
            (reais + 500) / 1_000 < 1_000 -> short(1_000, "mil")
            (reais + 500_000) / 1_000_000 < 1_000 -> short(1_000_000, "mi")
            else -> short(1_000_000_000, "bi")
        }
    }

    /** Valor do dia com sinal ("+5,2 mil", "−120"); zero fica "0". */
    fun signed(c: Cents): String = when {
        c > 0 -> "+" + compact(c)
        c < 0 -> compact(c)
        else -> "0"
    }

    private val MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to "segunda-feira", DayOfWeek.TUESDAY to "terça-feira", DayOfWeek.WEDNESDAY to "quarta-feira",
        DayOfWeek.THURSDAY to "quinta-feira", DayOfWeek.FRIDAY to "sexta-feira", DayOfWeek.SATURDAY to "sábado", DayOfWeek.SUNDAY to "domingo",
    )
    /** Cabeçalho das colunas, começando em [WEEK_START]. */
    val WEEK_HEADER: List<String> = listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")

    /** "Outubro de 2026" (sem depender do idioma do aparelho). */
    fun monthTitle(ym: YearMonth): String = MONTHS[ym.monthValue - 1].replaceFirstChar { it.uppercase() } + " de " + ym.year

    fun monthName(ym: YearMonth): String = MONTHS[ym.monthValue - 1]

    /** "Quinta, 15 de outubro" (ano só quando não é o de [today]). */
    fun dayTitle(d: LocalDate, today: LocalDate): String {
        val wd = WEEKDAYS.getValue(d.dayOfWeek).substringBefore("-").replaceFirstChar { it.uppercase() }
        return "$wd, ${d.dayOfMonth} de ${MONTHS[d.monthValue - 1]}" + if (d.year != today.year) " de ${d.year}" else ""
    }

    /**
     * Texto do leitor de tela para um dia (TalkBack), com o valor completo.
     * Com "Ocultar valores", não fala valores.
     */
    fun describe(d: LocalDate, day: CalDay?, today: LocalDate, hide: Boolean): String {
        val parts = ArrayList<String>()
        parts.add("${d.dayOfMonth} de ${MONTHS[d.monthValue - 1]}, ${WEEKDAYS.getValue(d.dayOfWeek)}")
        if (d == today) parts.add("hoje")
        if (day == null || day.count == 0) parts.add("sem lançamentos")
        else {
            parts.add(if (day.count == 1) "1 lançamento" else "${day.count} lançamentos")
            if (!hide && (day.income != 0L || day.expense != 0L)) {
                parts.add(when {
                    day.net > 0 -> "saldo do dia mais ${Money.format(day.net)}"
                    day.net < 0 -> "saldo do dia menos ${Money.format(-day.net)}"
                    else -> "saldo do dia zero"
                })
            }
            if (day.overdue) parts.add("em atraso")
        }
        return parts.joinToString(", ")
    }
}
