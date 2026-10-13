// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.bill

import com.finanplus.core.AppState
import com.finanplus.core.Card
import com.finanplus.core.Cents
import com.finanplus.core.Finance
import com.finanplus.core.ym
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * "Ler fatura ou boleto": tira valor e vencimento de uma fatura em PDF (texto), de uma linha digitável
 * de boleto ou de um Pix copia e cola. Tudo aqui é cálculo puro, sem rede e sem Android
 * (o PDF é aberto em `data/BillPdf.kt`). Detalhes e limites em FATURAS.md.
 *
 * O resultado é sempre uma sugestão: a tela mostra de onde cada valor veio e a pessoa confirma.
 */

/** De onde veio a informação, em ordem de confiança: o código é exato, o texto é heurística. */
enum class Source(val label: String) { BOLETO("código do boleto"), PIX("Pix copia e cola"), TEXT("texto da fatura") }

/** Um valor encontrado e o trecho de onde saiu (mostrado na tela para a pessoa conferir). */
data class Found<T>(val value: T, val source: Source, val excerpt: String)

data class BillInfo(
    val amount: Found<Cents>? = null,
    val due: Found<LocalDate>? = null,
    /** recebedor do Pix, ou banco que emitiu o boleto (nem sempre é quem cobra) */
    val payee: String? = null,
    /** tipo de conta de um boleto de arrecadação ("Energia elétrica e gás", "Água e esgoto"…) */
    val segment: String? = null,
    /** Pix dinâmico: o valor fica no banco de quem cobra e não vem no código */
    val dynamicPix: Boolean = false,
) {
    val isEmpty get() = amount == null && due == null
}

// --------------------------------------------------------------------------------------------- boleto
/**
 * Boleto bancário (linha digitável de 47 números ou código de barras de 44) e boleto de arrecadação
 * (contas de consumo, tributos: 48 números ou código de barras de 44 começando com 8), padrão Febraban.
 * Todos os dígitos verificadores são conferidos: um número digitado errado não vira um valor errado.
 */
object Boleto {
    enum class Type { BANK, UTILITY }

    data class Info(
        val type: Type,
        /** código de barras (44 números) */
        val barcode: String,
        val amount: Cents?,
        val due: LocalDate?,
        /** banco emissor (boleto bancário) */
        val bank: String?,
        /** segmento (boleto de arrecadação) */
        val segment: String?,
    )

    /** Aceita a linha com pontos, espaços e quebras. null = não é um boleto válido. */
    fun parse(input: String, today: LocalDate): Info? {
        val d = input.filter { it in '0'..'9' }
        return when (d.length) {
            47 -> bankLine(d, today)
            48 -> if (d[0] == '8') utilityLine(d) else null
            44 -> if (d[0] == '8') utilityBarcode(d) else bankBarcode(d, today)
            else -> null
        }
    }

    internal fun mod10(s: String): Int {
        var sum = 0; var w = 2
        for (i in s.indices.reversed()) {
            var p = (s[i] - '0') * w
            if (p > 9) p = p / 10 + p % 10
            sum += p; w = if (w == 2) 1 else 2
        }
        return (10 - sum % 10) % 10
    }

    private fun weighted11(s: String): Int {
        var sum = 0; var w = 2
        for (i in s.indices.reversed()) { sum += (s[i] - '0') * w; w = if (w == 9) 2 else w + 1 }
        return sum
    }

    /** DV geral do boleto bancário: 0, 10 e 11 viram 1. */
    internal fun mod11Bank(s: String): Int { val dv = 11 - weighted11(s) % 11; return if (dv == 0 || dv == 10 || dv == 11) 1 else dv }

    /** DV do boleto de arrecadação: resto 0 ou 1 vira 0; resto 10 vira 1. */
    internal fun mod11Utility(s: String): Int { val r = weighted11(s) % 11; return if (r == 0 || r == 1) 0 else 11 - r }

    private fun bankLine(d: String, today: LocalDate): Info? {
        if (mod10(d.substring(0, 9)) != d[9] - '0') return null
        if (mod10(d.substring(10, 20)) != d[20] - '0') return null
        if (mod10(d.substring(21, 31)) != d[31] - '0') return null
        val barcode = d.substring(0, 4) + d[32] + d.substring(33, 47) + d.substring(4, 9) + d.substring(10, 20) + d.substring(21, 31)
        return bankBarcode(barcode, today)
    }

