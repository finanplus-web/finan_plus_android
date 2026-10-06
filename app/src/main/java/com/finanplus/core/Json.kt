// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

/**
 * JSON mínimo e sem dependências (o núcleo não depende de Android nem de bibliotecas).
 * Objetos viram LinkedHashMap<String, Any?>, listas viram List<Any?>, números viram Double.
 */
object Json {
    /** Número já formatado, escrito sem aspas (ex.: valores em reais "12.50"). */
    class Raw(val text: String)

    class ParseException(msg: String) : Exception(msg)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != text.length) throw ParseException("Conteúdo extra na posição ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }
        fun fail(m: String): Nothing = throw ParseException("$m na posição $i")
        fun value(depth: Int): Any? {
            if (depth > 64) fail("Aninhamento excessivo")
            if (i >= s.length) fail("Fim inesperado")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("Caractere inesperado '$c'")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) fail("Valor inválido"); i += w.length; return v }
        fun obj(depth: Int): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws(); if (i >= s.length || s[i] != '"') fail("Chave esperada")
                val k = str(); ws()
                if (i >= s.length || s[i] != ':') fail("':' esperado"); i++; ws()
                m[k] = value(depth + 1); ws()
                if (i >= s.length) fail("Fim inesperado")
                when (s[i]) { ',' -> i++; '}' -> { i++; return m }; else -> fail("',' ou '}' esperado") }
            }
        }
        fun arr(depth: Int): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                ws(); l.add(value(depth + 1)); ws()
                if (i >= s.length) fail("Fim inesperado")
                when (s[i]) { ',' -> i++; ']' -> { i++; return l }; else -> fail("',' ou ']' esperado") }
            }
        }
        fun str(): String {
            val sb = StringBuilder(); i++
            while (true) {
                if (i >= s.length) fail("Texto sem fim")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) fail("Escape inválido")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("Escape \\u incompleto")
                                val h = s.substring(i, i + 4).toIntOrNull(16) ?: fail("Escape \\u inválido")
                                sb.append(h.toChar()); i += 4
                            }
                            else -> fail("Escape '\\$e' inválido")
                        }
                    }
                    c < ' ' -> fail("Caractere de controle em texto")
                    else -> sb.append(c)
                }
            }
        }
        fun num(): Double {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(st, i).toDoubleOrNull()?.takeIf { it.isFinite() } ?: fail("Número inválido")
        }
    }

    fun stringify(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Raw -> sb.append(v.text)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Double -> sb.append(if (v.isFinite()) (if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()) else "null")
            is Number -> sb.append(v.toString())
            is String -> quote(sb, v)
            is Map<*, *> -> {
                sb.append('{'); var first = true
                for ((k, x) in v) { if (!first) sb.append(','); first = false; quote(sb, k.toString()); sb.append(':'); write(sb, x) }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('['); var first = true
                for (x in v) { if (!first) sb.append(','); first = false; write(sb, x) }
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\""); c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n"); c == '\r' -> sb.append("\\r"); c == '\t' -> sb.append("\\t")
            c < ' ' || c == ' ' || c == ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }
}
