// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.report

import com.finanplus.core.AppState
import com.finanplus.core.Cents
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.Tx
import com.finanplus.core.ym
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Dados do relatório em PDF de um período. Só cálculo, sem Android (testado em ReportTest).
 *
 * Convenções (as mesmas dos Relatórios do app):
 * - receitas/despesas = lançamentos **realizados** (pagos/recebidos) no período;
 * - pagamento de fatura não é despesa nova (as compras no cartão já contam na data da compra);
 * - pendentes (a receber / a pagar) aparecem à parte.
 */
data class Report(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    /** nº de meses do período: meses inteiros contam 1; meses parciais, a fração de dias (ex.: 15 de 30 = 0,5) */
    val monthSpan: Double,
    val income: Cents,
    val expense: Cents,
    val pendingIncome: Cents,
    val pendingExpense: Cents,
    /** mesmo número de dias, imediatamente antes de [from] */
    val prevFrom: LocalDate,
    val prevTo: LocalDate,
    val prevIncome: Cents,
    val prevExpense: Cents,
    val expenseByCategory: List<CategoryRow>,
    val incomeByCategory: List<CategoryRow>,
    val months: List<MonthRow>,
    val topExpenses: List<Tx>,
    /** todos os lançamentos do período, por data (inclui pendentes e pagamentos de fatura) */
    val txs: List<Tx>,
    val accounts: List<Pair<String, Cents>>,
    val goals: List<GoalRow>,
) {
    val balance: Cents get() = income - expense
    /** % das receitas que sobrou (null sem receitas) */
    val savingsRate: Double? get() = if (income > 0) balance * 100.0 / income else null
    val dailyAverage: Cents get() = if (days > 0) expense / days else 0

    fun change(cur: Cents, prev: Cents): Double? = if (prev > 0) (cur - prev) * 100.0 / prev else null
}

data class CategoryRow(
    val name: String,
    val value: Cents,
    val percent: Double,
    val count: Int,
    /** valor médio por mês no período */
    val monthlyAverage: Cents,
    /** limite mensal definido pelo usuário (só despesas) */
    val monthlyLimit: Cents?,
) {
    val overLimit: Boolean get() = monthlyLimit != null && monthlyAverage > monthlyLimit
}

data class MonthRow(val ym: YearMonth, val income: Cents, val expense: Cents) {
    val balance: Cents get() = income - expense
}

data class GoalRow(val name: String, val saved: Cents, val target: Cents, val deadline: LocalDate?) {
    val percent: Double get() = if (target > 0) (saved * 100.0 / target).coerceAtMost(100.0) else 0.0
}

object Reports {
    const val TOP = 10
    /** gráfico mensal mostra no máximo os últimos 24 meses do período */
    const val MAX_CHART_MONTHS = 24

    private fun inRange(t: Tx, from: LocalDate, to: LocalDate) = !t.date.isBefore(from) && !t.date.isAfter(to)
    private fun sum(l: List<Tx>): Cents = l.fold(0L) { a, t -> a + t.value }

