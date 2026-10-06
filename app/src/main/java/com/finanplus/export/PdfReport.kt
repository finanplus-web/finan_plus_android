// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.finanplus.core.AppState
import com.finanplus.core.Cents
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Tx
import com.finanplus.core.assist.Br
import com.finanplus.core.report.CategoryRow
import com.finanplus.core.report.Report
import com.finanplus.core.report.Reports
import java.io.OutputStream
import java.time.LocalDateTime

/**
 * Relatório financeiro em PDF (A4 retrato), desenhado com o PdfDocument do próprio Android:
 * sem bibliotecas, sem internet. Os números vêm de [Reports.build] (core, testado).
 *
 * O layout roda duas vezes: a primeira só conta as páginas (para o rodapé "Página n de N"),
 * a segunda desenha.
 */
object PdfReport {
    private const val W = 595f
    private const val H = 842f
    private const val M = 36f
    private const val CW = W - 2 * M
    private const val FOOTER = 28f

    // cores fixas (sempre claro, pensado para tela e impressão)
    private const val INK = 0xFF182238.toInt()
    private const val MUTED = 0xFF5B6579.toInt()
    private const val ACCENT = 0xFF3A5FC8.toInt()
    private const val GREEN = 0xFF1B7351.toInt()
    private const val RED = 0xFFB03A4F.toInt()
    private const val CARD = 0xFFF3F6FD.toInt()
    private const val LINE = 0xFFDDE3EE.toInt()
    private const val TRACK = 0xFFE6EBF5.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private val SERIES = intArrayOf(
        0xFF3A5FC8.toInt(), 0xFFE07A2F.toInt(), 0xFF1B9E77.toInt(), 0xFFB03A4F.toInt(),
        0xFF7B61C9.toInt(), 0xFF2A9DB5.toInt(), 0xFFC49A1A.toInt(), 0xFF8A8F99.toInt(),
    )

    data class Options(val includeTransactions: Boolean = true, val appVersion: String = "")

