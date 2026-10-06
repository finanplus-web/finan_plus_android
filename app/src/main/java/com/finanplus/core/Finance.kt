// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Dia [day] do mês, limitado ao último dia (31 → 28/29/30). */
fun YearMonth.dayClamped(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))

fun LocalDate.ym(): YearMonth = YearMonth.from(this)

/** Soma o mês mantendo o dia quando possível (31/jan + 1 → 28/fev). */
fun LocalDate.plusMonthsClamped(n: Long): LocalDate = ym().plusMonths(n).dayClamped(dayOfMonth)

fun List<Tx>.totalOf(kind: Kind? = null): Cents = fold(0L) { s, t -> if (kind == null || t.kind == kind) s + t.value else s }

data class Invoice(
    val ym: YearMonth,
    val total: Cents,
    val paid: Cents,
    val closeDate: LocalDate,
    val due: LocalDate,
    val closed: Boolean,
) { val open: Cents get() = total - paid }

data class CardStatus(val used: Cents, val available: Cents, val credit: Cents, val invoices: List<Invoice>, val current: Invoice?)

data class GoalPlan(
    val remaining: Cents,
    val done: Boolean,
    /** quanto guardar por mês para chegar no prazo */
    val needed: Cents?,
    /** mês previsto de conclusão pelo plano mensal */
    val eta: YearMonth?,
    val late: Boolean,
    val pastDue: Boolean,
)

data class Flow(val income: Cents, val expense: Cents) {
    val balance get() = income - expense
}

enum class ReminderType { BILL_OVERDUE, BILL_DUE, INCOME_DUE, INVOICE_DUE }
data class Reminder(val type: ReminderType, val title: String, val amount: Cents, val date: LocalDate, val refId: String)

object Finance {
    // ---------------- cartões e faturas ----------------
    /** Mês da fatura em que entra uma compra feita em [date]: após o fechamento vai para a seguinte. */
    fun invoiceYm(card: Card, date: LocalDate): YearMonth {
        val ym = date.ym()
        return if (date.dayOfMonth > minOf(card.close, ym.lengthOfMonth())) ym.plusMonths(1) else ym
    }

    /** Vencimento da fatura do mês [ym]: mesmo mês se vence depois do fechamento, senão no mês seguinte. */
    fun invoiceDue(card: Card, ym: YearMonth): LocalDate = (if (card.due > card.close) ym else ym.plusMonths(1)).dayClamped(card.due)

    fun invoiceClose(card: Card, ym: YearMonth): LocalDate = ym.dayClamped(card.close)

    /** Limite usado inclui parcelas futuras; pagamentos abatem da fatura mais antiga. */
    fun cardStatus(s: AppState, card: Card, today: LocalDate): CardStatus {
        val purchases = s.txs.filter { it.cardId == card.id && it.kind == Kind.EXPENSE }
        val paid = s.txs.filter { it.cardPayment == card.id && it.paid }.totalOf()
        val byYm = sortedMapOf<YearMonth, Long>()
        for (p in purchases) { val k = invoiceYm(card, p.date); byYm[k] = (byYm[k] ?: 0L) + p.value }
        var left = paid
        val invoices = byYm.map { (ym, total) ->
            val pay = minOf(left, total); left -= pay
            val close = invoiceClose(card, ym)
            Invoice(ym, total, pay, close, invoiceDue(card, ym), today.isAfter(close))
        }
        val spent = purchases.totalOf()
        val used = maxOf(0L, spent - paid)
        val current = invoices.firstOrNull { it.open > 0 && it.closed } ?: invoices.firstOrNull { it.open > 0 && !it.closed }
        return CardStatus(used, maxOf(0L, card.limit - used), maxOf(0L, paid - spent), invoices, current)
    }

    // ---------------- saldos ----------------
    fun accountBalance(s: AppState, a: Account): Cents = a.initial + s.txs
        .filter { it.accountId == a.id && !it.isCard && it.paid }
        .fold(0L) { n, t -> if (t.kind == Kind.INCOME) n + t.value else n - t.value }

