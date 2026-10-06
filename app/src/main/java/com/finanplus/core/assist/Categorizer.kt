// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.assist

import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Tx
import kotlin.math.exp
import kotlin.math.ln

/** De onde veio a sugestão — mostrado ao usuário em "Por quê?". */
enum class Source { SAME_DESCRIPTION, LEARNED, DICTIONARY }

data class Suggestion(val category: String, val source: Source, val why: String, val confidence: Double)

/**
 * Sugere a categoria de um lançamento a partir da descrição, em três etapas (a primeira que responder vence):
 *
 * 1. **Mesma descrição** — o usuário já lançou algo com a mesma descrição: usa a categoria que ele escolheu.
 * 2. **Aprendizado** — classificador Naive Bayes treinado só com os lançamentos do próprio usuário
 *    (palavras da descrição → categoria). Só sugere com confiança ≥ [MIN_CONFIDENCE].
 * 3. **Dicionário** — termos conhecidos (ifood, sabesp, farmácia…) do arquivo aberto `dicionario.txt`.
 *
 * Nada é guardado à parte: o "aprendizado" é recalculado a partir dos lançamentos que já existem
 * (que ficam criptografados no aparelho). Apagar ou corrigir um lançamento corrige o aprendizado.
 */
class Categorizer private constructor(
    private val kind: Kind,
    private val categories: List<String>,
    private val dict: Dictionary?,
    /** chave da descrição → categoria → (quantidade, última data em epochDay) */
    private val exact: Map<String, Map<String, Pair<Int, Long>>>,
    /** categoria → nº de lançamentos */
    private val catDocs: Map<String, Int>,
    /** palavra → categoria → nº de lançamentos que contêm a palavra */
    private val wordDocs: Map<String, Map<String, Int>>,
    /** categoria → total de palavras */
    private val catWords: Map<String, Int>,
    private val docs: Int,
) {
    companion object {
        const val MIN_CONFIDENCE = 0.70
        /** Mínimo de lançamentos do usuário para o aprendizado começar a sugerir. */
        const val MIN_DOCS = 5
        /** A palavra decisiva precisa ter aparecido em pelo menos 2 lançamentos da categoria. */
        const val MIN_WORD_DOCS = 2
        /** Suavização do Naive Bayes. */
        const val ALPHA = 0.1
        private val PARCEL = Regex("\\s*\\(\\d+/\\d+\\)\\s*$")

        /** Descrição sem o sufixo de parcela "(2/10)" que o próprio app acrescenta. */
        fun clean(desc: String) = desc.replace(PARCEL, "")

        fun build(s: AppState, kind: Kind, dict: Dictionary?): Categorizer {
            val cats = s.cats.of(kind)
            val exact = HashMap<String, HashMap<String, Pair<Int, Long>>>()
            val catDocs = HashMap<String, Int>()
            val wordDocs = HashMap<String, HashMap<String, Int>>()
            val catWords = HashMap<String, Int>()
            var docs = 0
            for (t in training(s, kind)) {
                val toks = Text.tokens(clean(t.desc))
                if (toks.isEmpty()) continue
                docs++
                val k = toks.distinct().joinToString(" ")
                val m = exact.getOrPut(k) { HashMap() }
                val prev = m[t.category]
                m[t.category] = Pair((prev?.first ?: 0) + 1, maxOf(prev?.second ?: Long.MIN_VALUE, t.date.toEpochDay()))
                catDocs[t.category] = (catDocs[t.category] ?: 0) + 1
                catWords[t.category] = (catWords[t.category] ?: 0) + toks.size
                for (w in toks.distinct()) { val wm = wordDocs.getOrPut(w) { HashMap() }; wm[t.category] = (wm[t.category] ?: 0) + 1 }
            }
            return Categorizer(kind, cats, dict, exact, catDocs, wordDocs, catWords, docs)
        }

        /** Lançamentos usados para aprender: do mesmo tipo, com descrição, em categoria que ainda existe. */
        fun training(s: AppState, kind: Kind): List<Tx> {
            val cats = s.cats.of(kind).toSet()
            return s.txs.filter { it.kind == kind && it.isFlow && it.category in cats && it.desc != "Sem descrição" }
        }
    }

    fun suggest(desc: String): Suggestion? {
        val toks = Text.tokens(clean(desc))
        if (toks.isEmpty()) return null
        sameDescription(toks)?.let { return it }
        learned(toks)?.let { return it }
        return dictionary(desc)
    }

    private fun sameDescription(toks: List<String>): Suggestion? {
        val m = exact[toks.distinct().joinToString(" ")] ?: return null
        val total = m.values.sumOf { it.first }
        val best = m.entries.sortedWith(compareByDescending<Map.Entry<String, Pair<Int, Long>>> { it.value.first }.thenByDescending { it.value.second }).first()
        val share = best.value.first.toDouble() / total
        if (share < 0.6) return null // a mesma descrição já foi usada em categorias diferentes: não decide
        val n = best.value.first
        return Suggestion(
            best.key, Source.SAME_DESCRIPTION,
            "Você já lançou esta descrição ${if (n == 1) "1 vez" else "$n vezes"} como ${best.key}" +
                (if (total > n) " (e ${total - n} vez(es) em outra categoria)." else "."),
            share,
        )
    }

    private fun learned(toks: List<String>): Suggestion? {
        if (docs < MIN_DOCS || catDocs.size < 2) return null
        val known = toks.distinct().filter { it in wordDocs }
        if (known.isEmpty()) return null
        val vocab = wordDocs.size.toDouble()
        val k = catDocs.size.toDouble()
        // log P(c) + Σ log P(palavra | c). Suavização leve (ALPHA) porque descrições são curtas;
        // palavras nunca vistas são ignoradas (não indicam nada).
        val scores = catDocs.keys.associateWith { c ->
            var sc = ln((catDocs[c]!! + 1.0) / (docs + k))
            for (w in known) sc += ln(((wordDocs[w]?.get(c) ?: 0) + ALPHA) / (catWords[c]!! + ALPHA * vocab))
            sc
        }
        val max = scores.values.maxOrNull() ?: return null
        val sum = scores.values.sumOf { exp(it - max) }
        val (cat, sc) = scores.maxByOrNull { it.value }!!
        val p = exp(sc - max) / sum
        if (p < MIN_CONFIDENCE) return null
        // palavra que mais sustenta a decisão
        val word = known.maxByOrNull { (wordDocs[it]?.get(cat) ?: 0) } ?: return null
        val wd = wordDocs[word]?.get(cat) ?: 0
        if (wd < MIN_WORD_DOCS) return null
        val totalW = wordDocs[word]!!.values.sum()
        return Suggestion(
            cat, Source.LEARNED,
            "A palavra “$word” aparece em $wd lançamento(s) seus de $cat" +
                (if (totalW > wd) " (de $totalW com essa palavra)" else "") + ". Confiança: ${Br.pct(p * 100)}.",
            p,
        )
    }

    private fun dictionary(desc: String): Suggestion? {
        val d = dict ?: return null
        val m = d.match(desc, kind, categories) ?: return null
        val terms = m.terms.joinToString(", ") { "“$it”" }
        return Suggestion(
            m.category, Source.DICTIONARY,
            "$terms está no dicionário aberto do assistente, na seção de ${m.category} (linha ${m.section.line} de dicionario.txt).",
            0.6,
        )
    }

    /** "O que o assistente aprendeu": palavras mais frequentes por categoria (≥ 2 lançamentos). */
    fun learnedWords(perCategory: Int = 6): List<Pair<String, List<Pair<String, Int>>>> =
        categories.mapNotNull { c ->
            val words = wordDocs.mapNotNull { (w, m) -> m[c]?.takeIf { it >= MIN_WORD_DOCS }?.let { w to it } }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).take(perCategory)
            if (words.isEmpty()) null else c to words
        }

    val trainingSize: Int get() = docs
}
