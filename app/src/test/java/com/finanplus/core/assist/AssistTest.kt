// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.assist

import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Tx
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private var seq = 0
private fun d(s: String) = LocalDate.parse(s)
private fun ex(desc: String, cat: String, cents: Long, date: String, paid: Boolean = true, rec: String = "", group: String = "") =
    Tx("t${seq++}", Kind.EXPENSE, cents, d(date), desc, cat, paid, "main", recurringId = rec, groupId = group)
private fun inc(desc: String, cat: String, cents: Long, date: String, paid: Boolean = true) =
    Tx("t${seq++}", Kind.INCOME, cents, d(date), desc, cat, paid, "main")
private fun st(vararg t: Tx, limits: Map<String, Long> = emptyMap()) = AppState(txs = t.toList(), limits = limits)
private val money: MoneyFmt = { Money.format(it) }

/** O dicionário real do app (o mesmo arquivo que vai no APK). */
private val dict: Dictionary by lazy {
    val f = listOf("src/main/assets/assistente/dicionario.txt", "app/src/main/assets/assistente/dicionario.txt").map(::File).first { it.exists() }
    Dictionary.parse(f.readText())
}

class TextTest {
    @Test fun normalizaExtratoBancario() {
        assertEquals("pag uber trip 2 3", Text.fold("PAG*Uber  Trip (2/3)"))
        assertEquals(listOf("uber", "trip"), Text.tokens("PAG*Uber  Trip (2/3)"))
        assertEquals(listOf("pao", "acucar"), Text.tokens("Pão de Açúcar"))
        assertTrue(Text.same("Saúde", "SAUDE"))
    }
}

class DictionaryTest {
    @Test fun arquivoRealCarregaESugereCategoriasPadrao() {
        assertTrue(dict.sections.size >= 20)
        val cats = AppState.DEFAULT_EXPENSE
        assertEquals("Alimentação", dict.match("iFood pizza", Kind.EXPENSE, cats)?.category)
        assertEquals("Alimentação", dict.match("Supermercados Pão de Açúcar", Kind.EXPENSE, cats)?.category)
        assertEquals("Transporte", dict.match("PAG*UBER TRIP", Kind.EXPENSE, cats)?.category)
        assertEquals("Moradia", dict.match("Conta Sabesp", Kind.EXPENSE, cats)?.category)
        assertEquals("Saúde", dict.match("Drogasil", Kind.EXPENSE, cats)?.category)
        assertEquals("Lazer", dict.match("Netflix.com", Kind.EXPENSE, cats)?.category)
        assertEquals("Salário", dict.match("Salário setembro", Kind.INCOME, AppState.DEFAULT_INCOME)?.category)
        assertEquals("Outros", dict.match("Presente da Ana", Kind.EXPENSE, cats)?.category) // "Presentes" não existe: cai na alternativa "Outros"
    }

    @Test fun usaCategoriaMaisEspecificaQuandoOUsuarioTem() {
        val cats = AppState.DEFAULT_EXPENSE + listOf("Mercado", "Assinaturas")
        assertEquals("Mercado", dict.match("Carrefour", Kind.EXPENSE, cats)?.category)
        assertEquals("Assinaturas", dict.match("Spotify", Kind.EXPENSE, cats)?.category)
    }

    @Test fun termoMaisLongoVence() {
        assertEquals("Alimentação", dict.match("Uber Eats", Kind.EXPENSE, AppState.DEFAULT_EXPENSE)?.category)
        assertEquals("Outros", dict.match("Mercado Livre", Kind.EXPENSE, AppState.DEFAULT_EXPENSE)?.category)
    }

    @Test fun empateNaoSugere() {
        val dd = Dictionary.parse("[despesa: A]\nfoo\n[despesa: B]\nbar")
        assertNull(dd.match("foo bar", Kind.EXPENSE, listOf("A", "B")))
        assertEquals("A", dd.match("foo", Kind.EXPENSE, listOf("A", "B"))?.category)
        assertNull(dd.match("foo", Kind.EXPENSE, listOf("B"))) // categoria inexistente nunca é sugerida
    }
}

class CategorizerTest {
    private fun history() = st(
        ex("Feira do Seu Zé", "Alimentação", 4500, "2026-07-02"),
        ex("Feira do Seu Zé", "Alimentação", 3900, "2026-07-09"),
        ex("Uber casa", "Transporte", 2300, "2026-07-03"),
        ex("Uber trabalho", "Transporte", 1800, "2026-07-04"),
        ex("Uber aeroporto", "Transporte", 6100, "2026-07-10"),
        ex("Academia Fit", "Saúde", 9900, "2026-07-05"),
        ex("Cinema shopping", "Lazer", 5000, "2026-07-06"),
        ex("Livro faculdade", "Educação", 7000, "2026-07-07"),
    )

