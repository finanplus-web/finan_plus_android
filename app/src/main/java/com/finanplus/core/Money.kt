// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import kotlin.math.abs
import kotlin.math.roundToLong

object Money {
    /** "R$ 1.234,56" / "-R$ 0,50". Formatação própria: não depende do Locale do aparelho. */
    fun format(c: Cents): String {
        val neg = c < 0
        val a = abs(c)
        val reais = (a / 100).toString()
        val sb = StringBuilder()
        reais.forEachIndexed { i, ch -> if (i > 0 && (reais.length - i) % 3 == 0) sb.append('.'); sb.append(ch) }
        return (if (neg) "-" else "") + "R$ " + sb + "," + (a % 100).toString().padStart(2, '0')
    }

    /** Valor para campo de edição: "1500,50". */
    fun input(c: Cents): String {
        val a = abs(c)
        return (if (c < 0) "-" else "") + (a / 100) + "," + (a % 100).toString().padStart(2, '0')
    }

    /** Reais (Double vindo do JSON do PWA) → centavos. */
    fun fromReais(d: Double): Cents = (d * 100).roundToLong()

    /** Centavos → texto numérico em reais para o JSON ("12.5" → "12.50"). */
    fun toReaisJson(c: Cents): Json.Raw {
        val a = abs(c)
        return Json.Raw((if (c < 0) "-" else "") + (a / 100) + "." + (a % 100).toString().padStart(2, '0'))
    }

    /**
     * Aceita "1.500,50", "1500,50", "1500.50", "1,500.25", "R$ 2.000", "-50".
     * Retorna centavos, ou null se inválido.
     */
    fun parse(input: String?): Cents? {
        var s = (input ?: "").replace("R$", "").filterNot { it.isWhitespace() }
        if (s.isEmpty()) return null
        val neg = s.startsWith('-')
        if (neg || s.startsWith('+')) s = s.substring(1)
        if (s.isEmpty() || !s.all { it.isDigit() || it == '.' || it == ',' }) return null
        val lc = s.lastIndexOf(','); val ld = s.lastIndexOf('.')
        if (lc >= 0 && ld >= 0) {
            val dec = if (lc > ld) ',' else '.'
            val thou = if (dec == ',') '.' else ','
            s = s.replace(thou.toString(), "")
            if (s.count { it == dec } > 1) return null
            s = s.replace(dec, '.')
        } else if (lc >= 0) {
            if (s.count { it == ',' } > 1) return null
            s = s.replace(',', '.')
        } else if (ld >= 0 && Regex("^\\d{1,3}(\\.\\d{3})+$").matches(s)) {
            s = s.replace(".", "")
        }
        if (s.count { it == '.' } > 1) return null
        val parts = s.split('.')
        val intPart = parts[0].ifEmpty { "0" }
        if (intPart.length > 13) return null
        val frac = if (parts.size > 1) parts[1] else ""
        if (parts.size > 1 && frac.isEmpty() && parts[0].isEmpty()) return null
        val whole = intPart.toLongOrNull() ?: return null
        // arredonda a partir da 3ª casa, como o PWA (Math.round)
        val f3 = (frac + "000").substring(0, 3).toInt()
        val cents = whole * 100 + (f3 + 5) / 10
        return if (neg) -cents else cents
    }
}