    private fun bankBarcode(b: String, today: LocalDate): Info? {
        if (b[3] != '9') return null // só real
        if (mod11Bank(b.substring(0, 4) + b.substring(5)) != b[4] - '0') return null
        val factor = b.substring(5, 9).toInt()
        val value = b.substring(9, 19).toLong()
        return Info(Type.BANK, b, value.takeIf { it > 0 }, dueFromFactor(factor, today), BANKS[b.substring(0, 3)], null)
    }

    /**
     * Fator de vencimento: dias desde 07/10/1997. Ele chegou a 9999 em 21/02/2025 e recomeçou em 1000
     * no dia 22/02/2025 (nova base 29/05/2022). Das duas datas possíveis, vale a mais próxima de hoje.
     */
    fun dueFromFactor(factor: Int, today: LocalDate): LocalDate? {
        if (factor <= 0) return null
        val old = OLD_BASE.plusDays(factor.toLong())
        val new = NEW_BASE.plusDays(factor.toLong())
        return if (abs(ChronoUnit.DAYS.between(today, old)) <= abs(ChronoUnit.DAYS.between(today, new))) old else new
    }

    private fun utilityLine(d: String): Info? {
        val ident = d[2]
        val blocks = (0 until 4).map { d.substring(it * 12, it * 12 + 12) }
        for (bl in blocks) {
            val body = bl.substring(0, 11)
            val dv = when (ident) { '6', '7' -> mod10(body); '8', '9' -> mod11Utility(body); else -> return null }
            if (dv != bl[11] - '0') return null
        }
        return utilityBarcode(blocks.joinToString("") { it.substring(0, 11) })
    }

    private fun utilityBarcode(b: String): Info? {
        val ident = b[2]
        val rest = b.substring(0, 3) + b.substring(4)
        val dv = when (ident) { '6', '7' -> mod10(rest); '8', '9' -> mod11Utility(rest); else -> return null }
        if (dv != b[3] - '0') return null
        // 6 e 8: valor em reais; 7 e 9: valor de referência (quantidade em outra moeda/índice), não é dinheiro
        val value = if (ident == '6' || ident == '8') b.substring(4, 15).toLong().takeIf { it > 0 } else null
        return Info(Type.UTILITY, b, value, null, null, SEGMENTS[b[1]])
    }

    private val OLD_BASE: LocalDate = LocalDate.of(1997, 10, 7)
    private val NEW_BASE: LocalDate = LocalDate.of(2022, 5, 29)

    private val SEGMENTS = mapOf(
        '1' to "Prefeitura", '2' to "Água e esgoto", '3' to "Energia elétrica e gás", '4' to "Telefone e internet",
        '5' to "Órgão do governo", '6' to "Carnê ou cobrança", '7' to "Multa de trânsito",
    )

    /** Bancos emissores mais comuns (código da câmara de compensação). */
    val BANKS = mapOf(
        "001" to "Banco do Brasil", "004" to "Banco do Nordeste", "021" to "Banestes", "033" to "Santander",
        "041" to "Banrisul", "070" to "BRB", "077" to "Inter", "104" to "Caixa", "197" to "Stone", "208" to "BTG Pactual",
        "212" to "Banco Original", "237" to "Bradesco", "260" to "Nubank", "290" to "PagBank", "323" to "Mercado Pago",
        "336" to "C6 Bank", "341" to "Itaú", "380" to "PicPay", "389" to "Mercantil do Brasil", "403" to "Cora",
        "422" to "Safra", "655" to "Banco Votorantim", "748" to "Sicredi", "756" to "Sicoob",
    )
}

// ------------------------------------------------------------------------------------------------ Pix
/** Pix copia e cola (BR Code, padrão EMV): confere o CRC antes de aceitar. */
object Pix {
    data class Info(val amount: Cents?, val name: String?, val city: String?, val dynamic: Boolean)