    @Test fun mesmaDescricaoTemPrioridade() {
        val c = Categorizer.build(history(), Kind.EXPENSE, dict)
        val s = c.suggest("feira do seu zé")!!
        assertEquals("Alimentação", s.category); assertEquals(Source.SAME_DESCRIPTION, s.source)
        assertTrue(s.why.contains("2 vezes"))
    }

    @Test fun aprendeComOsLancamentosDoUsuario() {
        val c = Categorizer.build(history(), Kind.EXPENSE, null)
        assertNull(c.suggest("Uber shopping")) // "shopping" puxa para Lazer: ambíguo, não sugere
        val s = c.suggest("Uber noite")!!
        assertEquals("Transporte", s.category); assertEquals(Source.LEARNED, s.source)
        assertTrue(s.why.contains("“uber”") && s.why.contains("3 lançamento"), s.why)
        // aprendizado pessoal vence o dicionário: aqui "Seu Zé" não está em nenhum dicionário
        assertEquals("Alimentação", Categorizer.build(history(), Kind.EXPENSE, dict).suggest("Seu Zé banca")?.category)
    }

    @Test fun semDadosUsaDicionarioESemConfiancaNaoSugere() {
        val c = Categorizer.build(st(), Kind.EXPENSE, dict)
        assertEquals(Source.DICTIONARY, c.suggest("Drogaria São Paulo")?.source)
        assertNull(c.suggest("xyz abc"))
        assertNull(c.suggest(""))
    }

    @Test fun descricaoUsadaEmCategoriasDiferentesNaoDecide() {
        val s = st(ex("Loja", "Lazer", 100, "2026-07-01"), ex("Loja", "Outros", 100, "2026-07-02"))
        assertNull(Categorizer.build(s, Kind.EXPENSE, null).suggest("Loja"))
    }

    @Test fun ignoraSufixoDeParcelaECategoriaExcluida() {
        assertEquals("Compra TV", Categorizer.clean("Compra TV (3/10)"))
        val s = st(ex("Coisa x", "Antiga", 100, "2026-07-01"))
        assertNull(Categorizer.build(s, Kind.EXPENSE, null).suggest("Coisa x")) // "Antiga" não está nas categorias
    }

    @Test fun mostraOQueAprendeu() {
        val w = Categorizer.build(history(), Kind.EXPENSE, null).learnedWords()
        assertEquals(listOf("uber" to 3), w.first { it.first == "Transporte" }.second)
    }
}

class InsightsTest {
    private val today = d("2026-10-15")

    @Test fun resumoComparaMesmoPeriodo() {
        val s = st(
            ex("Mercado", "Alimentação", 300_00, "2026-10-05"), ex("Uber", "Transporte", 100_00, "2026-10-10"),
            ex("Mercado", "Alimentação", 200_00, "2026-09-05"), ex("Aluguel", "Moradia", 1000_00, "2026-09-20"), // dia 20 fica fora da comparação
            inc("Salário", "Salário", 3000_00, "2026-10-05"), ex("Luz", "Moradia", 150_00, "2026-10-25", paid = false),
        )
        val r = Insights.report(s, today, money)
        assertTrue(r.lines[0].contains("R$ 400,00") && r.lines[0].contains("100% a mais") && r.lines[0].contains("R$ 200,00"), r.lines[0])
        assertTrue(r.lines.any { it.contains("sobram R$ 2.600,00") })
        assertTrue(r.lines.any { it.contains("maior categoria é Alimentação") && it.contains("75%") })
        assertTrue(r.lines.any { it.contains("faltam R$ 150,00 em 1 conta") })
        val r2 = Insights.report(st(inc("Salário", "Salário", 900_00, "2026-10-30", paid = false), inc("Adiantamento", "Extra", 1213_62, "2026-10-15", paid = false)), today, money)
        assertTrue(r2.lines.any { it == "A receber neste mês: R$ 2.113,62 em 2 lançamentos." }, r2.lines.toString())
    }

