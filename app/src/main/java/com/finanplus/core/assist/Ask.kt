// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.assist

import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Tx
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Perguntas rápidas em português, respondidas com cálculo exato sobre os lançamentos.
 * Não é um modelo de linguagem: é um interpretador de palavras-chave. Por isso a resposta
 * sempre mostra "Como entendi" — o usuário vê exatamente o que foi considerado.
 *
 * Exemplos: "quanto gastei com mercado em agosto?", "maior gasto da semana",
 * "quanto recebi este ano", "saldo do mês passado", "quantas vezes usei uber nos últimos 30 dias".
 */
object Ask {
    enum class Intent(val label: String) { TOTAL("total"), MAX("maior lançamento"), COUNT("quantidade"), AVERAGE("média por dia"), BALANCE("saldo") }

    data class Period(val from: LocalDate, val to: LocalDate, val label: String)

    data class Parsed(
        val intent: Intent,
        val kind: Kind?,
        val period: Period,
        val category: String?,
        /** palavras usadas como filtro da descrição */
        val words: List<String>,
        /** palavras que não aparecem em nenhum lançamento e por isso foram ignoradas */
        val ignored: List<String> = emptyList(),
    )

    data class Answer(val text: String, val understood: String, val parsed: Parsed, val matches: List<Tx>)

    val EXAMPLES = listOf(
        "Quanto gastei este mês?",
        "Quanto gastei com mercado no mês passado?",
        "Maior gasto da semana",
        "Quanto recebi este ano?",
        "Saldo do mês passado",
        "Quantas vezes usei uber nos últimos 30 dias?",
    )

    private val EXPENSE_W = setOf("gastei", "gasto", "gastos", "gastou", "gastamos", "despesa", "despesas", "paguei", "pagamos", "saiu", "sairam", "custou", "custaram", "gastar")
    private val INCOME_W = setOf("recebi", "recebemos", "receita", "receitas", "ganhei", "ganho", "ganhos", "entrou", "entraram", "entrada", "entradas", "renda")
    private val BALANCE_W = setOf("saldo", "sobrou", "sobra", "economizei", "guardei", "balanco", "lucro")
    private val MAX_W = setOf("maior", "maiores", "caro", "cara")
    private val COUNT_W = setOf("quantas", "quantos", "vezes", "frequencia")
    private val AVG_W = setOf("media", "medio")
    private val FILLER = setOf(
        "quanto", "quanta", "qual", "quais", "foi", "foram", "eu", "nos", "meu", "minha", "meus", "minhas", "total", "valor", "lancamento",
        "lancamentos", "usei", "fiz", "tive", "tem", "teve", "ja", "ate", "agora", "mes", "ano", "semana", "dia", "dias", "ultimos", "ultimas",
        "ultimo", "ultima", "passado", "passada", "este", "esta", "esse", "essa", "neste", "nesta", "nesse", "nessa", "deste", "desta",
        "desse", "dessa", "hoje", "ontem", "por", "no", "na", "em", "de", "do", "da", "com", "o", "a", "os", "as", "que", "mais", "gasto",
        "atual", "corrente", "inteiro", "todo", "toda", "periodo", "compra", "compras", "vez",
    )
    private val MONTHS = mapOf(
        "janeiro" to 1, "jan" to 1, "fevereiro" to 2, "fev" to 2, "marco" to 3, "abril" to 4, "abr" to 4, "maio" to 5,
        "junho" to 6, "jun" to 6, "julho" to 7, "jul" to 7, "agosto" to 8, "ago" to 8, "setembro" to 9, "set" to 9,
        "outubro" to 10, "out" to 10, "novembro" to 11, "nov" to 11, "dezembro" to 12, "dez" to 12,
    )

