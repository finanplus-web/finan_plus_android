// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Total de um dia na lista de Lançamentos e no calendário.
 * [net] = receitas − despesas das contas (realizadas ou pendentes), sem compras no cartão (contam na fatura)
 * e sem reservas para meta nem transferências entre contas; [goals] = guardado em metas − retirado das metas nesse dia;
 * [hasFlow] = o dia tem alguma receita ou despesa que entra em [net] (sem isso, o total do dia não aparece).
 * Só apresentação: os saldos (atual, previsto) continuam tirando a reserva da conta, como sempre.
 */
data class DayTotal(val net: Cents, val goals: Cents, val hasFlow: Boolean)

/** O que falta entrar e sair num mês (o que ainda está pendente). */
data class Pending(val toReceive: Cents, val toPay: Cents)

/**
 * Período da lista de Lançamentos (De/Até) e os resumos que a tela mostra.
 * Sem Android: testado em JVM.
 */
object Period {
    /** O período é exatamente um mês inteiro (do dia 1 ao último dia)? */
    fun fullMonth(from: LocalDate?, to: LocalDate?): YearMonth? {
        if (from == null || to == null || from.dayOfMonth != 1) return null
        val ym = from.ym()
        return if (to == ym.atEndOfMonth()) ym else null
    }

    /** Título do período: "Outubro de 2026", "Todo o período", "01/10/2026 a 15/10/2026", "Desde 01/10/2026", "Até 15/10/2026". */
    fun label(from: LocalDate?, to: LocalDate?): String {
        fullMonth(from, to)?.let { return MonthCalendar.monthTitle(it) }
        fun f(d: LocalDate) = String.format(java.util.Locale.ROOT, "%02d/%02d/%d", d.dayOfMonth, d.monthValue, d.year)
        return when {
            from == null && to == null -> "Todo o período"
            from == null -> "Até ${f(to!!)}"
            to == null -> "Desde ${f(from)}"
            from == to -> f(from)
            else -> "${f(from)} a ${f(to)}"
        }
    }

    /**
     * Setas ‹ › do período: anda um mês inteiro. Se o período não for um mês inteiro,
     * vai para o mês vizinho de onde ele começa (ou de hoje, se não tem início).
     */
    fun shift(from: LocalDate?, to: LocalDate?, delta: Long, today: LocalDate): Pair<LocalDate, LocalDate> {
        val base = fullMonth(from, to) ?: (from ?: to ?: today).ym()
        val ym = base.plusMonths(delta)
        return ym.atDay(1) to ym.atEndOfMonth()
    }

    /**
     * Pendências do mês [ym]: receitas a receber e contas a pagar fora do cartão, mais as faturas
     * em aberto que vencem no mês (compras no cartão são pagas pela fatura, não contam de novo).
     */
    fun monthPending(s: AppState, ym: YearMonth, today: LocalDate): Pending {
        var inc = 0L; var exp = 0L
        for (t in s.txs) {
            if (t.paid || t.isCard || t.date.ym() != ym) continue
            if (t.kind == Kind.INCOME) inc += t.value else exp += t.value
        }
        exp += MonthCalendar.invoicesDue(s, ym, today).sumOf { it.amount }
        // num mês que ainda não chegou, as recorrências previstas também faltam entrar ou sair
        for (t in Projection.between(s, ym.atDay(1), ym.atEndOfMonth(), today)) {
            if (t.isCard) continue
            if (t.kind == Kind.INCOME) inc += t.value else exp += t.value
        }
        return Pending(inc, exp)
    }

    /** Pendências de uma lista já filtrada (fora do cartão). */
    fun pending(list: List<Tx>): Pending {
        var inc = 0L; var exp = 0L
        for (t in list) {
            if (t.paid || !t.isFlow || t.isCard) continue
            if (t.kind == Kind.INCOME) inc += t.value else exp += t.value
        }
        return Pending(inc, exp)
    }

    /**
     * Saldo de um dia na lista agrupada: o que entra menos o que sai das contas, realizado ou pendente.
     * Compras no cartão ficam de fora (contam pela fatura); pagamento de fatura conta como saída.
     * É a mesma regra do calendário.
     */
    fun dayTotal(txs: List<Tx>): DayTotal {
        var net = 0L; var goals = 0L; var flow = false
        for (t in txs) when {
            t.isCard -> {}
            // transferência: sai de uma conta e entra na outra, o total do dia não muda
            t.isTransfer -> {}
            t.isGoal -> goals += if (t.kind == Kind.EXPENSE) t.value else -t.value
            else -> { flow = true; net += if (t.kind == Kind.INCOME) t.value else -t.value }
        }
        return DayTotal(net, goals, flow)
    }

    fun cashNet(txs: List<Tx>): Cents = txs.fold(0L) { n, t ->
        when {
            t.isCard -> n
            t.kind == Kind.INCOME -> n + t.value
            else -> n - t.value
        }
    }
}

/** Comparação dos Relatórios: o período de referência e como chamá-lo ("vs. set (mesmos dias)"). */
data class Compare(val from: LocalDate, val to: LocalDate, val label: String)

object PeriodCompare {
    private val SHORT = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    /**
     * - mês atual inteiro: o mês anterior **até o mesmo dia** (comparar o começo de um mês com o mês anterior inteiro seria injusto);
     * - outro mês inteiro: o mês anterior inteiro;
     * - período livre com início e fim: o período do mesmo tamanho logo antes;
     * - sem início ou sem fim: sem comparação (null).
     */
    fun of(from: LocalDate?, to: LocalDate?, today: LocalDate): Compare? {
        val ym = Period.fullMonth(from, to)
        if (ym != null) {
            val prev = ym.minusMonths(1)
            return if (ym == today.ym()) Compare(prev.atDay(1), prev.atDay(minOf(today.dayOfMonth, prev.lengthOfMonth())), "vs. ${SHORT[prev.monthValue - 1]} (mesmos dias)")
            else Compare(prev.atDay(1), prev.atEndOfMonth(), "vs. ${MonthCalendar.monthName(prev)}")
        }
        if (from == null || to == null || to.isBefore(from)) return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
        return Compare(from.minusDays(days), from.minusDays(1), "vs. período anterior")
    }

    /** "+12% vs. …", "−9% vs. …"; sem valor na referência: "Sem base para comparar" */
    fun text(cur: Cents, prev: Cents, c: Compare): String {
        if (prev <= 0) return "Sem base para comparar"
        val pct = Math.round((cur - prev) * 100.0 / prev)
        return (if (pct > 0) "+" else if (pct < 0) "−" else "") + kotlin.math.abs(pct) + "% " + c.label
    }
}