    @Test fun destaquesDoInicioPorPrioridade() {
        // sem despesas realizadas: "Ainda não há despesas" fica de fora; contas a pagar vêm antes do que falta receber
        val s = st(
            ex("Aluguel", "Moradia", 700_00, "2026-10-20", paid = false), ex("Luz", "Moradia", 312_50, "2026-10-20", paid = false),
            inc("Salário", "Salário", 1213_62, "2026-10-20", paid = false),
        )
        val r = Insights.report(s, today, money)
        assertEquals(listOf("Ainda faltam R$ 1.012,50 em 2 contas a pagar até o fim do mês.", "A receber neste mês: R$ 1.213,62 em 1 lançamento."), r.highlights)
        // conta em atraso vem primeiro
        val late = Insights.report(st(ex("Internet", "Moradia", 119_90, "2026-10-06", paid = false), ex("Mercado", "Alimentação", 300_00, "2026-10-05")), today, money)
        assertEquals(2, late.highlights.size)
        assertTrue(late.highlights[0].contains("em atraso"), late.highlights.toString())
        assertTrue(late.highlights[1].contains("você gastou R$ 300,00"), late.highlights.toString())
    }

    @Test fun duplicado() {
        val s = st(ex("Padaria", "Alimentação", 12_50, "2026-10-10"), ex("padaria", "Alimentação", 12_50, "2026-10-10"), ex("Padaria", "Alimentação", 12_50, "2026-10-11"))
        val l = Insights.duplicates(s, today, money)
        assertEquals(1, l.size); assertTrue(l[0].text.contains("2 vezes em 10/10"))
    }

    @Test fun parcelasNaoSaoDuplicadas() {
        val s = st(ex("TV (1/2)", "Outros", 500_00, "2026-10-10", group = "g"), ex("TV (2/2)", "Outros", 500_00, "2026-10-10", group = "g"))
        assertTrue(Insights.duplicates(s, today, money).isEmpty())
    }

    @Test fun assinaturasEAumentoDePreco() {
        val s = st(
            ex("Netflix", "Lazer", 44_90, "2026-07-08"), ex("Netflix", "Lazer", 44_90, "2026-08-08"),
            ex("Netflix", "Lazer", 44_90, "2026-09-08"), ex("Netflix", "Lazer", 55_90, "2026-10-08"),
            ex("Spotify", "Lazer", 21_90, "2026-08-02"), ex("Spotify", "Lazer", 21_90, "2026-09-02"), ex("Spotify", "Lazer", 21_90, "2026-10-02"),
            ex("Uber", "Transporte", 20_00, "2026-09-01"), ex("Uber", "Transporte", 25_00, "2026-09-03"), ex("Uber", "Transporte", 22_00, "2026-10-01"),
            ex("Cinema", "Lazer", 30_00, "2026-08-10"), ex("Cinema", "Lazer", 30_00, "2026-10-10"), // não seguidos
        )
        val subs = Insights.recurringExpenses(s, today)
        assertEquals(setOf("Netflix", "Spotify"), subs.map { it.name }.toSet())
        val sum = Insights.subscriptionsSummary(subs, today, money)!!
        assertTrue(sum.text.contains("R$ 77,80 por mês") && sum.text.contains("R$ 933,60 por ano"), sum.text)
        val up = Insights.priceUps(subs, money)
        assertEquals(1, up.size); assertTrue(up[0].text.contains("R$ 44,90 para R$ 55,90") && up[0].text.contains("+24%"), up[0].text)
    }

    @Test fun categoriaAcimaDaMedia() {
        val s = st(
            ex("Bar", "Lazer", 100_00, "2026-07-10"), ex("Bar", "Lazer", 100_00, "2026-08-10"), ex("Bar", "Lazer", 100_00, "2026-09-10"),
            ex("Show", "Lazer", 250_00, "2026-10-03"),
            ex("Mercado", "Alimentação", 500_00, "2026-09-10"), ex("Mercado", "Alimentação", 520_00, "2026-10-10"),
        )
        val l = Insights.spikes(s, today, money)
        assertEquals(listOf("Lazer"), l.map { it.query })
        assertTrue(l[0].text.contains("R$ 250,00") && l[0].text.contains("150% acima") && l[0].text.contains("R$ 100,00"), l[0].text)
    }

    @Test fun ritmoDoLimiteConsideraCompromissos() {
        // dia 15 de 31: aluguel fixo (recorrência) não é extrapolado, só o variável
        val s = st(
            ex("Restaurante", "Alimentação", 300_00, "2026-10-08"), ex("Mercado", "Alimentação", 300_00, "2026-10-12"),
            ex("Assinatura comida", "Alimentação", 100_00, "2026-10-01", rec = "r1"),
            limits = mapOf("Alimentação" to 1000_00),
        )
        val l = Insights.limitPace(s, today, money)
        assertEquals(1, l.size)
        // projeção = 100 + 600/15*31 = 1340
        assertTrue(l[0].text.contains("R$ 1.340,00") && l[0].text.contains("R$ 18,75 por dia") && l[0].text.contains("16 dias"), l[0].text)
        assertTrue(Insights.limitPace(s, d("2026-10-05"), money).isEmpty()) // antes do dia 7 não projeta
    }