    fun parse(question: String, s: AppState, today: LocalDate): Parsed {
        val f = Text.fold(question)
        val words = f.split(' ').filter { it.isNotEmpty() }
        val set = words.toSet()
        val used = HashSet<String>()

        // categoria: nome de categoria do usuário que aparece na pergunta (o mais longo vence)
        var category: String? = null
        var catKind: Kind? = null
        val padded = " $f "
        for (k in Kind.entries) for (c in s.cats.of(k)) {
            val fc = Text.fold(c)
            if (fc.isNotEmpty() && padded.contains(" $fc ") && (category == null || fc.length > Text.fold(category).length)) { category = c; catKind = k }
        }
        category?.let { used += Text.fold(it).split(' ') }

        val kind = when {
            set.any { it in INCOME_W } -> Kind.INCOME
            set.any { it in EXPENSE_W } -> Kind.EXPENSE
            else -> catKind
        }
        val intent = when {
            set.any { it in BALANCE_W } -> Intent.BALANCE
            set.any { it in AVG_W } -> Intent.AVERAGE
            set.any { it in MAX_W } -> Intent.MAX
            set.any { it in COUNT_W } -> Intent.COUNT
            else -> Intent.TOTAL
        }
        used += EXPENSE_W + INCOME_W + BALANCE_W + MAX_W + COUNT_W + AVG_W

        val period = period(words, today, used)
        // palavras que sobraram viram filtro de descrição ("uber", "netflix"...), mas só as que existem
        // em algum lançamento: "pedi", "comprei" etc. não filtram nada e aparecem como ignoradas.
        val rest = words.filter { it !in used && it !in FILLER && it !in Text.STOP && it.length >= 2 && !it.all(Char::isDigit) }.distinct()
        val vocab = HashSet<String>()
        for (t in s.txs) vocab += Text.fold(t.desc + " " + t.category).split(' ')
        val (known, unknown) = rest.partition { w -> vocab.any { it.startsWith(w) } }
        return Parsed(intent, if (intent == Intent.BALANCE) null else kind ?: Kind.EXPENSE, period, category, known, unknown)
    }

    private fun period(w: List<String>, today: LocalDate, used: MutableSet<String>): Period {
        val f = " " + w.joinToString(" ") + " "
        fun has(vararg p: String) = p.any { f.contains(" $it ") }
        val ym = YearMonth.from(today)
        // "últimos N dias"
        Regex(" ultimos (\\d{1,3}) dias ").find(f)?.let { m ->
            val n = m.groupValues[1].toInt().coerceIn(1, 366)
            used += m.groupValues[1]
            return Period(today.minusDays(n - 1L), today, "últimos $n dias")
        }
        when {
            has("hoje") -> return Period(today, today, "hoje")
            has("ontem") -> today.minusDays(1).let { return Period(it, it, "ontem (${Br.date(it)})") }
            has("semana passada", "ultima semana") -> {
                val mon = today.with(DayOfWeek.MONDAY).minusWeeks(1)
                return Period(mon, mon.plusDays(6), "semana passada (${Br.dayMonth(mon)} a ${Br.dayMonth(mon.plusDays(6))})")
            }
            has("semana") -> { val mon = today.with(DayOfWeek.MONDAY); return Period(mon, today, "esta semana (desde ${Br.dayMonth(mon)})") }
            has("mes passado", "ultimo mes") -> ym.minusMonths(1).let { return Period(it.atDay(1), it.atEndOfMonth(), Br.monthYear(it)) }
            has("ano passado") -> (today.year - 1).let { return Period(LocalDate.of(it, 1, 1), LocalDate.of(it, 12, 31), "ano de $it") }
            has("este ano", "esse ano", "neste ano", "nesse ano", "ano atual", "deste ano", "desse ano") ->
                return Period(LocalDate.of(today.year, 1, 1), today, "este ano (${today.year})")
        }
        // nome de mês, com ou sem ano ("agosto", "ago de 2025", "em março 2024")
        for ((i, word) in w.withIndex()) {
            val m = MONTHS[word] ?: continue
            used += word
            val yearWord = w.drop(i + 1).take(2).firstOrNull { it.length == 4 && it.all(Char::isDigit) }
            val year = yearWord?.toInt()?.also { used += yearWord } ?: if (m > today.monthValue) today.year - 1 else today.year
            val target = YearMonth.of(year, m)
            return Period(target.atDay(1), target.atEndOfMonth(), Br.monthYear(target))
        }
        // ano solto ("em 2025")
        w.firstOrNull { it.length == 4 && it.all(Char::isDigit) && it.toInt() in 1990..2100 }?.let { y ->
            used += y
            return Period(LocalDate.of(y.toInt(), 1, 1), minOf(LocalDate.of(y.toInt(), 12, 31), maxOf(today, LocalDate.of(y.toInt(), 1, 1))), "ano de $y")
        }
        return Period(ym.atDay(1), ym.atEndOfMonth(), "${Br.monthYear(ym)} (este mês)")
    }

