// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Radar: saldo previsto dia a dia, "Posso comprar?" e alternativas. Só faz contas: nada aqui grava dados.
 *
 * Usa exatamente o que [Finance.futureBalance] usa (saldo atual, pendentes, faturas em aberto e recorrências
 * previstas), só que somado dia a dia; o último dia do Radar é sempre igual ao saldo previsto naquela data.
 * Regras e exemplos em RADAR.md.
 */
object Radar {
    const val DAYS = 30
    const val MAX_DAYS = 120
    /** o simulador olha até 30 dias depois do último efeito da compra (ex.: a fatura em que ela cai) */
    private const val AFTER = 30

    /** De onde vem cada movimento previsto (é o rótulo mostrado na tela). */
    enum class Source { PENDING, OVERDUE, RECURRING, INVOICE, SIMULATED }

    data class Event(val date: LocalDate, val delta: Cents, val desc: String, val source: Source, val refId: String = "")
    /**
     * [balance] = saldo no fim do dia; [low] = o menor ponto do dia: as saídas contam antes das entradas
     * (o salário que cai no mesmo dia não cobre uma conta que vence de manhã).
     */
    data class Day(val date: LocalDate, val balance: Cents, val low: Cents = balance)

    /** OK = nunca abaixo da reserva; LOW = abaixo da reserva, sem ficar negativo; NEGATIVE = algum dia negativo. */
    enum class Level { OK, LOW, NEGATIVE }

    class Result(val start: Cents, val days: List<Day>, val events: List<Event>, val reserve: Cents) {
        /** menor saldo (o primeiro dia, se empatar) */
        val min: Day = days.reduce { a, b -> if (b.low < a.low) b else a }
        val firstNegative: Day? = days.firstOrNull { it.low < 0 }
        val firstBelowReserve: Day? = if (reserve > 0) days.firstOrNull { it.low < reserve } else null
        val level: Level = when { firstNegative != null -> Level.NEGATIVE; firstBelowReserve != null -> Level.LOW; else -> Level.OK }
        /** quanto o menor saldo fica acima (+) ou abaixo (−) da reserva */
        val margin: Cents get() = min.low - reserve
        val end: Day get() = days.last()
        val hasIncome: Boolean get() = events.any { it.delta > 0 }
        /** o dia que mais importa: o primeiro negativo, senão o menor saldo */
        val worst: Day get() = firstNegative ?: min
    }

    // ------------------------------------------------------------------ movimentos previstos

    /** Movimentos previstos de hoje até [until]; os atrasados (pendentes ou faturas vencidas) entram hoje. */
    fun events(s: AppState, today: LocalDate, until: LocalDate): List<Event> {
        val out = ArrayList<Event>()
        for (t in s.txs) {
            if (t.isCard || t.paid || t.date.isAfter(until)) continue
            val late = t.date.isBefore(today)
            out.add(Event(if (late) today else t.date, signed(t), t.desc, if (late) Source.OVERDUE else Source.PENDING, t.id))
        }
        for (card in s.cards) for (inv in Finance.cardStatus(s, card, today).invoices) {
            if (inv.open <= 0 || inv.due.isAfter(until)) continue
            val late = inv.due.isBefore(today)
            out.add(Event(if (late) today else inv.due, -inv.open, "Fatura ${card.name}", if (late) Source.OVERDUE else Source.INVOICE, card.id))
        }
        for (t in Projection.between(s, today, until, today)) if (!t.isCard) out.add(Event(t.date, signed(t), t.desc, Source.RECURRING, t.recurringId))
        return out
    }

    private fun signed(t: Tx): Cents = if (t.kind == Kind.INCOME) t.value else -t.value

    /** Radar dos próximos [days] dias (hoje incluído), com [extra] movimentos simulados. */
    fun run(s: AppState, today: LocalDate, days: Int = DAYS, reserve: Cents = s.reserve, extra: List<Event> = emptyList()): Result =
        Engine(s, today).run(days, reserve, extra)