    @Test fun despesasAcimaDasReceitasPrevistas() {
        val s = st(inc("Salário", "Salário", 1000_00, "2026-10-05"), ex("Gastos", "Outros", 800_00, "2026-10-10"))
        val i = Insights.overIncome(s, today, money)
        assertNotNull(i); assertTrue(i.text.contains("R$ 1.653,33"), i.text)
    }

    @Test fun pequenosGastos() {
        val many = (1..10).map { ex(if (it % 2 == 0) "Café" else "Bala", "Alimentação", 8_00, "2026-10-%02d".format(it)) }.toTypedArray()
        val i = Insights.smallSpends(st(*many, ex("Mercado", "Alimentação", 220_00, "2026-10-02")), today, money)!!
        assertTrue(i.text.contains("10 compras de até R$ 20,00 somaram R$ 80,00") && i.text.contains("27%"), i.text)
        assertNull(Insights.smallSpends(st(*many.take(9).toTypedArray()), today, money))
    }

    @Test fun valoresOcultos() {
        val hidden: MoneyFmt = { "R$ ••••" }
        val r = Insights.report(st(ex("Mercado", "Alimentação", 300_00, "2026-10-05")), today, hidden)
        assertTrue(r.lines.none { it.contains("300") })
    }
}

class AskTest {
    private val today = d("2026-10-15")
    private val s = st(
        ex("Mercado Extra", "Alimentação", 250_00, "2026-08-05"), ex("Uber centro", "Transporte", 30_00, "2026-08-07"),
        ex("Uber casa", "Transporte", 25_00, "2026-10-14"), ex("Netflix", "Lazer", 55_90, "2026-10-08"),
        ex("Aluguel", "Moradia", 1500_00, "2026-10-10"), ex("Luz", "Moradia", 180_00, "2026-10-28", paid = false),
        inc("Salário", "Salário", 4000_00, "2026-10-05"), inc("Freela", "Extra", 500_00, "2025-12-10"),
    )

    @Test fun totalComCategoriaEMes() {
        val a = Ask.answer("Quanto gastei com alimentação em agosto?", s, today, money)
        assertEquals("Alimentação", a.parsed.category); assertEquals(d("2026-08-01"), a.parsed.period.from)
        assertTrue(a.text.contains("R$ 250,00"), a.text)
        assertTrue(a.understood.startsWith("Como entendi: total de despesas · agosto de 2026 · categoria Alimentação"), a.understood)
    }

    @Test fun filtroPorPalavraEPeriodoRelativo() {
        val a = Ask.answer("quantas vezes usei uber nos ultimos 90 dias", s, today, money)
        assertEquals(Ask.Intent.COUNT, a.parsed.intent); assertEquals(listOf("uber"), a.parsed.words)
        assertTrue(a.text.startsWith("2 lançamentos") && a.text.contains("R$ 55,00"), a.text)
    }

    @Test fun maiorGastoESaldoEPendentes() {
        assertTrue(Ask.answer("maior gasto do mês", s, today, money).text.contains("“Aluguel”: R$ 1.500,00"))
        val total = Ask.answer("quanto gastei este mês", s, today, money).text
        assertTrue(total.contains("R$ 1.580,90") && total.contains("R$ 180,00 pendente"), total)
        val bal = Ask.answer("saldo do mês", s, today, money).text
        assertTrue(bal.contains("receitas R$ 4.000,00") && bal.contains("saldo R$ 2.419,10"), bal)
    }

    @Test fun receitasEMesSemAnoUsaUltimaOcorrencia() {
        val a = Ask.answer("quanto recebi em dezembro", s, today, money) // dezembro ainda não chegou → 2025
        assertEquals(2025, a.parsed.period.from.year); assertTrue(a.text.contains("R$ 500,00"), a.text)
        assertEquals(Kind.INCOME, a.parsed.kind)
    }

    @Test fun palavrasDesconhecidasSaoIgnoradasEAvisadas() {
        val a = Ask.answer("quantas vezes pedi uber nos ultimos 90 dias", s, today, money)
        assertEquals(listOf("uber"), a.parsed.words); assertEquals(listOf("pedi"), a.parsed.ignored)
        assertTrue(a.understood.contains("Ignorei “pedi”"), a.understood)
    }

    @Test fun mediaPorDia() {
        val a = Ask.answer("média de gastos este mês", s, today, money)
        assertTrue(a.text.contains("R$ 105,39 por dia") && a.text.contains("15 dia(s)"), a.text)
    }
}