    fun build(s: AppState, from: LocalDate, to: LocalDate, today: LocalDate): Report {
        require(!to.isBefore(from)) { "período inválido" }
        val days = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
        val period = s.txs.filter { inRange(it, from, to) }
        val flows = period.filter { it.isFlow }
        val done = flows.filter { it.paid }
        val inc = done.filter { it.kind == Kind.INCOME }
        val exp = done.filter { it.kind == Kind.EXPENSE }
        val pending = flows.filter { !it.paid }

        val prevTo = from.minusDays(1)
        val prevFrom = prevTo.minusDays(days - 1L)
        val prevDone = s.txs.filter { it.isFlow && it.paid && inRange(it, prevFrom, prevTo) }

        var span = 0.0
        run {
            var mm = from.ym()
            while (!mm.isAfter(to.ym())) {
                val a = maxOf(from, mm.atDay(1)); val b = minOf(to, mm.atEndOfMonth())
                span += (ChronoUnit.DAYS.between(a, b) + 1).toDouble() / mm.lengthOfMonth()
                mm = mm.plusMonths(1)
            }
        }
        fun byCategory(l: List<Tx>, limits: Map<String, Cents>): List<CategoryRow> {
            val total = sum(l)
            return l.groupBy { it.category }.map { (c, g) ->
                val v = sum(g)
                CategoryRow(c, v, if (total > 0) v * 100.0 / total else 0.0, g.size, Math.round(v / maxOf(span, 1.0)), limits[c])
            }.sortedWith(compareByDescending<CategoryRow> { it.value }.thenBy { it.name })
        }

        // meses que tocam o período (valores só dos dias dentro do período)
        val months = ArrayList<MonthRow>()
        var m = from.ym()
        while (!m.isAfter(to.ym())) {
            val mm = m
            val l = done.filter { it.date.ym() == mm }
            months += MonthRow(mm, sum(l.filter { it.kind == Kind.INCOME }), sum(l.filter { it.kind == Kind.EXPENSE }))
            m = m.plusMonths(1)
        }

        return Report(
            from = from, to = to, days = days, monthSpan = span,
            income = sum(inc), expense = sum(exp),
            pendingIncome = sum(pending.filter { it.kind == Kind.INCOME }),
            pendingExpense = sum(pending.filter { it.kind == Kind.EXPENSE }),
            prevFrom = prevFrom, prevTo = prevTo,
            prevIncome = sum(prevDone.filter { it.kind == Kind.INCOME }),
            prevExpense = sum(prevDone.filter { it.kind == Kind.EXPENSE }),
            expenseByCategory = byCategory(exp, s.limits),
            incomeByCategory = byCategory(inc, emptyMap()),
            months = months,
            topExpenses = exp.sortedWith(compareByDescending<Tx> { it.value }.thenBy { it.date }).take(TOP),
            txs = period.sortedWith(compareBy<Tx>({ it.date }, { it.kind != Kind.INCOME }, { it.desc })),
            accounts = s.accounts.map { it.name to Finance.accountBalance(s, it) },
            goals = s.goals.map { GoalRow(it.name, it.saved, it.target, it.deadline) },
        )
    }

    /** Valor curto para eixos de gráfico: "R$ 950", "R$ 1,2 mil", "R$ 3,4 mi". */
    fun compact(c: Cents): String {
        val neg = c < 0
        val r = Math.abs(c) / 100.0
        val s = when {
            r < 1_000 -> "R$ " + Math.round(r)
            r < 1_000_000 -> "R$ " + one(r / 1_000) + " mil"
            else -> "R$ " + one(r / 1_000_000) + " mi"
        }
        return if (neg) "-$s" else s
    }

    private fun one(v: Double): String {
        val x = Math.round(v * 10) / 10.0
        return if (x == Math.floor(x)) x.toLong().toString() else x.toString().replace('.', ',')
    }

    /** Escala "bonita" para o eixo do gráfico: 0, passo, 2·passo… até cobrir [max]. */
    fun niceStep(max: Cents, ticks: Int = 4): Cents {
        if (max <= 0) return 100_00
        val raw = max.toDouble() / ticks
        val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
        val n = raw / mag
        val f = when { n <= 1 -> 1.0; n <= 2 -> 2.0; n <= 2.5 -> 2.5; n <= 5 -> 5.0; else -> 10.0 }
        return maxOf(Math.round(f * mag), 100L) // nunca zero: com 1 ou 2 centavos o passo arredondava para 0 (divisão por zero no gráfico)
    }

    /** Nome do arquivo sugerido. */
    fun fileName(from: LocalDate, to: LocalDate) = "relatorio-finan-plus-$from-a-$to.pdf"

    /** Atalhos de período da tela de exportação. */
    fun preset(key: String, today: LocalDate, first: LocalDate?, last: LocalDate? = null): Pair<LocalDate, LocalDate> {
        val ym = today.ym()
        return when (key) {
            "mes" -> ym.atDay(1) to ym.atEndOfMonth()
            "anterior" -> ym.minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            "ano" -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
            "12m" -> ym.minusMonths(11).atDay(1) to ym.atEndOfMonth()
            else -> minOf(first ?: ym.atDay(1), ym.atDay(1)) to maxOf(last ?: today, today)
        }
    }
}