    /**
     * Calcula os movimentos previstos uma vez (até [MAX_DAYS]) e refaz só a soma em cada simulação:
     * as alternativas testam dezenas de datas sem reler todos os lançamentos.
     */
    class Engine(s: AppState, private val today: LocalDate) {
        private val start = Finance.currentBalance(s)
        private val base = events(s, today, today.plusDays(MAX_DAYS.toLong()))

        fun run(days: Int, reserve: Cents, extra: List<Event> = emptyList()): Result {
            val n = days.coerceIn(1, MAX_DAYS)
            val until = today.plusDays(n.toLong())
            val ev = (base + extra.map { if (it.date.isBefore(today)) it.copy(date = today) else it })
                .filter { !it.date.isAfter(until) }
                .sortedWith(compareBy<Event> { it.date }.thenBy { it.delta })
            var bal = start
            var i = 0
            val out = ArrayList<Day>(n + 1)
            for (k in 0..n) {
                val d = today.plusDays(k.toLong())
                var low = bal
                while (i < ev.size && !ev[i].date.isAfter(d)) { bal += ev[i].delta; if (ev[i].delta < 0) low += ev[i].delta; i++ }
                out.add(Day(d, bal, minOf(low, bal)))
            }
            return Result(start, out, ev, maxOf(0L, reserve))
        }
    }

    /** Os movimentos que explicam a queda até o pior dia (até [limit], por data) e a primeira entrada depois dele. */
    fun causes(r: Result, limit: Int = 4): List<Event> {
        val worst = r.worst.date
        val drops = r.events.filter { it.delta < 0 && !it.date.isAfter(worst) }.sortedBy { it.delta }.take(limit).sortedBy { it.date }
        val recovery = r.events.firstOrNull { it.delta > 0 && it.date.isAfter(worst) }
        return drops + listOfNotNull(recovery)
    }

    // ------------------------------------------------------------------ posso comprar?

    /** Compra simulada: à vista na conta, ou no cartão (em [parcels] parcelas, que caem nas faturas). */
    data class Purchase(
        val value: Cents,
        val date: LocalDate,
        val accountId: String = "",
        val cardId: String = "",
        val parcels: Int = 1,
        val desc: String = "Compra",
    )

    /** Efeito da compra no saldo: à vista sai no dia; no cartão, cada parcela sai no vencimento da sua fatura. */
    fun purchaseEvents(s: AppState, p: Purchase): List<Event> {
        val desc = p.desc.ifBlank { "Compra" }
        val card = s.card(p.cardId) ?: return listOf(Event(p.date, -p.value, desc, Source.SIMULATED))
        val n = p.parcels.coerceIn(1, 60)
        val values = Finance.splitInstallments(p.value, n)
        return values.mapIndexed { i, v ->
            val day = if (i == 0) p.date else p.date.plusMonthsClamped(i.toLong())
            val due = Finance.invoiceDue(card, Finance.invoiceYm(card, day))
            Event(due, -v, if (n > 1) "$desc (${i + 1}/$n)" else desc, Source.SIMULATED, card.id)
        }
    }

    /** Quantos dias olhar: no mínimo [DAYS], e até [AFTER] dias depois do último efeito simulado. */
    fun horizon(today: LocalDate, extra: List<Event>): Int {
        val last = extra.maxOfOrNull { it.date } ?: return DAYS
        return (ChronoUnit.DAYS.between(today, last).toInt() + AFTER).coerceIn(DAYS, MAX_DAYS)
    }

    data class Check(val purchase: Purchase, val base: Result, val sim: Result) {
        /** quanto a compra muda o menor saldo (≤ 0) */
        val impact: Cents get() = sim.min.low - base.min.low
        /** sem nenhuma receita prevista no período: a previsão provavelmente está incompleta */
        val noIncome: Boolean get() = !base.hasIncome
    }

    fun check(s: AppState, today: LocalDate, p: Purchase, reserve: Cents = s.reserve): Check = check(Engine(s, today), s, today, p, reserve)