    fun answer(question: String, s: AppState, today: LocalDate, money: MoneyFmt): Answer {
        val p = parse(question, s, today)
        fun base(k: Kind?) = s.txs.filter { t ->
            t.isFlow && (k == null || t.kind == k) && !t.date.isBefore(p.period.from) && !t.date.isAfter(p.period.to) &&
                (p.category == null || t.category == p.category) &&
                (p.words.isEmpty() || Text.fold(t.desc + " " + t.category).let { d -> p.words.all { w -> " $d ".contains(" $w") } })
        }
        val all = base(p.kind)
        val done = all.filter { it.paid }
        val pending = all.filter { !it.paid }
        val what = when (p.kind) { Kind.INCOME -> "receitas"; Kind.EXPENSE -> "despesas"; null -> "receitas e despesas" }
        val filter = listOfNotNull(p.category?.let { "categoria $it" }, p.words.takeIf { it.isNotEmpty() }?.let { "descrição com “${it.joinToString(" ")}”" })
        val understood = "Como entendi: ${p.intent.label} de $what · ${p.period.label}" +
            (if (filter.isNotEmpty()) " · " + filter.joinToString(" · ") else "") + " · só valores realizados." +
            (if (p.ignored.isNotEmpty()) " Ignorei ${p.ignored.joinToString(", ") { "“$it”" }}: não aparece em nenhum lançamento." else "")
        val scope = (if (filter.isNotEmpty()) " (${filter.joinToString(", ")})" else "") + " em ${p.period.label}"
        val sum = done.fold(0L) { a, t -> a + t.value }
        val verb = if (p.kind == Kind.INCOME) "recebeu" else "gastou"
        val pendNote = if (pending.isNotEmpty() && p.intent != Intent.BALANCE)
            " Há ainda ${money(pending.fold(0L) { a, t -> a + t.value })} pendente(s) em ${Br.plural(pending.size, "lançamento", "lançamentos")}." else ""

        val text = when (p.intent) {
            Intent.TOTAL ->
                if (done.isEmpty()) "Não encontrei $what realizadas$scope.$pendNote"
                else "Você $verb ${money(sum)}$scope, em ${Br.plural(done.size, "lançamento", "lançamentos")}.$pendNote"
            Intent.COUNT ->
                if (done.isEmpty()) "Nenhum lançamento de $what$scope.$pendNote"
                else "${Br.plural(done.size, "lançamento", "lançamentos")} de $what$scope, somando ${money(sum)}.$pendNote"
            Intent.MAX -> {
                val top = done.sortedByDescending { it.value }.take(3)
                if (top.isEmpty()) "Não encontrei $what realizadas$scope."
                else "O maior foi “${top[0].desc}”: ${money(top[0].value)} em ${Br.date(top[0].date)} (${top[0].category})." +
                    (if (top.size > 1) " Depois: " + top.drop(1).joinToString("; ") { "“${it.desc}” ${money(it.value)}" } + "." else "")
            }
            Intent.AVERAGE -> {
                val end = minOf(p.period.to, today)
                val days = (end.toEpochDay() - p.period.from.toEpochDay() + 1).coerceAtLeast(1)
                if (done.isEmpty()) "Não encontrei $what realizadas$scope."
                else "Média de ${money(sum / days)} por dia$scope (${money(sum)} em $days dia(s) até ${Br.date(end)})."
            }
            Intent.BALANCE -> {
                val inc = done.filter { it.kind == Kind.INCOME }.fold(0L) { a, t -> a + t.value }
                val exp = done.filter { it.kind == Kind.EXPENSE }.fold(0L) { a, t -> a + t.value }
                "Em ${p.period.label}: receitas ${money(inc)}, despesas ${money(exp)}, saldo ${money(inc - exp)}."
            }
        }
        return Answer(text, understood, p, done)
    }
}