    /** Saldo atual = soma das contas (saldo inicial + realizados fora do cartão). */
    fun currentBalance(s: AppState): Cents = s.accounts.fold(0L) { n, a -> n + accountBalance(s, a) }

    /** Saldo previsto em [until]: pendências das contas + faturas em aberto que vencem até lá. */
    fun futureBalance(s: AppState, until: LocalDate, today: LocalDate): Cents {
        var c = currentBalance(s)
        for (t in s.txs) {
            if (t.isCard || t.paid || t.date.isAfter(until)) continue
            c += if (t.kind == Kind.INCOME) t.value else -t.value
        }
        for (card in s.cards) for (inv in cardStatus(s, card, today).invoices) if (inv.open > 0 && !inv.due.isAfter(until)) c -= inv.open
        return c
    }

    // ---------------- recorrências ----------------
    /**
     * Gera os lançamentos das recorrências até o mês de [today], recuperando meses em que o app
     * não foi aberto (no máximo 24 por vez). Nunca gera antes da data de início.
     */
    fun generateRecurring(s: AppState, today: LocalDate): Pair<AppState, Int> {
        val cur = today.ym()
        val newTxs = ArrayList<Tx>()
        val recs = s.recurring.map { r ->
            if (!r.active) return@map r
            val startYm = r.start?.ym() ?: r.last?.plusMonths(1) ?: cur
            var m = r.last?.plusMonths(1)?.takeIf { it.isAfter(startYm) } ?: startYm
            var last = r.last
            var guard = 0
            while (!m.isAfter(cur) && guard < 24) {
                val date = m.dayClamped(r.day)
                last = m
                if (r.start == null || !date.isBefore(r.start)) {
                    newTxs.add(Tx(Ids.new(), r.kind, r.value, date, r.desc, r.category, r.cardId.isNotEmpty(), r.accountId, r.cardId, recurringId = r.id))
                }
                m = m.plusMonths(1); guard++
            }
            if (last == r.last) r else r.copy(last = last)
        }
        return if (newTxs.isEmpty() && recs == s.recurring) s to 0 else s.copy(txs = s.txs + newTxs, recurring = recs) to newTxs.size
    }

    // ---------------- parcelas ----------------
    /** Divide [total] em [n] parcelas; a diferença de centavos fica na primeira. */
    fun splitInstallments(total: Cents, n: Int): List<Cents> {
        val base = total / n; val rest = total - base * n
        return List(n) { i -> base + if (i == 0) rest else 0 }
    }

    // ---------------- metas ----------------
    fun goalPlan(g: Goal, today: LocalDate): GoalPlan {
        val remaining = maxOf(0L, g.target - g.saved)
        if (remaining == 0L) return GoalPlan(0, true, null, null, false, false)
        var needed: Cents? = null; var pastDue = false
        if (g.deadline != null) {
            if (g.deadline.isBefore(today)) pastDue = true
            else {
                val months = ChronoUnit.MONTHS.between(today.ym(), g.deadline.ym()) + 1
                needed = (remaining + months - 1) / months
            }
        }
        var eta: YearMonth? = null; var late = false
        if (g.monthly > 0) {
            val months = (remaining + g.monthly - 1) / g.monthly
            eta = today.ym().plusMonths(months - 1)
            if (g.deadline != null && eta.isAfter(g.deadline.ym())) late = true
        }
        return GoalPlan(remaining, false, needed, eta, late, pastDue)
    }

    // ---------------- resumos ----------------
    fun flow(list: List<Tx>): Flow { val r = list.filter { it.isFlow && it.paid }; return Flow(r.totalOf(Kind.INCOME), r.totalOf(Kind.EXPENSE)) }

    fun monthFlow(s: AppState, ym: YearMonth): Flow = flow(s.txs.filter { it.date.ym() == ym })