    fun parse(input: String): Info? {
        val s = input.trim()
        if (!s.startsWith("000201")) return null
        val crcAt = s.length - 8
        if (crcAt < 6 || s.substring(crcAt, crcAt + 4) != "6304") return null
        if (crc16(s.substring(0, crcAt + 4)) != s.substring(crcAt + 4).uppercase()) return null
        val tags = tlv(s) ?: return null
        val account = tags["26"] ?: return null
        val sub = tlv(account) ?: return null
        if (sub["00"]?.lowercase() != "br.gov.bcb.pix") return null
        val amount = tags["54"]?.let { a -> if (Regex("^\\d{1,13}(\\.\\d{1,2})?$").matches(a)) parseDot(a) else null }
        return Info(amount?.takeIf { it > 0 }, tags["59"]?.trim()?.ifEmpty { null }, tags["60"]?.trim()?.ifEmpty { null }, dynamic = sub.containsKey("25"))
    }

    private fun parseDot(a: String): Cents {
        val p = a.split('.')
        return p[0].toLong() * 100 + (if (p.size > 1) p[1].padEnd(2, '0').toLong() else 0L)
    }

    private fun tlv(s: String): Map<String, String>? {
        val out = HashMap<String, String>()
        var i = 0
        while (i < s.length) {
            if (i + 4 > s.length) return null
            val id = s.substring(i, i + 2)
            val len = s.substring(i + 2, i + 4).toIntOrNull() ?: return null
            if (i + 4 + len > s.length) return null
            out[id] = s.substring(i + 4, i + 4 + len)
            i += 4 + len
        }
        return out
    }