    private fun check(e: Engine, s: AppState, today: LocalDate, p: Purchase, reserve: Cents): Check {
        val extra = purchaseEvents(s, p)
        val days = horizon(today, extra)
        return Check(p, e.run(days, reserve), e.run(days, reserve, extra))
    }

    // ------------------------------------------------------------------ alternativas

    enum class AltKind { POSTPONE, PARCELS, GOAL }

    /** Uma saída para a compra: [purchase] é a compra como ficaria (outra data ou parcelada); [goalId]/[amount] = retirar da meta. */
    data class Alternative(val kind: AltKind, val result: Result, val purchase: Purchase, val goalId: String = "", val goalName: String = "", val amount: Cents = 0)

    /**
     * Até três saídas quando a compra deixa o saldo negativo ou abaixo da reserva. Só usa o que o Finan+ conhece:
     * adiar a compra, parcelar no cartão ou tirar dinheiro de uma meta. Nada é executado.
     */
    fun alternatives(s: AppState, today: LocalDate, p: Purchase, reserve: Cents = s.reserve): List<Alternative> {
        val e = Engine(s, today)
        val now = check(e, s, today, p, reserve)
        if (now.sim.level == Level.OK) return emptyList()
        val out = ArrayList<Alternative>()
        val rank = { r: Result -> when (r.level) { Level.OK -> 2; Level.LOW -> 1; Level.NEGATIVE -> 0 } }
        val better = { r: Result -> rank(r) > rank(now.sim) }

        // 1) adiar: o primeiro dia (até 60 dias depois) em que a compra não deixa o saldo negativo; se der, respeitando a reserva
        var firstOk: Alternative? = null
        var firstLow: Alternative? = null
        for (k in 1..60) {
            val q = p.copy(date = p.date.plusDays(k.toLong()))
            if (ChronoUnit.DAYS.between(today, q.date) > MAX_DAYS - AFTER) break
            val r = check(e, s, today, q, reserve).sim
            if (r.level == Level.OK) { firstOk = Alternative(AltKind.POSTPONE, r, q); break }
            if (r.level == Level.LOW && firstLow == null) firstLow = Alternative(AltKind.POSTPONE, r, q)
        }
        (firstOk ?: firstLow)?.takeIf { better(it.result) }?.let(out::add)

        // 2) parcelar no cartão: o menor número de parcelas que resolve (em qualquer cartão)
        if (s.cards.isNotEmpty()) {
            var best: Alternative? = null
            loop@ for (n in listOf(2, 3, 4, 5, 6, 8, 10, 12)) {
                if (p.cardId.isNotEmpty() && n <= p.parcels) continue
                if (p.value < n) break
                for (c in s.cards) {
                    val q = p.copy(cardId = c.id, parcels = n)
                    val r = check(e, s, today, q, reserve).sim
                    if (best == null || rank(r) > rank(best.result)) best = Alternative(AltKind.PARCELS, r, q)
                    if (r.level == Level.OK) break@loop
                }
            }
            best?.takeIf { better(it.result) }?.let(out::add)
        }

        // 3) usar dinheiro de uma meta: o que falta para não ficar negativo (e, se der, para respeitar a reserva)
        val goals = s.goals.filter { it.saved > 0 }
        if (goals.isNotEmpty()) {
            val target = if (reserve > 0) reserve else 0L
            val need = target - now.sim.min.low
            val needZero = -now.sim.min.low
            val g = goals.filter { it.saved >= need }.minByOrNull { it.saved }
                ?: goals.filter { it.saved >= needZero }.minByOrNull { it.saved }
            if (g != null) {
                val amount = minOf(g.saved, need)
                val back = Event(today, amount, "Retirada da meta › ${g.name}", Source.SIMULATED, g.id)
                val extra = purchaseEvents(s, p) + back
                val r = e.run(horizon(today, extra), reserve, extra)
                if (better(r)) out.add(Alternative(AltKind.GOAL, r, p, g.id, g.name, amount))
            }
        }
        return out
    }
}