    fun write(out: OutputStream, r: Report, s: AppState, opt: Options) {
        val count = Pen(null, 0, r).also { layout(it, r, s, opt); it.finish() }.pages
        val doc = PdfDocument()
        try {
            Pen(doc, count, r).also { layout(it, r, s, opt); it.finish() }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // ------------------------------------------------------------------ "caneta" com paginação

    private class Pen(val doc: PdfDocument?, val total: Int, val r: Report) {
        var pages = 0
        var y = 0f
        private var page: PdfDocument.Page? = null
        val c: Canvas? get() = page?.canvas
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        fun newPage() {
            finishPage()
            pages++
            if (doc != null) page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pages).create())
            if (pages == 1) y = 0f else {
                // cabeçalho das páginas seguintes
                text("Finan+ · Relatório financeiro · ${Br.date(r.from)} a ${Br.date(r.to)}", M, M + 4, 8f, MUTED)
                line(M, M + 12, W - M, M + 12)
                y = M + 26
            }
        }

        private fun finishPage() {
            if (pages == 0) return
            text("Gerado no aparelho pelo Finan+ · os dados não saem do celular", M, H - 18, 7.5f, MUTED)
            text("Página $pages de ${if (total > 0) total else pages}", W - M, H - 18, 7.5f, MUTED, align = Paint.Align.RIGHT)
            page?.let { doc?.finishPage(it) }
            page = null
        }

        fun finish() = finishPage()

        /** garante espaço vertical [h]; senão, nova página */
        fun need(h: Float) { if (pages == 0 || y + h > H - M - FOOTER) newPage() }

        fun font(size: Float, b: Boolean) { p.textSize = size; p.typeface = if (b) bold else Typeface.DEFAULT }

        fun text(t: String, x: Float, base: Float, size: Float, color: Int, b: Boolean = false, align: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f) {
            font(size, b); p.color = color; p.textAlign = align; p.style = Paint.Style.FILL
            c?.drawText(if (maxW > 0) fit(t, maxW) else t, x, base, p)
        }

        fun fit(t: String, maxW: Float): String {
            if (p.measureText(t) <= maxW) return t
            var s = t
            while (s.length > 1 && p.measureText("$s…") > maxW) s = s.dropLast(1)
            return "$s…"
        }

        fun rect(x: Float, y0: Float, w: Float, h: Float, color: Int, radius: Float = 0f) {
            p.color = color; p.style = Paint.Style.FILL
            if (radius > 0) c?.drawRoundRect(RectF(x, y0, x + w, y0 + h), radius, radius, p) else c?.drawRect(x, y0, x + w, y0 + h, p)
        }

        fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int = LINE, w: Float = 0.7f) {
            p.color = color; p.strokeWidth = w; p.style = Paint.Style.STROKE
            c?.drawLine(x0, y0, x1, y1, p)
            p.style = Paint.Style.FILL
        }
    }

    // ------------------------------------------------------------------ conteúdo

    private fun money(c: Cents) = Money.format(c)

    private fun pct(v: Double) = (Math.round(v * 10) / 10.0).toString().replace('.', ',') + "%"

    private fun changeText(v: Double?, label: String): String =
        if (v == null) "sem base no período anterior" else (if (v >= 0) "+" else "−") + pct(Math.abs(v)) + " vs. $label"

    private fun layout(pen: Pen, r: Report, s: AppState, opt: Options) {
        pen.newPage()
        header(pen, r)
        kpis(pen, r)
        section(pen, "Despesas por categoria", "Valores realizados. Média mensal comparada com o limite mensal definido em Ajustes.", keep = 160f)
        if (r.expenseByCategory.isEmpty()) note(pen, "Sem despesas realizadas no período.")
        else { donut(pen, r.expenseByCategory, r.expense); categoryTable(pen, r.expenseByCategory, withLimit = true) }

        section(pen, "Evolução mensal", if (r.months.size > 1) "Receitas e despesas realizadas por mês." else "Receitas e despesas realizadas no mês.",
            keep = if (r.months.size > 1) 200f else 60f)
        if (r.months.size > 1) monthChart(pen, r)
        monthTable(pen, r)

        section(pen, "Receitas por categoria", "Valores recebidos no período.")
        if (r.incomeByCategory.isEmpty()) note(pen, "Sem receitas recebidas no período.") else categoryTable(pen, r.incomeByCategory, withLimit = false)

        section(pen, "Maiores despesas", "As ${Reports.TOP} maiores despesas realizadas do período.")
        if (r.topExpenses.isEmpty()) note(pen, "Sem despesas realizadas no período.") else topTable(pen, r.topExpenses)

        section(pen, "Contas e metas", "Saldos e metas na data de hoje (não dependem do período).")
        accountsAndGoals(pen, r)

        if (opt.includeTransactions) {
            section(pen, "Lançamentos do período", "${r.txs.size} lançamento(s), incluindo pendentes. Pagamentos de fatura aparecem, mas não contam como despesa.")
            if (r.txs.isEmpty()) note(pen, "Nenhum lançamento no período.") else txTable(pen, r, s)
        }
        section(pen, "Como ler este relatório", "", keep = 40f)
        for (l in listOf(
            "Receitas e despesas consideram só o que foi pago ou recebido. O que está pendente aparece em “A receber” e “A pagar”.",
            "Compras no cartão contam na data da compra; o pagamento da fatura não é uma despesa nova.",
            "A comparação usa o período anterior com o mesmo número de dias (${Br.date(r.prevFrom)} a ${Br.date(r.prevTo)}).",
            "Média mensal = valor da categoria ÷ meses do período (meses incompletos contam pela fração de dias).",
        )) para(pen, "• $l")
    }

    private fun header(pen: Pen, r: Report) {
        pen.rect(0f, 0f, W, 112f, ACCENT)
        pen.text("FINAN+", M, 40f, 10f, 0xCCFFFFFF.toInt(), b = true)
        pen.text("Relatório financeiro", M, 68f, 24f, WHITE, b = true)
        pen.text("${Br.date(r.from)} a ${Br.date(r.to)} · ${r.days} dia(s)", M, 92f, 12f, WHITE)
        val now = LocalDateTime.now()
        pen.text("Gerado em ${Br.date(now.toLocalDate())} às %02d:%02d".format(now.hour, now.minute), W - M, 40f, 9f, 0xDDFFFFFF.toInt(), align = Paint.Align.RIGHT)
        pen.y = 132f
    }

    private fun kpis(pen: Pen, r: Report) {
        pen.need(150f)
        val gap = 10f
        val w3 = (CW - 2 * gap) / 3
        val big = listOf(
            Triple("Receitas", r.income, GREEN) to changeText(r.change(r.income, r.prevIncome), "período anterior"),
            Triple("Despesas", r.expense, RED) to changeText(r.change(r.expense, r.prevExpense), "período anterior"),
            Triple("Saldo do período", r.balance, if (r.balance < 0) RED else ACCENT) to (r.savingsRate?.let { "${pct(it)} das receitas" } ?: "sem receitas no período"),
        )
        big.forEachIndexed { i, (t, sub) ->
            val x = M + i * (w3 + gap)
            pen.rect(x, pen.y, w3, 72f, CARD, 12f)
            pen.text(t.first, x + 12, pen.y + 20, 9f, MUTED)
            pen.text(money(t.second), x + 12, pen.y + 44, 16f, t.third, b = true, maxW = w3 - 24)
            pen.text(sub, x + 12, pen.y + 61, 7.5f, MUTED, maxW = w3 - 24)
        }
        pen.y += 82f
        val w4 = (CW - 3 * gap) / 4
        val small = listOf(
            "A receber (pendente)" to money(r.pendingIncome),
            "A pagar (pendente)" to money(r.pendingExpense),
            "Média diária de gastos" to money(r.dailyAverage),
            "Lançamentos" to r.txs.size.toString(),
        )
        small.forEachIndexed { i, (l, v) ->
            val x = M + i * (w4 + gap)
            pen.rect(x, pen.y, w4, 48f, CARD, 10f)
            pen.text(l, x + 10, pen.y + 17, 8f, MUTED, maxW = w4 - 20)
            pen.text(v, x + 10, pen.y + 36, 12f, INK, b = true, maxW = w4 - 20)
        }
        pen.y += 60f
    }

    /** Título de seção. [keep] = altura mínima do conteúdo que precisa caber junto (o título nunca fica sozinho no pé da página). */
    private fun section(pen: Pen, title: String, sub: String, keep: Float = 60f) {
        pen.need(56f + keep)
        pen.y += 12f
        pen.text(title, M, pen.y + 14, 14f, INK, b = true)
        pen.y += 22f
        if (sub.isNotEmpty()) para(pen, sub)
        pen.y += 2f
    }

    private fun note(pen: Pen, t: String) { pen.need(20f); pen.text(t, M, pen.y + 12, 9.5f, MUTED); pen.y += 22f }

    private fun para(pen: Pen, t: String) {
        // quebra simples por palavras
        pen.font(8.5f, false)
        val words = t.split(' ')
        var line = ""
        for (w in words) {
            val cand = if (line.isEmpty()) w else "$line $w"
            if (pen.p.measureText(cand) > CW) { pen.need(13f); pen.text(line, M, pen.y + 10, 8.5f, MUTED); pen.y += 13f; line = w } else line = cand
        }
        if (line.isNotEmpty()) { pen.need(13f); pen.text(line, M, pen.y + 10, 8.5f, MUTED); pen.y += 13f }
        pen.y += 3f
    }

    /** Rosca com as 7 maiores categorias + "Outras", e legenda ao lado. */
    private fun donut(pen: Pen, rows: List<CategoryRow>, total: Cents) {
        pen.need(160f)
        val top = rows.take(7).toMutableList()
        val rest = rows.drop(7)
        val slices = top.map { it.name to it.value } + if (rest.isNotEmpty()) listOf("Outras (${rest.size})" to rest.sumOf { it.value }) else emptyList()
        val d = 130f
        val cx = M + d / 2 + 6; val cy = pen.y + d / 2 + 6
        val oval = RectF(cx - d / 2 + 11, cy - d / 2 + 11, cx + d / 2 - 11, cy + d / 2 - 11)
        var start = -90f
        pen.p.style = Paint.Style.STROKE; pen.p.strokeWidth = 22f; pen.p.strokeCap = Paint.Cap.BUTT
        slices.forEachIndexed { i, (_, v) ->
            val sweep = if (total > 0) 360f * v / total else 0f
            pen.p.color = SERIES[i % SERIES.size]
            if (sweep > 0) pen.c?.drawArc(oval, start, maxOf(sweep - 0.8f, 0.5f), false, pen.p)
            start += sweep
        }
        pen.p.style = Paint.Style.FILL
        pen.text("Total", cx, cy - 4, 8f, MUTED, align = Paint.Align.CENTER)
        pen.text(Reports.compact(total), cx, cy + 11, 11f, INK, b = true, align = Paint.Align.CENTER)
        // legenda
        val lx = M + d + 30
        var ly = pen.y + 14
        slices.forEachIndexed { i, (name, v) ->
            pen.rect(lx, ly - 8, 9f, 9f, SERIES[i % SERIES.size], 2f)
            pen.text(name, lx + 16, ly, 9.5f, INK, maxW = 200f)
            pen.text(money(v), W - M - 52, ly, 9.5f, INK, b = true, align = Paint.Align.RIGHT)
            pen.text(pct(if (total > 0) v * 100.0 / total else 0.0), W - M, ly, 9f, MUTED, align = Paint.Align.RIGHT)
            ly += 16f
        }
        pen.y += maxOf(d + 16, ly - pen.y + 4)
    }

    private fun tableHeader(pen: Pen, cols: List<Pair<String, Float>>, rightFrom: Int) {
        pen.need(40f)
        pen.rect(M, pen.y, CW, 18f, CARD, 4f)
        var x = M + 6
        cols.forEachIndexed { i, (t, w) ->
            if (i >= rightFrom) pen.text(t, x + w - 8, pen.y + 12.5f, 8f, MUTED, b = true, align = Paint.Align.RIGHT)
            else pen.text(t, x, pen.y + 12.5f, 8f, MUTED, b = true)
            x += w
        }
        pen.y += 22f
    }

    private fun categoryTable(pen: Pen, rows: List<CategoryRow>, withLimit: Boolean) {
        val cols = if (withLimit) listOf("Categoria" to 128f, "" to 92f, "%" to 46f, "Lanç." to 38f, "Média/mês" to 74f, "Limite/mês" to 70f, "Valor" to (CW - 448f))
        else listOf("Categoria" to 150f, "" to 150f, "%" to 50f, "Lanç." to 50f, "Valor" to (CW - 400f))
        val right = 2
        tableHeader(pen, cols, right)
        rows.forEachIndexed { i, row ->
            if (pen.y + 18 > H - M - FOOTER) { pen.newPage(); tableHeader(pen, cols, right) }
            val base = pen.y + 11
            var x = M + 6
            val color = SERIES[minOf(i, 7) % SERIES.size]
            pen.text(row.name, x, base, 9f, INK, maxW = cols[0].second - 8); x += cols[0].second
            pen.rect(x, pen.y + 5, cols[1].second - 10, 6f, TRACK, 3f)
            pen.rect(x, pen.y + 5, maxOf(2f, (cols[1].second - 10) * (row.percent / 100.0).toFloat()), 6f, color, 3f); x += cols[1].second
            pen.text(pct(row.percent), x + cols[2].second - 8, base, 8.5f, MUTED, align = Paint.Align.RIGHT); x += cols[2].second
            pen.text(row.count.toString(), x + cols[3].second - 8, base, 8.5f, MUTED, align = Paint.Align.RIGHT); x += cols[3].second
            if (withLimit) {
                pen.text(money(row.monthlyAverage), x + cols[4].second - 8, base, 8.5f, if (row.overLimit) RED else INK, align = Paint.Align.RIGHT); x += cols[4].second
                pen.text(row.monthlyLimit?.let { money(it) + if (row.overLimit) " · acima" else "" } ?: "—", x + cols[5].second - 8, base, 8f,
                    if (row.overLimit) RED else MUTED, align = Paint.Align.RIGHT, maxW = cols[5].second - 6); x += cols[5].second
            }
            pen.text(money(row.value), x + cols.last().second - 8, base, 9f, INK, b = true, align = Paint.Align.RIGHT)
            pen.line(M, pen.y + 17, W - M, pen.y + 17)
            pen.y += 18f
        }
        pen.y += 6f
    }

    private fun monthLabel(ym: java.time.YearMonth) = Br.MONTHS[ym.monthValue - 1].take(3) + "/" + (ym.year % 100).toString().padStart(2, '0')

    private fun monthChart(pen: Pen, r: Report) {
        val months = r.months.takeLast(Reports.MAX_CHART_MONTHS)
        val ch = 150f
        pen.need(ch + 40)
        val max = months.maxOf { maxOf(it.income, it.expense) }
        val step = Reports.niceStep(max)
        val top = maxOf(step, ((max + step - 1) / step) * step)
        val left = M + 52; val right = W - M; val y0 = pen.y + 6; val y1 = y0 + ch
        var v = 0L
        while (v <= top) {
            val yy = y1 - (ch * v / top).toFloat()
            pen.line(left, yy, right, yy)
            pen.text(Reports.compact(v), left - 6, yy + 3, 7.5f, MUTED, align = Paint.Align.RIGHT)
            v += step
        }
        val slot = (right - left) / months.size
        val bw = minOf(14f, slot * 0.32f)
        months.forEachIndexed { i, m ->
            val x = left + i * slot + slot / 2
            val hi = (ch * m.income / top).toFloat(); val he = (ch * m.expense / top).toFloat()
            if (hi > 0) pen.rect(x - bw - 1, y1 - hi, bw, hi, GREEN, 2f)
            if (he > 0) pen.rect(x + 1, y1 - he, bw, he, RED, 2f)
            if (months.size <= 12 || i % 2 == 0) pen.text(monthLabel(m.ym), x, y1 + 12, 7.5f, MUTED, align = Paint.Align.CENTER)
        }
        pen.y = y1 + 22
        pen.rect(left, pen.y - 7, 8f, 8f, GREEN, 2f); pen.text("Receitas", left + 12, pen.y, 8f, MUTED)
        pen.rect(left + 70, pen.y - 7, 8f, 8f, RED, 2f); pen.text("Despesas", left + 82, pen.y, 8f, MUTED)
        if (r.months.size > months.size) pen.text("Gráfico com os últimos ${months.size} meses do período; a tabela abaixo traz todos.", right, pen.y, 7.5f, MUTED, align = Paint.Align.RIGHT)
        pen.y += 14f
    }

    private fun monthTable(pen: Pen, r: Report) {
        val cols = listOf("Mês" to 160f, "Receitas" to 120f, "Despesas" to 120f, "Saldo" to (CW - 400f))
        tableHeader(pen, cols, 1)
        for (m in r.months) {
            if (pen.y + 18 > H - M - FOOTER) { pen.newPage(); tableHeader(pen, cols, 1) }
            val base = pen.y + 11
            var x = M + 6
            pen.text(Br.monthYear(m.ym).replaceFirstChar { it.uppercase() }, x, base, 9f, INK); x += cols[0].second
            pen.text(money(m.income), x + cols[1].second - 8, base, 9f, GREEN, align = Paint.Align.RIGHT); x += cols[1].second
            pen.text(money(m.expense), x + cols[2].second - 8, base, 9f, RED, align = Paint.Align.RIGHT); x += cols[2].second
            pen.text(money(m.balance), x + cols[3].second - 8, base, 9f, if (m.balance < 0) RED else INK, b = true, align = Paint.Align.RIGHT)
            pen.line(M, pen.y + 17, W - M, pen.y + 17)
            pen.y += 18f
        }
        pen.y += 6f
    }

    private fun topTable(pen: Pen, list: List<Tx>) {
        val cols = listOf("Data" to 70f, "Descrição" to 230f, "Categoria" to 120f, "Valor" to (CW - 420f))
        tableHeader(pen, cols, 3)
        for (t in list) {
            if (pen.y + 18 > H - M - FOOTER) { pen.newPage(); tableHeader(pen, cols, 3) }
            val base = pen.y + 11
            var x = M + 6
            pen.text(Br.date(t.date), x, base, 8.5f, MUTED); x += cols[0].second
            pen.text(t.desc, x, base, 9f, INK, maxW = cols[1].second - 8); x += cols[1].second
            pen.text(t.category, x, base, 8.5f, MUTED, maxW = cols[2].second - 8); x += cols[2].second
            pen.text(money(t.value), x + cols[3].second - 8, base, 9f, RED, b = true, align = Paint.Align.RIGHT)
            pen.line(M, pen.y + 17, W - M, pen.y + 17)
            pen.y += 18f
        }
        pen.y += 6f
    }

    private fun accountsAndGoals(pen: Pen, r: Report) {
        val cols = listOf("Conta" to 300f, "Saldo atual" to (CW - 300f))
        tableHeader(pen, cols, 1)
        for ((name, bal) in r.accounts) {
            if (pen.y + 18 > H - M - FOOTER) { pen.newPage(); tableHeader(pen, cols, 1) }
            pen.text(name, M + 6, pen.y + 11, 9f, INK, maxW = 290f)
            pen.text(money(bal), W - M - 8, pen.y + 11, 9f, if (bal < 0) RED else INK, b = true, align = Paint.Align.RIGHT)
            pen.line(M, pen.y + 17, W - M, pen.y + 17)
            pen.y += 18f
        }
        pen.y += 8f
        if (r.goals.isEmpty()) { note(pen, "Nenhuma meta cadastrada."); return }
        for (g in r.goals) {
            pen.need(34f)
            pen.text(g.name, M, pen.y + 11, 9.5f, INK, b = true, maxW = 260f)
            pen.text("${money(g.saved)} de ${money(g.target)} · ${pct(g.percent)}" + (g.deadline?.let { " · até ${Br.date(it)}" } ?: ""),
                W - M, pen.y + 11, 8.5f, MUTED, align = Paint.Align.RIGHT, maxW = 260f)
            pen.rect(M, pen.y + 17, CW, 7f, TRACK, 3.5f)
            pen.rect(M, pen.y + 17, maxOf(3f, CW * (g.percent / 100).toFloat()), 7f, ACCENT, 3.5f)
            pen.y += 32f
        }
    }

    private fun txTable(pen: Pen, r: Report, s: AppState) {
        val cols = listOf("Data" to 58f, "Descrição" to 150f, "Categoria" to 90f, "Conta / cartão" to 86f, "Situação" to 62f, "Valor" to (CW - 446f))
        tableHeader(pen, cols, 5)
        r.txs.forEachIndexed { i, t ->
            if (pen.y + 17 > H - M - FOOTER) { pen.newPage(); tableHeader(pen, cols, 5) }
            if (i % 2 == 1) pen.rect(M, pen.y, CW, 17f, 0xFFF8FAFE.toInt())
            val base = pen.y + 11.5f
            var x = M + 6
            val payment = !t.isFlow
            val where = when {
                t.isCard -> "Cartão " + (s.card(t.cardId)?.name ?: "")
                payment -> s.account(t.accountId)?.name ?: ""
                else -> s.account(t.accountId)?.name ?: ""
            }
            val status = when {
                payment -> "Pag. fatura"
                t.isCard -> "Cartão"
                t.paid -> if (t.kind == Kind.INCOME) "Recebido" else "Pago"
                else -> if (t.kind == Kind.INCOME) "A receber" else "A pagar"
            }
            pen.text(Br.date(t.date), x, base, 8f, MUTED); x += cols[0].second
            pen.text(t.desc, x, base, 8.5f, if (payment) MUTED else INK, maxW = cols[1].second - 8); x += cols[1].second
            pen.text(t.category, x, base, 8f, MUTED, maxW = cols[2].second - 8); x += cols[2].second
            pen.text(where, x, base, 8f, MUTED, maxW = cols[3].second - 8); x += cols[3].second
            pen.text(status, x, base, 8f, if (!t.paid && !payment) ACCENT else MUTED, maxW = cols[4].second - 6); x += cols[4].second
            val color = when { payment -> MUTED; t.kind == Kind.INCOME -> GREEN; else -> RED }
            pen.text((if (t.kind == Kind.INCOME) "+ " else "− ") + money(t.value), x + cols[5].second - 8, base, 8.5f, color, b = !payment, align = Paint.Align.RIGHT)
            pen.y += 17f
        }
        pen.line(M, pen.y, W - M, pen.y)
        pen.y += 8f
    }
}