    /** CRC16-CCITT (polinômio 0x1021, início 0xFFFF), em 4 dígitos hexadecimais maiúsculos. */
    fun crc16(s: String): String {
        var crc = 0xFFFF
        for (b in s.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}

// --------------------------------------------------------------------------------------------- leitor
object BillReader {
    /** Código colado (linha digitável, código de barras ou Pix copia e cola). null = não reconhecido. */
    fun fromCode(input: String, today: LocalDate): BillInfo? {
        val t = input.trim()
        Pix.parse(t)?.let { return fromPix(it, t) }
        Pix.parse(t.replace("\n", "").replace("\r", ""))?.let { return fromPix(it, t) }
        Boleto.parse(t, today)?.let { return fromBoleto(it, t) }
        return null
    }

    private fun fromPix(p: Pix.Info, raw: String): BillInfo {
        val ex = short(raw)
        return BillInfo(amount = p.amount?.let { Found(it, Source.PIX, ex) }, payee = p.name, dynamicPix = p.dynamic && p.amount == null)
    }

    private fun fromBoleto(b: Boleto.Info, raw: String): BillInfo {
        val ex = short(raw)
        return BillInfo(
            amount = b.amount?.let { Found(it, Source.BOLETO, ex) },
            due = b.due?.let { Found(it, Source.BOLETO, ex) },
            payee = b.bank, segment = b.segment,
        )
    }

    /**
     * Texto extraído de uma fatura (PDF). Procura, nesta ordem de confiança:
     * 1. a linha digitável do boleto impressa na fatura (valor e vencimento exatos);
     * 2. um Pix copia e cola;
     * 3. rótulos como "Total da fatura" e "Vencimento" (o valor é o que vem logo depois).
     */
    fun fromText(text: String, today: LocalDate): BillInfo {
        val lines = text.split('\n').map { it.replace(' ', ' ').replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }
        val boleto = findBoleto(text, today)
        val pix = if (boleto?.amount == null) findPix(text) else null
        val textAmount = findAmount(lines)
        val textDue = findDue(lines, today)
        val amount = boleto?.amount ?: pix?.amount ?: textAmount
        val due = boleto?.due ?: textDue
        return BillInfo(amount, due, payee = pix?.payee ?: boleto?.payee, segment = boleto?.segment, dynamicPix = pix?.dynamicPix == true && amount == null)
    }

    // números com pontos, espaços e hífens no meio: a linha digitável impressa ("34191.79001 01043.510047 …")
    private val CODE_RUN = Regex("\\d[\\d .\\-]{41,80}\\d")

    private fun findBoleto(text: String, today: LocalDate): BillInfo? {
        var best: BillInfo? = null
        for (m in CODE_RUN.findAll(text.replace('\n', ' '))) {
            val digits = m.value.filter { it in '0'..'9' }
            // o trecho pode vir grudado em outros números: tenta as janelas de 47 e 48 algarismos
            for (len in intArrayOf(47, 48, 44)) {
                if (digits.length < len) continue
                for (start in 0..(digits.length - len)) {
                    val b = Boleto.parse(digits.substring(start, start + len), today) ?: continue
                    val info = fromBoleto(b, m.value.trim())
                    if (info.amount != null) return info
                    if (best == null) best = info
                }
            }
        }
        return best
    }

    private fun findPix(text: String): BillInfo? {
        for (variant in listOf(text.replace("\r", "").replace("\n", ""), text.filterNot { it.isWhitespace() })) {
            var from = 0
            while (true) {
                val i = variant.indexOf("000201", from)
                if (i < 0) break
                val j = variant.indexOf("6304", i)
                var k = j
                // o "6304" pode aparecer dentro do código: tenta cada ocorrência até o CRC conferir
                while (k >= 0 && k + 8 <= variant.length) {
                    val cand = variant.substring(i, k + 8)
                    Pix.parse(cand)?.let { return fromPix(it, cand) }
                    k = variant.indexOf("6304", k + 1)
                }
                from = i + 6
            }
        }
        return null
    }

    // ------------------------------------------------------------------ valor por rótulo
    // procurado no texto já em minúsculas e sem acento ("r$")
    private val MONEY = Regex("(?<![\\d,.])(?:r\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})+|\\d+),(\\d{2})(?![\\d])")

    /** rótulo (sem acento, minúsculo) → peso */
    private val AMOUNT_LABELS = listOf(
        "total desta fatura" to 100, "total da fatura" to 100, "valor total da fatura" to 100, "total da sua fatura" to 100,
        "valor da fatura" to 95, "valor total a pagar" to 95, "total a pagar" to 90, "valor a pagar" to 90,
        "valor do documento" to 85, "valor cobrado" to 80, "pagamento total" to 80, "valor total" to 60, "total geral" to 60,
    )

    /** linhas com estas palavras falam de outro valor (mínimo, limite, fatura anterior, juros…) */
    private val AMOUNT_SKIP = listOf(
        "minimo", "limite", "anterior", "parcelamento", "parcelar", "juros", "encargo", "multa", "iof", "cet ",
        "rotativo", "disponivel", "saque", "desconto", "proxima", "proximo",
    )

    private fun findAmount(lines: List<String>): Found<Cents>? {
        var best: Found<Cents>? = null; var bestW = -1
        val norm = lines.map { fold(it) }
        for ((i, l) in norm.withIndex()) {
            for ((label, w) in AMOUNT_LABELS) {
                if (w <= bestW) continue
                val at = l.indexOf(label)
                if (at < 0) continue
                // "Total da fatura anterior", "Pagamento mínimo do total da fatura": o rótulo é de outro valor
                if (skips(l.substring(maxOf(0, at - 25), at))) continue
                val end = at + label.length
                val same = MONEY.find(l, end)
                val v: Pair<Cents, String>? = if (same != null) {
                    if (skips(l.substring(end, same.range.first))) null else money(same)?.let { it to lines[i] }
                } else (1..2).firstNotNullOfOrNull { k ->
                    val n = norm.getOrNull(i + k) ?: return@firstNotNullOfOrNull null
                    val m = MONEY.find(n) ?: return@firstNotNullOfOrNull null
                    if (skips(n.substring(0, m.range.first))) null else money(m)?.let { it to (lines[i] + " " + lines[i + k]) }
                }
                if (v != null && v.first > 0) { best = Found(v.first, Source.TEXT, short(v.second)); bestW = w }
            }
        }
        return best
    }

    private fun skips(s: String) = AMOUNT_SKIP.any { s.contains(it) }
    private fun money(m: MatchResult): Cents? = m.groupValues[1].replace(".", "").toLongOrNull()?.let { it * 100 + m.groupValues[2].toLong() }

    // ------------------------------------------------------------------ vencimento por rótulo
    private val DUE_LABELS = listOf("data de vencimento", "vencimento", "vence em", "pague ate", "pagar ate", "pagamento ate")
    private val NUM_DATE = Regex("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?!\\d)")
    private val MONTHS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val NAME_DATE = Regex("(?<!\\d)(\\d{1,2})(?:\\s+de)?\\s+(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-z]*\\.?(?:(?:\\s+de)?\\s+(\\d{4}))?")

    private fun findDue(lines: List<String>, today: LocalDate): Found<LocalDate>? {
        for ((i, raw) in lines.withIndex()) {
            val l = fold(raw)
            for (label in DUE_LABELS) {
                val at = l.indexOf(label)
                if (at < 0) continue
                // "Próximo vencimento", "vencimento da fatura anterior": é de outra fatura
                val before = l.substring(maxOf(0, at - 25), at)
                if (before.contains("proxim") || before.contains("anterior") || l.substring(at).startsWith(label + " da fatura anterior")) break
                dateIn(l.substring(at + label.length), today)?.let { return Found(it, Source.TEXT, short(raw)) }
                for (k in 1..2) {
                    val n = lines.getOrNull(i + k) ?: break
                    val nf = fold(n)
                    if (nf.contains("proxim")) break
                    dateIn(nf, today)?.let { return Found(it, Source.TEXT, short("$raw $n")) }
                }
                break
            }
        }
        return null
    }

    /** Primeira data do trecho: 15/11/2026, 15.11.26, "15 de novembro de 2026", "15 NOV" (sem ano: a próxima ocorrência). */
    fun dateIn(s: String, today: LocalDate): LocalDate? {
        val f = fold(s)
        val a = NUM_DATE.find(f)
        val b = NAME_DATE.find(f)
        val first = listOfNotNull(a, b).minByOrNull { it.range.first } ?: return null
        return if (first === a) {
            val y = a.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            safeDate(y, a.groupValues[2].toInt(), a.groupValues[1].toInt())
        } else {
            val m = MONTHS.indexOf(b!!.groupValues[2]) + 1
            val d = b.groupValues[1].toInt()
            val y = b.groupValues[3].toIntOrNull()
            if (y != null) safeDate(y, m, d)
            else (-1..1).mapNotNull { safeDate(today.year + it, m, d) }.firstOrNull { !it.isBefore(today.minusDays(60)) }
        }
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        if (y in 2000..2100 && m in 1..12 && d in 1..31 && d <= YearMonth.of(y, m).lengthOfMonth()) LocalDate.of(y, m, d) else null

    /** minúsculas e sem acento (para achar "Mínimo" e "minimo" igual) */
    private fun fold(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun short(s: String) = s.replace(Regex("\\s+"), " ").trim().let { if (it.length > 90) it.take(87) + "…" else it }
}

// ------------------------------------------------------------------------------ fatura do cartão
/** Comparação com a fatura que o Finan+ calcula pelas compras lançadas no cartão. */
object BillMatch {
    /** Mês da fatura do cartão que vence no mês de [due]; sem vencimento, a fatura em aberto (ou a do mês). */
    fun invoiceYm(s: AppState, card: Card, due: LocalDate?, today: LocalDate): YearMonth {
        if (due != null) {
            val target = due.ym()
            for (ym in listOf(target, target.minusMonths(1), target.minusMonths(2))) if (Finance.invoiceDue(card, ym).ym() == target) return ym
        }
        return Finance.cardStatus(s, card, today).current?.ym ?: Finance.invoiceYm(card, today)
    }

    /** Total da fatura [ym] no Finan+ (compras do cartão naquele mês de fatura). */
    fun appTotal(s: AppState, card: Card, ym: YearMonth): Cents =
        s.txs.filter { it.cardId == card.id && it.kind == com.finanplus.core.Kind.EXPENSE && Finance.invoiceYm(card, it.date) == ym }.sumOf { it.value }

    /** Data de uma compra que cai na fatura [ym]: o dia do fechamento. */
    fun dateInInvoice(card: Card, ym: YearMonth): LocalDate = Finance.invoiceClose(card, ym)
}
