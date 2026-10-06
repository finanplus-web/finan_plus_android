// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.assist

import com.finanplus.core.Kind

/**
 * Dicionário inicial de palavras → categoria (arquivo `assets/assistente/dicionario.txt`).
 *
 * Formato (texto simples, uma seção por grupo de categorias):
 * ```
 * # comentário
 * [despesa: Alimentação | Mercado | Comida]
 * ifood, rappi, supermercado, pao de acucar
 * ```
 * - Os nomes depois de `despesa:`/`receita:` são alternativas: vale o PRIMEIRO que existir
 *   na lista de categorias do usuário (sem diferenciar acento e maiúscula). Se nenhum existir,
 *   a seção é ignorada — o assistente nunca sugere uma categoria que o usuário não tem.
 * - Termos podem ter mais de uma palavra ("pao de acucar") e são comparados sem acento.
 */
class Dictionary(val sections: List<Section>) {
    data class Section(val kind: Kind, val names: List<String>, val terms: List<String>, val line: Int)

    data class Match(val category: String, val terms: List<String>, val section: Section)

    /**
     * Procura termos do dicionário em [desc]. Devolve a categoria com maior peso de termos encontrados;
     * empate entre categorias diferentes = ambíguo = sem sugestão.
     */
    fun match(desc: String, kind: Kind, categories: List<String>): Match? {
        val padded = " " + Text.fold(desc) + " "
        val words = Text.fold(desc).split(' ')
        val found = ArrayList<Match>()
        for (s in sections) {
            if (s.kind != kind) continue
            val cat = s.names.firstNotNullOfOrNull { n -> categories.firstOrNull { Text.same(it, n) } } ?: continue
            val hits = s.terms.filter { t ->
                padded.contains(" $t ") ||
                    // plural/variação simples: "supermercados" casa com "supermercado" (termos de 5+ letras, uma palavra)
                    (t.length >= 5 && ' ' !in t && words.any { w -> w.startsWith(t) && w.length - t.length <= 2 })
            }
            if (hits.isNotEmpty()) found.add(Match(cat, hits, s))
        }
        if (found.isEmpty()) return null
        // várias seções podem apontar para a mesma categoria do usuário: soma os termos.
        // Peso = nº de palavras do termo ("mercado livre" vale 2 e vence "mercado", que vale 1).
        fun weight(m: Match) = m.terms.sumOf { it.count { ch -> ch == ' ' } + 1 }
        val byCat = found.groupBy { it.category }.map { (c, l) -> Match(c, l.flatMap { it.terms }.distinct(), l[0].section) }
            .sortedByDescending { weight(it) }
        if (byCat.size > 1 && weight(byCat[0]) == weight(byCat[1])) return null
        return byCat[0]
    }

    companion object {
        private val HEADER = Regex("^\\[\\s*(despesa|receita)\\s*:\\s*(.+)]$", RegexOption.IGNORE_CASE)

        fun parse(text: String): Dictionary {
            val out = ArrayList<Section>()
            var kind: Kind? = null
            var names: List<String> = emptyList()
            var terms = ArrayList<String>()
            var line0 = 0
            fun flush() { if (kind != null && terms.isNotEmpty()) out.add(Section(kind!!, names, terms.distinct(), line0)) }
            text.lines().forEachIndexed { i, raw ->
                val l = raw.substringBefore('#').trim()
                if (l.isEmpty()) return@forEachIndexed
                val h = HEADER.matchEntire(l)
                if (h != null) {
                    flush()
                    kind = if (h.groupValues[1].lowercase() == "receita") Kind.INCOME else Kind.EXPENSE
                    names = h.groupValues[2].split('|').map { it.trim() }.filter { it.isNotEmpty() }
                    terms = ArrayList(); line0 = i + 1
                } else if (kind != null) {
                    l.split(',').map { Text.fold(it) }.filter { it.length >= 2 }.forEach { terms.add(it) }
                }
            }
            flush()
            return Dictionary(out)
        }
    }
}
