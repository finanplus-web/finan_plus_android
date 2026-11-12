// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Glossário único de valores (REDESIGN-COERENCIA.md): todas as telas (Início, Lançamentos lista e calendário,
 * Resumo, widget) leem estes números, para que o mesmo valor tenha o mesmo nome e a mesma conta em qualquer lugar.
 *
 * - **Realizado**: o que já entrou ou saiu das contas (pago ou recebido). Compras no cartão não entram: o que sai
 *   é o pagamento da fatura. Reservas para meta ficam à parte ("guardado em metas"), nunca como despesa.
 * - **A receber**: receitas pendentes fora do cartão (no mês atual, também as atrasadas) e recorrências previstas.
 * - **A pagar**: despesas pendentes fora do cartão (no mês atual, também as atrasadas), faturas abertas que vencem
 *   no mês e recorrências previstas. Compras no cartão entram pela fatura.
 * - **Reservado para metas**: aportes mensais planejados que ainda não foram feitos no mês. O que já foi guardado
 *   já saiu do saldo e não é descontado de novo.
 * - **Transferência entre contas**: muda o saldo de cada conta, mas não entra no realizado nem nas pendências.
 * - **Saldo atual**: saldo das contas hoje ([Finance.currentBalance]).
 * - **Previsto no fim do mês**: saldo atual + a receber − a pagar − reservado para metas.
 */
object Totals {
    enum class When { PAST, CURRENT, FUTURE }

    data class Month(
        val ym: YearMonth,
        val whenIs: When,
        /** saldo no começo da conta: saldo atual (mês atual), previsto no fim do mês anterior (futuro), saldo no fim do mês anterior (passado) */
        val opening: Cents,
        val realizedIn: Cents,
        val realizedOut: Cents,
        /** guardado em metas − retirado das metas no mês (já fora do saldo) */
        val goalsSaved: Cents,
        val toReceive: Cents,
        val toPay: Cents,
        val goalsPlanned: Cents,
        /** previsto no fim do mês (atual e futuro) ou saldo no fim do mês (passado) */
        val closing: Cents,
    ) {
        /** resultado do mês só com o realizado (visão isolada): entrou − saiu − guardado */
        val result: Cents get() = realizedIn - realizedOut - goalsSaved
        /** resultado do mês com o que falta (visão isolada) */
        val resultForecast: Cents get() = result + toReceive - toPay - goalsPlanned
    }

    private fun inMonth(d: LocalDate, ym: YearMonth) = d.ym() == ym

    /** Aportes planejados (Meta.monthly) ainda não feitos no mês [ym]; 0 em meses passados. Limitado ao que falta para a meta. */
    fun goalsPlanned(s: AppState, ym: YearMonth, today: LocalDate): Cents {
        val cur = today.ym()
        if (ym.isBefore(cur)) return 0L
        var total = 0L
        for (g in s.goals) {
            if (g.monthly <= 0) continue
            var left = maxOf(0L, g.target - g.saved)
            if (left == 0L) continue
            val doneNow = s.txs.filter { it.goalId == g.id && it.paid && inMonth(it.date, cur) }
                .sumOf { if (it.kind == Kind.EXPENSE) it.value else -it.value }
            var m = cur
            while (!m.isAfter(ym) && left > 0) {
                val want = if (m == cur) maxOf(0L, g.monthly - doneNow) else g.monthly
                val take = minOf(want, left)
                if (m == ym) total += take
                left -= take
                m = m.plusMonths(1)
            }
        }
        return total
    }

    /** Previsto ao fim do dia [d]: [Finance.futureBalance] − aportes planejados dos meses já encerrados até [d] (contam no último dia do mês). */
    fun forecastAt(s: AppState, d: LocalDate, today: LocalDate): Cents {
        var c = Finance.futureBalance(s, d, today)
        var m = today.ym()
        while (!m.atEndOfMonth().isAfter(d)) { c -= goalsPlanned(s, m, today); m = m.plusMonths(1) }
        return c
    }

    fun month(s: AppState, ym: YearMonth, today: LocalDate): Month {
        val cur = today.ym()
        val whenIs = when { ym.isBefore(cur) -> When.PAST; ym == cur -> When.CURRENT; else -> When.FUTURE }
        val ids = s.accounts.mapTo(HashSet()) { it.id }
        var inc = 0L; var out = 0L; var goals = 0L
        for (t in s.txs) {
            if (!t.paid || t.isCard || t.accountId !in ids || !inMonth(t.date, ym)) continue
            when {
                // transferência entre contas: sai de uma e entra na outra; não é entrada nem saída do mês
                t.isTransfer -> {}
                t.isGoal -> goals += if (t.kind == Kind.EXPENSE) t.value else -t.value
                t.kind == Kind.INCOME -> inc += t.value
                else -> out += t.value
            }
        }
        // pendências: no mês atual, também as atrasadas (é o que o previsto desconta)
        val end = ym.atEndOfMonth()
        var rec = 0L; var pay = 0L
        for (t in s.txs) {
            if (t.paid || t.isCard) continue
            val counts = if (whenIs == When.CURRENT) !t.date.isAfter(end) else inMonth(t.date, ym)
            if (!counts) continue
            if (t.kind == Kind.INCOME) rec += t.value else pay += t.value
        }
        for (card in s.cards) for (inv in Finance.cardStatus(s, card, today).invoices) {
            if (inv.open <= 0) continue
            val counts = if (whenIs == When.CURRENT) !inv.due.isAfter(end) else inMonth(inv.due, ym)
            if (counts) pay += inv.open
        }
        if (whenIs != When.PAST) for (t in Projection.between(s, ym.atDay(1), end, today)) {
            if (t.isCard) continue
            if (t.kind == Kind.INCOME) rec += t.value else pay += t.value
        }
        val planned = goalsPlanned(s, ym, today)
        val opening = when (whenIs) {
            When.CURRENT -> Finance.currentBalance(s)
            When.FUTURE -> forecastAt(s, ym.minusMonths(1).atEndOfMonth(), today)
            When.PAST -> Finance.balanceAt(s, ym.minusMonths(1).atEndOfMonth())
        }
        val closing = if (whenIs == When.PAST) Finance.balanceAt(s, end) else opening + rec - pay - planned
        return Month(ym, whenIs, opening, inc, out, goals, rec, pay, planned, closing)
    }
}
