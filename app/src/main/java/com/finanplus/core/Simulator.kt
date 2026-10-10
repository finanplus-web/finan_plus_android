// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Simulador "E se…?": contas para testar decisões sem mudar nenhum dado (nada aqui grava no estado).
 * Sem Android: testado em JVM, com os mesmos casos do Finan+ web (tests/simulator.test.mjs).
 * Detalhes e exemplos em SIMULADOR.md.
 */
object Simulator {
    /** meses olhados para trás na base */
    const val BASE_MONTHS = 3

    /** O que entra, sai e sobra num mês típico. [months] = quantos meses com dados entraram na média (0 = sem histórico). */
    data class Base(val income: Cents, val expense: Cents, val months: Int) {
        val left: Cents get() = income - expense
    }

    /**
     * Base: média dos [BASE_MONTHS] meses completos antes do mês de [today], só valores realizados
     * (como em Relatórios) e só meses que tiveram algum valor. O mês atual fica de fora porque ainda não terminou.
     */
    fun base(s: AppState, today: LocalDate): Base {
        val flows = (1..BASE_MONTHS).map { Finance.monthFlow(s, today.ym().minusMonths(it.toLong())) }
            .filter { it.income > 0 || it.expense > 0 }
        if (flows.isEmpty()) return Base(0, 0, 0)
        val n = flows.size
        return Base(Math.round(flows.sumOf { it.income }.toDouble() / n), Math.round(flows.sumOf { it.expense }.toDouble() / n), n)
    }

    // ------------------------------------------------------------------ E se eu economizar…
    data class Save(val perMonth: Cents, val months: Int, val total: Cents, val newLeft: Cents, val overLeft: Boolean)

    /** guardar [perMonth] por [months] meses: total juntado e quanto passa a sobrar por mês */
    fun save(base: Base, perMonth: Cents, months: Int): Save {
        val newLeft = base.left - perMonth
        return Save(perMonth, months, perMonth * months, newLeft, perMonth > base.left)
    }

    // ------------------------------------------------------------------ Quanto tempo para comprar…
    /** [months] meses guardando, a partir do mês que vem; [doneYm] = mês em que o valor fica completo */
    data class Buy(val missing: Cents, val months: Int, val doneYm: YearMonth)

    /** null quando não dá para calcular (nada a guardar por mês e ainda falta dinheiro) */
    fun buy(price: Cents, have: Cents, perMonth: Cents, today: LocalDate): Buy? {
        val missing = maxOf(0L, price - have)
        if (missing == 0L) return Buy(0, 0, today.ym())
        if (perMonth <= 0) return null
        val months = ((missing + perMonth - 1) / perMonth).toInt()
        return Buy(missing, months, today.ym().plusMonths(months.toLong()))
    }

    // ------------------------------------------------------------------ E se minha renda mudar…
    data class Income(val newIncome: Cents, val diff: Cents, val newLeft: Cents, val yearDiff: Cents, val goalsMonthly: Cents)

    /** renda muda [percent]% (ex.: −15); [goalsMonthly] = soma das contribuições mensais planejadas das metas em andamento */
    fun income(base: Base, percent: Double, goalsMonthly: Cents): Income {
        val newIncome = Math.round(base.income * (1 + percent / 100.0))
        val diff = newIncome - base.income
        return Income(newIncome, diff, base.left + diff, diff * 12, goalsMonthly)
    }

    /** soma do que as metas não concluídas pedem por mês (contribuição planejada) */
    fun goalsMonthly(s: AppState): Cents = s.goals.filter { it.saved < it.target }.sumOf { it.monthly }

    // ------------------------------------------------------------------ E se eu antecipar uma dívida…
    /** compra parcelada com parcelas ainda por vir; [remaining] = quantas faltam, [left] = quanto falta pagar */
    data class Debt(val groupId: String, val name: String, val parcel: Cents, val remaining: Int, val total: Int, val left: Cents, val card: Boolean)

    /**
     * Parcelamentos com parcelas a pagar: na conta, as parcelas pendentes; no cartão, as que ainda não chegaram
     * (data depois de hoje). Ordenados pelo que falta pagar, do maior para o menor.
     */
    fun debts(s: AppState, today: LocalDate): List<Debt> = s.txs.filter { it.groupId.isNotEmpty() && it.parcelTotal > 1 && it.kind == Kind.EXPENSE }
        .groupBy { it.groupId }.mapNotNull { (gid, l) ->
            val rest = l.filter { if (it.isCard) it.date.isAfter(today) else !it.paid }
            if (rest.isEmpty()) return@mapNotNull null
            val first = l.minByOrNull { it.parcelN }!!
            val name = first.desc.replace(Regex("""\s*\(?\d+/\d+\)?\s*$"""), "").trim().ifEmpty { first.desc }
            Debt(gid, name, rest.maxOf { it.value }, rest.size, first.parcelTotal, rest.sumOf { it.value }, first.isCard)
        }.sortedByDescending { it.left }

    data class Payoff(val payNow: Cents, val saved: Cents, val freedPerMonth: Cents, val months: Int, val balanceAfter: Cents)

    /**
     * Quitar hoje por [payNow] (o valor que o credor oferece; sem ele, o total que falta).
     * O app não conhece os juros: a economia é só a diferença entre o que falta e o valor oferecido.
     */
    fun payoff(debt: Debt, payNow: Cents, balance: Cents): Payoff =
        Payoff(payNow, maxOf(0L, debt.left - payNow), debt.parcel, debt.remaining, balance - payNow)

    /** "janeiro de 2028" */
    fun monthYear(ym: YearMonth): String = MonthCalendar.monthName(ym) + " de " + ym.year
}