    /** Despesas do mês por categoria, incluindo pendentes (para limites). */
    fun budgetUsage(s: AppState, ym: YearMonth): Map<String, Cents> = s.txs
        .filter { it.kind == Kind.EXPENSE && it.isFlow && it.date.ym() == ym }
        .groupBy { it.category }.mapValues { (_, l) -> l.totalOf() }

    /** Despesas realizadas por categoria no período, em ordem decrescente. */
    fun categoryTotals(s: AppState, from: LocalDate?, to: LocalDate?): List<Pair<String, Cents>> = s.txs
        .filter { it.kind == Kind.EXPENSE && it.paid && it.isFlow && (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) }
        .groupBy { it.category }.map { (k, l) -> k to l.totalOf() }.sortedByDescending { it.second }

    fun lastMonths(s: AppState, today: LocalDate, n: Int = 6): List<Pair<YearMonth, Flow>> =
        (n - 1 downTo 0).map { i -> val ym = today.ym().minusMonths(i.toLong()); ym to monthFlow(s, ym) }

    // ---------------- lembretes (notificações e widget) ----------------
    /** Contas pendentes atrasadas ou que vencem em até [days] dias, receitas a receber e faturas a vencer. */
    fun reminders(s: AppState, today: LocalDate, days: Long = 2): List<Reminder> {
        val limit = today.plusDays(days)
        val out = ArrayList<Reminder>()
        for (t in s.txs) {
            if (t.paid || t.isCard || t.date.isAfter(limit)) continue
            val type = when {
                t.kind == Kind.INCOME -> if (t.date.isAfter(today)) continue else ReminderType.INCOME_DUE
                t.date.isBefore(today) -> ReminderType.BILL_OVERDUE
                else -> ReminderType.BILL_DUE
            }
            out.add(Reminder(type, t.desc, t.value, t.date, t.id))
        }
        for (c in s.cards) for (inv in cardStatus(s, c, today).invoices) {
            if (inv.open > 0 && !inv.due.isAfter(limit) && inv.closed) out.add(Reminder(ReminderType.INVOICE_DUE, "Fatura ${c.name}", inv.open, inv.due, c.id))
        }
        return out.sortedBy { it.date }
    }

    /** Próximo compromisso (para o widget): conta pendente ou fatura com vencimento mais próximo a partir de hoje. */
    fun nextDue(s: AppState, today: LocalDate): Reminder? {
        val bills = s.txs.filter { !it.paid && !it.isCard && it.kind == Kind.EXPENSE }
            .map { Reminder(if (it.date.isBefore(today)) ReminderType.BILL_OVERDUE else ReminderType.BILL_DUE, it.desc, it.value, it.date, it.id) }
        val invs = s.cards.flatMap { c -> cardStatus(s, c, today).invoices.filter { it.open > 0 }.map { Reminder(ReminderType.INVOICE_DUE, "Fatura ${c.name}", it.open, it.due, c.id) } }
        return (bills + invs).minByOrNull { it.date }
    }
}

object Csv {
    fun cell(v: Any?): String {
        var s = v?.toString() ?: ""
        if (s.isNotEmpty() && s[0] in "=+-@\t\r") s = "'$s" // impede fórmula no Excel/Sheets
        return "\"" + s.replace("\"", "\"\"") + "\""
    }

    fun build(s: AppState): String {
        val head = listOf("data", "tipo", "categoria", "descricao", "valor", "situacao", "conta", "cartao")
        val rows = s.txs.sortedBy { it.date }.map { t ->
            listOf(
                cell(t.date), cell(if (t.kind == Kind.INCOME) "receita" else "despesa"), cell(t.category), cell(t.desc),
                cell(Money.input(t.value)), cell(if (t.paid) "realizado" else "pendente"),
                cell(if (t.isCard) "" else s.account(t.accountId)?.name ?: ""),
                cell(s.card(t.cardId.ifEmpty { t.cardPayment })?.name ?: ""),
            ).joinToString(";")
        }
        return "﻿" + head.joinToString(";") + "\r\n" + rows.joinToString("\r\n")
    }
}
