// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.assist

import com.finanplus.core.AppState
import com.finanplus.core.Cents
import com.finanplus.core.Kind
import com.finanplus.core.Tx
import com.finanplus.core.ym
import java.time.LocalDate
import java.time.YearMonth

enum class InsightType(val label: String) {
    DUPLICATE("Possível duplicado"),
    PRICE_UP("Aumento de preço"),
    LIMIT_PACE("Ritmo do limite"),
    OVER_INCOME("Ritmo do mês"),
    CATEGORY_SPIKE("Acima da média"),
    SMALL_SPENDS("Pequenos gastos"),
    SUBSCRIPTIONS("Gastos fixos"),
}

/**
 * Uma dica. [why] explica a regra e os números usados (botão "Por quê?").
 * [query]/[from]/[to] permitem abrir a lista de lançamentos já filtrada.
 */
data class Insight(
    val id: String,
    val type: InsightType,
    val title: String,
    val text: String,
    val why: String,
    val priority: Int,
    val query: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

/**
 * Resumo do mês. [highlights]: as até 2 frases mais úteis para o cartão do Início, nesta ordem de prioridade:
 * contas em atraso, contas a pagar, quanto já gastou, quanto falta receber, quanto entrou.
 * "Ainda não há despesas" e o fechamento do mês anterior ficam só no resumo completo.
 */
data class MonthReport(val title: String, val lines: List<String>, val why: String, val highlights: List<String> = lines.take(2))

/**
 * Resumo do mês e dicas de economia. Tudo é cálculo sobre os lançamentos (nada é "inventado"):
 * cada regra está descrita no próprio texto de [Insight.why] e em ASSISTENTE.md.
 *
 * Convenções (iguais às dos Relatórios do app):
 * - despesa = lançamento de despesa que não é pagamento de fatura;
 * - compras no cartão contam na data da compra;
 * - "realizado" = pago/recebido.
 */
object Insights {
    // limites das regras (documentados em ASSISTENTE.md)
    const val SMALL_VALUE: Cents = 20_00
    const val SMALL_MIN_COUNT = 10
    const val SPIKE_RATIO = 1.30
    const val SPIKE_MIN_DIFF: Cents = 50_00
    const val PACE_MIN_DAY = 7
    /** ritmo: mínimo de despesas variáveis pagas no mês para projetar (mês inteiro / categoria com limite) */
    const val PACE_MIN_COUNT = 5
    const val PACE_MIN_COUNT_CAT = 3
    /** uma despesa acima desta parte do gasto variável é "pontual": conta uma vez, não é multiplicada pelos dias */
    const val ONE_OFF_SHARE = 0.5
    const val SUB_MIN_MONTHS = 3
    const val SUB_TOLERANCE = 0.30
    const val PRICE_UP_RATIO = 1.05
    const val DUP_DAYS = 60L

    private fun expenses(s: AppState) = s.txs.filter { it.kind == Kind.EXPENSE && it.isFlow }
    private fun sum(l: List<Tx>): Cents = l.fold(0L) { a, t -> a + t.value }
    private fun inMonthUntil(t: Tx, ym: YearMonth, lastDay: Int) = t.date.ym() == ym && t.date.dayOfMonth <= lastDay
    private fun isFixed(t: Tx) = t.recurringId.isNotEmpty() || t.groupId.isNotEmpty()

    // ------------------------------------------------------------------ resumo do mês

    fun report(s: AppState, today: LocalDate, money: MoneyFmt): MonthReport {
        val ym = today.ym()
        val day = today.dayOfMonth
        val prev = ym.minusMonths(1)
        val prevDay = minOf(day, prev.lengthOfMonth())
        val exp = expenses(s)
        val curExp = exp.filter { it.paid && inMonthUntil(it, ym, day) }
        val prevExp = exp.filter { it.paid && inMonthUntil(it, prev, prevDay) }
        val curInc = s.txs.filter { it.kind == Kind.INCOME && it.paid && inMonthUntil(it, ym, day) }
        val spent = sum(curExp); val before = sum(prevExp); val income = sum(curInc)
        val lines = ArrayList<String>()
        var spentLine: String? = null; var incomeLine: String? = null; var pendingLine: String? = null
        var receiveLine: String? = null; var lateLine: String? = null

        if (spent == 0L) lines.add("Ainda não há despesas realizadas em ${Br.month(ym)}.")
        else {
            var l = "Até hoje (dia $day) você gastou ${money(spent)} em ${Br.month(ym)}."
            if (before > 0) {
                val c = (spent - before) * 100.0 / before
                l += when {
                    Math.abs(c) < 3 -> " Praticamente o mesmo que no mesmo período de ${Br.month(prev)} (${money(before)})."
                    c > 0 -> " São ${Br.pct(c)} a mais que no mesmo período de ${Br.month(prev)} (${money(before)})."
                    else -> " São ${Br.pct(c)} a menos que no mesmo período de ${Br.month(prev)} (${money(before)})."
                }
            }
            lines.add(l); spentLine = l
        }
        if (income > 0) {
            incomeLine = if (income >= spent) "Entraram ${money(income)}; sobram ${money(income - spent)} até agora."
            else "Entraram ${money(income)}; as despesas já passam as receitas em ${money(spent - income)}."
            lines.add(incomeLine)
        }
        curExp.groupBy { it.category }.mapValues { sum(it.value) }.maxByOrNull { it.value }?.let { (cat, v) ->
            if (spent > 0) lines.add("A maior categoria é $cat: ${money(v)} (${Br.pct(v * 100.0 / spent)} das despesas).")
        }
        val pending = exp.filter { !it.paid && !it.isCard && it.date.ym() == ym && !it.date.isBefore(today) }
        if (pending.isNotEmpty()) {
            pendingLine = "Ainda faltam ${money(sum(pending))} em ${Br.plural(pending.size, "conta", "contas")} a pagar até o fim do mês."
            lines.add(pendingLine)
        }
        val toReceive = s.txs.filter { it.kind == Kind.INCOME && !it.paid && it.date.ym() == ym }
        if (toReceive.isNotEmpty()) {
            receiveLine = "A receber neste mês: ${money(sum(toReceive))} em ${Br.plural(toReceive.size, "lançamento", "lançamentos")}."
            lines.add(receiveLine)
        }
        val late = exp.filter { !it.paid && !it.isCard && it.date.isBefore(today) }
        if (late.isNotEmpty()) {
            lateLine = "${Br.plural(late.size, "conta está", "contas estão")} em atraso (${money(sum(late))})."
            lines.add(lateLine)
        }
        if (day <= 7) {
            val pe = sum(exp.filter { it.paid && it.date.ym() == prev })
            val pi = sum(s.txs.filter { it.kind == Kind.INCOME && it.paid && it.date.ym() == prev })
            if (pe > 0 || pi > 0) lines.add("Fechamento de ${Br.month(prev)}: receitas ${money(pi)}, despesas ${money(pe)}, saldo ${money(pi - pe)}.")
        }
        return MonthReport(
            "Resumo de ${Br.monthYear(ym)}",
            lines,
            "Considera só lançamentos realizados (pagos ou recebidos) até hoje. Compras no cartão contam na data da compra; " +
                "pagamentos de fatura não contam como despesa nova. A comparação usa os mesmos dias (1 a $day) do mês anterior, para ser justa.",
            listOfNotNull(lateLine, pendingLine, spentLine, receiveLine, incomeLine).take(2),
        )
    }

    // ------------------------------------------------------------------ dicas

    fun tips(s: AppState, today: LocalDate, money: MoneyFmt): List<Insight> {
        val out = ArrayList<Insight>()
        out += duplicates(s, today, money)
        val subs = recurringExpenses(s, today)
        out += priceUps(subs, money)
        out += limitPace(s, today, money)
        overIncome(s, today, money)?.let { out += it }
        out += spikes(s, today, money)
        smallSpends(s, today, money)?.let { out += it }
        subscriptionsSummary(subs, today, money)?.let { out += it }
        return out.sortedByDescending { it.priority }
    }

    /** Mesma descrição + mesmo valor + mesma data, nos últimos 60 dias, sem ser parcela/recorrência. */
    fun duplicates(s: AppState, today: LocalDate, money: MoneyFmt): List<Insight> {
        val since = today.minusDays(DUP_DAYS)
        return expenses(s).filter { !it.date.isBefore(since) && !isFixed(it) }
            .groupBy { Triple(it.date, it.value, Text.key(it.desc)) }
            .filter { (k, l) -> k.third.isNotEmpty() && l.size >= 2 }
            .entries.sortedByDescending { it.key.first }.take(3)
            .map { (k, l) ->
                val t = l[0]
                Insight(
                    "dup:${k.first}:${k.second}:${k.third}", InsightType.DUPLICATE, "Possível lançamento duplicado",
                    "“${t.desc}” aparece ${l.size} vezes em ${Br.dayMonth(t.date)} com o mesmo valor (${money(t.value)}). Se foi lançado em dobro, exclua a cópia.",
                    "Regra: mesma descrição, mesmo valor e mesma data, nos últimos $DUP_DAYS dias, sem ser parcela nem recorrência. " +
                        "Se forem compras diferentes de verdade, dispense este aviso.",
                    10, query = t.desc, from = t.date, to = t.date,
                )
            }
    }

    /** Gasto que se repete uma vez por mês, com valor parecido, em ≥ 3 meses seguidos até o mês atual ou o anterior. */
    data class Monthly(val name: String, val key: String, val byMonth: List<Pair<YearMonth, Tx>>) {
        val last: Tx get() = byMonth.last().second
    }

    fun recurringExpenses(s: AppState, today: LocalDate): List<Monthly> {
        val cur = today.ym()
        val window = cur.minusMonths(5)
        val out = ArrayList<Monthly>()
        val groups = expenses(s).filter { it.groupId.isEmpty() && !it.date.ym().isBefore(window) && !it.date.ym().isAfter(cur) }
            .groupBy { Text.key(Categorizer.clean(it.desc)) }
        for ((key, list) in groups) {
            if (key.isEmpty()) continue
            val byM = list.groupBy { it.date.ym() }
            if (byM.values.any { it.size > 1 }) continue // várias vezes no mesmo mês: não é mensalidade
            val months = byM.keys.sorted()
            // sequência de meses seguidos que termina no mês atual ou no anterior
            val end = months.last()
            if (end != cur && end != cur.minusMonths(1)) continue
            var run = 1
            while (run < months.size && months[months.size - 1 - run] == end.minusMonths(run.toLong())) run++
            if (run < SUB_MIN_MONTHS) continue
            val seq = months.takeLast(run).map { it to byM[it]!!.first() }
            val vals = seq.map { it.second.value }.sorted()
            val median = vals[vals.size / 2].toDouble()
            if (vals.any { it < median * (1 - SUB_TOLERANCE) || it > median * (1 + SUB_TOLERANCE) }) continue
            out += Monthly(seq.last().second.desc.let(Categorizer::clean), key, seq)
        }
        return out.sortedByDescending { it.last.value }
    }

    fun priceUps(subs: List<Monthly>, money: MoneyFmt): List<Insight> = subs.mapNotNull { m ->
        if (m.byMonth.size < 2) return@mapNotNull null
        val (ymLast, last) = m.byMonth.last()
        val before = m.byMonth[m.byMonth.size - 2].second
        if (last.value < before.value * PRICE_UP_RATIO || last.value - before.value < 1_00) return@mapNotNull null
        val c = (last.value - before.value) * 100.0 / before.value
        Insight(
            "up:${m.key}:$ymLast", InsightType.PRICE_UP, "${m.name} ficou mais caro",
            "“${m.name}” passou de ${money(before.value)} para ${money(last.value)} em ${Br.month(ymLast)} (+${Br.pct(c)}). Vale conferir se houve reajuste ou mudança de plano.",
            "Regra: gasto mensal (1 vez por mês, ${m.byMonth.size} meses seguidos) cujo último valor ficou pelo menos ${Br.pct((PRICE_UP_RATIO - 1) * 100)} e R$ 1,00 acima do mês anterior.",
            9, query = m.name,
        )
    }

    fun subscriptionsSummary(subs: List<Monthly>, today: LocalDate, money: MoneyFmt): Insight? {
        if (subs.isEmpty()) return null
        val total = subs.fold(0L) { a, m -> a + m.last.value }
        val list = subs.take(5).joinToString(", ") { "${it.name} (${money(it.last.value)})" } + if (subs.size > 5) " e mais ${subs.size - 5}" else ""
        return Insight(
            "subs:${today.ym()}:${subs.size}:$total", InsightType.SUBSCRIPTIONS, "Gastos fixos do mês",
            "Encontrei ${Br.plural(subs.size, "gasto que se repete", "gastos que se repetem")} todo mês, somando ${money(total)} por mês (${money(total * 12)} por ano): $list. " +
                "Gastos fixos pesam o ano inteiro: vale revisar planos, assinaturas e tarifas que dá para reduzir.",
            "Regra: mesma descrição, uma vez por mês, em pelo menos $SUB_MIN_MONTHS meses seguidos (até este mês ou o anterior), " +
                "com valores a até ${Br.pct(SUB_TOLERANCE * 100)} da mediana. Parcelas não entram. O valor mensal é o do último lançamento.",
            4,
        )
    }

    /** Projeção do mês: compromissos (recorrências, parcelas e contas agendadas) + gasto variável no ritmo diário atual. */
    data class Projection(val committed: Cents, val variable: Cents, val projected: Cents, val oneOff: Cents = 0, val count: Int = 0, val enough: Boolean = true)

    /**
     * Projeção das despesas até o fim do mês: compromissos (recorrências, parcelas e contas pendentes) pelo valor
     * + gasto variável pago até hoje ÷ dias passados × dias do mês. Uma despesa que sozinha passa de metade do gasto
     * variável é pontual: conta uma vez, sem ser multiplicada. Com menos de [minCount] despesas variáveis não há
     * "ritmo" para projetar (enough = false) e as dicas não aparecem.
     */
    fun project(list: List<Tx>, today: LocalDate, minCount: Int = PACE_MIN_COUNT): Projection {
        val ym = today.ym(); val day = today.dayOfMonth; val len = ym.lengthOfMonth()
        val month = list.filter { it.date.ym() == ym }
        val committed = sum(month.filter { isFixed(it) || !it.paid })
        val vars = month.filter { !isFixed(it) && it.paid && it.date.dayOfMonth <= day }
        val variable = sum(vars)
        val biggest = vars.maxOfOrNull { it.value } ?: 0L
        val oneOff = if (variable > 0 && biggest > variable * ONE_OFF_SHARE) biggest else 0L
        val projected = committed + oneOff + Math.round((variable - oneOff).toDouble() / day * len)
        return Projection(committed, variable, projected, oneOff, vars.size, vars.size >= minCount)
    }

    /** texto do "Por quê?" com a conta da projeção */
    fun projectionWhy(p: Projection, day: Int, len: Int, money: MoneyFmt, minCount: Int): String =
        "Conta: compromissos do mês (recorrências, parcelas e contas agendadas) ${money(p.committed)}" +
            (if (p.oneOff > 0) " + gasto pontual ${money(p.oneOff)} (conta uma vez) + resto do gasto variável até hoje ${money(p.variable - p.oneOff)}"
            else " + gasto variável até hoje ${money(p.variable)}") + " ÷ $day dias × $len dias. " +
            "Só é calculada a partir do dia $PACE_MIN_DAY e com pelo menos $minCount despesas variáveis pagas no mês."


    fun limitPace(s: AppState, today: LocalDate, money: MoneyFmt): List<Insight> {
        val ym = today.ym(); val day = today.dayOfMonth; val len = ym.lengthOfMonth()
        if (day < PACE_MIN_DAY || day >= len) return emptyList()
        val exp = expenses(s)
        return s.limits.mapNotNull { (cat, lim) ->
            val l = exp.filter { it.category == cat }
            val used = sum(l.filter { it.date.ym() == ym })
            if (used >= lim) return@mapNotNull null // já ultrapassado: o Início já mostra
            val p = project(l, today, PACE_MIN_COUNT_CAT)
            if (!p.enough || p.projected <= lim || p.projected - lim < 10_00) return@mapNotNull null
            val left = len - day
            val perDay = maxOf(0L, lim - used) / left
            Insight(
                "pace:$ym:$cat", InsightType.LIMIT_PACE, "$cat pode passar do limite",
                "No ritmo atual, $cat deve fechar ${Br.month(ym)} em cerca de ${money(p.projected)}, acima do limite de ${money(lim)}. " +
                    "Para ficar dentro, gaste até ${money(perDay)} por dia nos $left dias restantes.",
                projectionWhy(p, day, len, money, PACE_MIN_COUNT_CAT) + " Já usado: ${money(used)} de ${money(lim)}.",
                8, query = cat, from = ym.atDay(1), to = today,
            )
        }
    }

    fun overIncome(s: AppState, today: LocalDate, money: MoneyFmt): Insight? {
        val ym = today.ym(); val day = today.dayOfMonth; val len = ym.lengthOfMonth()
        if (day < PACE_MIN_DAY || day >= len) return null
        val income = sum(s.txs.filter { it.kind == Kind.INCOME && it.date.ym() == ym }) // inclui receitas previstas
        if (income <= 0) return null
        val p = project(expenses(s), today, PACE_MIN_COUNT)
        if (!p.enough || p.projected <= income) return null
        return Insight(
            "over:$ym", InsightType.OVER_INCOME, "Despesas podem passar das receitas",
            "No ritmo atual, as despesas de ${Br.month(ym)} chegam a cerca de ${money(p.projected)}, acima das receitas previstas para o mês (${money(income)}). " +
                "Diferença estimada: ${money(p.projected - income)}.",
            projectionWhy(p, day, len, money, PACE_MIN_COUNT) + " Receitas previstas = recebidas + a receber neste mês.",
            8, from = ym.atDay(1), to = today,
        )
    }

    fun spikes(s: AppState, today: LocalDate, money: MoneyFmt): List<Insight> {
        val ym = today.ym()
        val exp = expenses(s).filter { it.paid }
        val months = (1..3).map { ym.minusMonths(it.toLong()) }
        return exp.filter { it.date.ym() == ym && !it.date.isAfter(today) }.groupBy { it.category }.mapNotNull { (cat, curList) ->
            val cur = sum(curList)
            val hist = months.map { m -> sum(exp.filter { it.category == cat && it.date.ym() == m }) }
            if (hist.count { it > 0 } < 2) return@mapNotNull null
            val avg = hist.sum() / 3
            if (avg <= 0 || cur < avg * SPIKE_RATIO || cur - avg < SPIKE_MIN_DIFF) return@mapNotNull null
            val top = curList.sortedByDescending { it.value }.take(2).joinToString(", ") { "“${it.desc}” (${money(it.value)})" }
            Insight(
                "spike:$ym:$cat", InsightType.CATEGORY_SPIKE, "$cat acima do normal",
                "$cat já soma ${money(cur)} em ${Br.month(ym)}, ${Br.pct((cur - avg) * 100.0 / avg)} acima da sua média dos últimos 3 meses (${money(avg)}). Maiores: $top.",
                "Regra: gasto realizado da categoria neste mês ≥ ${Br.pct((SPIKE_RATIO - 1) * 100)} acima da média de " +
                    "${months.reversed().joinToString(", ") { Br.month(it) }} (${hist.reversed().joinToString(" + ") { money(it) }} ÷ 3) e pelo menos ${money(SPIKE_MIN_DIFF)} a mais. " +
                    "Precisa de dados em pelo menos 2 desses meses.",
                7, query = cat, from = ym.atDay(1), to = today,
            )
        }
    }

    fun smallSpends(s: AppState, today: LocalDate, money: MoneyFmt): Insight? {
        val ym = today.ym()
        val month = expenses(s).filter { it.paid && it.date.ym() == ym && !it.date.isAfter(today) }
        val small = month.filter { it.value <= SMALL_VALUE }
        if (small.size < SMALL_MIN_COUNT) return null
        val total = sum(small); val all = sum(month)
        val freq = small.groupBy { Text.key(it.desc) }.filter { it.key.isNotEmpty() && it.value.size >= 2 }
            .entries.sortedByDescending { it.value.size }.take(3)
            .joinToString(", ") { "“${it.value[0].desc}” (${it.value.size}×)" }
        return Insight(
            "small:$ym:${small.size}", InsightType.SMALL_SPENDS, "Pequenos gastos somando",
            "${small.size} compras de até ${money(SMALL_VALUE)} somaram ${money(total)} em ${Br.month(ym)} (${Br.pct(total * 100.0 / all)} das despesas)." +
                (if (freq.isNotEmpty()) " Os mais frequentes: $freq." else ""),
            "Regra: despesas realizadas de até ${money(SMALL_VALUE)} neste mês, quando passam de $SMALL_MIN_COUNT. Sozinhas parecem pouco; juntas mostram para onde vai o dinheiro.",
            5, from = ym.atDay(1), to = today,
        )
    }
}
