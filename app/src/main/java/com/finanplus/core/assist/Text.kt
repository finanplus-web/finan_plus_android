// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.assist

import com.finanplus.core.Cents
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth

/**
 * Normalização de texto usada por todo o assistente.
 *
 * "PAG*Uber  Trip (2/3)" → fold = "pag uber trip 2 3" → tokens = [uber, trip]
 */
object Text {
    private val MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Palavras sem significado para classificar (artigos, preposições e "ruído" de extrato bancário). */
    val STOP: Set<String> = setOf(
        // português
        "a", "o", "as", "os", "um", "uma", "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas",
        "e", "com", "para", "pra", "por", "pelo", "pela", "ao", "aos", "meu", "minha",
        // extrato / maquininha
        "pag", "pagto", "compra", "compras", "cp", "deb", "debito", "cred", "credito", "cartao", "parcela",
        "ltda", "sa", "me", "eireli", "epp", "br", "www", "sem", "descricao",
    )

    /** Minúsculas, sem acentos, só letras e números separados por um espaço. */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase().replace(NON_ALNUM, " ").trim()

    /** Palavras relevantes: 2+ letras, não só números, fora da lista [STOP]. */
    fun tokens(s: String): List<String> =
        fold(s).split(' ').filter { it.length >= 2 && !it.all(Char::isDigit) && it !in STOP }

    /** Chave para reconhecer a "mesma descrição" (ordem e repetição de palavras importam pouco). */
    fun key(s: String): String = tokens(s).distinct().joinToString(" ")

    /** Comparação de nomes de categoria sem acento/maiúscula ("Saúde" == "saude"). */
    fun same(a: String, b: String): Boolean = fold(a) == fold(b)
}

/** Formatação de datas e números em português, sem depender do Locale do aparelho. */
object Br {
    val MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

    fun month(ym: YearMonth): String = MONTHS[ym.monthValue - 1]
    fun monthYear(ym: YearMonth): String = "${month(ym)} de ${ym.year}"
    fun date(d: LocalDate): String = String.format(java.util.Locale.ROOT, "%02d/%02d/%04d", d.dayOfMonth, d.monthValue, d.year)
    fun dayMonth(d: LocalDate): String = String.format(java.util.Locale.ROOT, "%02d/%02d", d.dayOfMonth, d.monthValue)

    /** Percentual inteiro, sempre positivo: 37.6 → "38%". */
    fun pct(v: Double): String = "${Math.round(Math.abs(v))}%"

    fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
}

/** Formata valores em reais. A interface passa uma versão que mostra "R$ ••••" quando "Ocultar valores" está ligado. */
typealias MoneyFmt = (Cents) -> String
