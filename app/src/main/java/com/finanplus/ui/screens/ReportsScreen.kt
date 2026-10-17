// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Money
import com.finanplus.core.Period
import com.finanplus.core.PeriodCompare
import com.finanplus.core.ym
import com.finanplus.ui.LocalNav
import com.finanplus.ui.MovesView
import com.finanplus.ui.Sheet
import com.finanplus.ui.Tab
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.BR
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin
import java.time.format.DateTimeFormatter

/**
 * Análises: o mesmo período da aba Lançamentos (‹ mês › e período livre), só com valores realizados.
 * Resumo com comparação justa, despesas por categoria e evolução.
 */
@Composable
fun ReportsScreen(s: AppState) {
    val nav = LocalNav.current
    val f = nav.filters
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val inRange = remember(s, f.from, f.to) { s.txs.filter { f.inRange(it) } }
    val flow = remember(inRange) { Finance.flow(inRange) }
    val cmp = remember(f.from, f.to, today) { PeriodCompare.of(f.from, f.to, today) }
    val prev = remember(s, cmp) { cmp?.let { c -> Finance.flow(s.txs.filter { !it.date.isBefore(c.from) && !it.date.isAfter(c.to) }) } }
    val categories = remember(s, f.from, f.to) { Finance.categoryTotals(s, f.from, f.to) }
    val months = remember(s, today) { Finance.lastMonths(s, today) }
    val hasHistory = months.any { it.second.income > 0L || it.second.expense > 0L }

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Análises", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                // exportar o mesmo período em PDF (dá para trocar o período na própria folha)
                Pill("PDF", icon = Ico.RECEIPT, modifier = Modifier.semantics { contentDescription = "Exportar relatório em PDF" }) {
                    nav.open(Sheet.ReportPdf(f.from, f.to))
                }
            }
        }
        item { PeriodBar(nav, today) }
        item {
            Text(
                "Só valores realizados (pagos ou recebidos). O período é o mesmo da aba Lançamentos.",
                style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        if (flow.income == 0L && flow.expense == 0L) item {
            // nada realizado: em vez de zeros, mostra o que está pendente e leva ao calendário
            val pend = remember(inRange) { Period.pending(inRange) }
            Glass(Modifier.fillMaxWidth().padding(top = 10.dp), radius = 24.dp, padding = 14.dp) {
                val name = Period.fullMonth(f.from, f.to)?.let { com.finanplus.core.MonthCalendar.monthName(it) } ?: "este período"
                Text("Nada realizado em $name ainda", fontWeight = FontWeight.Bold)
                if (pend.toReceive > 0 || pend.toPay > 0) {
                    Text("Os relatórios mostram o que já foi pago ou recebido. Por enquanto, está pendente:", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniBox("A receber", pend.toReceive, p.green, Modifier.weight(1f))
                        MiniBox("A pagar", pend.toPay, p.red, Modifier.weight(1f))
                    }
                } else Text("Os relatórios mostram o que já foi pago ou recebido. Troque o período ou marque lançamentos como pagos.", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                        nav.movesView = MovesView.CALENDAR
                        nav.calMonth = (f.from ?: today).ym()
                        nav.tab = Tab.MOVES
                    }.padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ver no calendário", color = p.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    AppIcon(Ico.NEXT, p.accent, size = 18.dp)
                }
            }
        } else item {
            // com "Ocultar valores", a variação percentual também fica oculta (revelaria a proporção entre os períodos)
            fun chg(cur: Long, old: Long?) = when {
                hide -> "Variação oculta"
                cmp == null || old == null -> ""
                else -> PeriodCompare.text(cur, old, cmp)
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SumBox("Receitas", Ico.UP, flow.income, p.green, chg(flow.income, prev?.income), Modifier.weight(1f))
                SumBox("Despesas", Ico.DOWN, flow.expense, p.red, chg(flow.expense, prev?.expense), Modifier.weight(1f))
            }
        }
        if (flow.income > 0L || flow.expense > 0L) item {
            val result = flow.balance
            val explanation = when {
                hide -> "Os relatórios consideram somente valores já pagos ou recebidos."
                flow.income == flow.expense -> "Neste período, você gastou o mesmo valor que recebeu."
                flow.expense > flow.income -> "Você gastou ${Money.format(flow.expense - flow.income)} a mais do que recebeu."
                else -> "Depois das despesas, sobraram ${Money.format(flow.income - flow.expense)}."
            }
            Glass(Modifier.padding(top = 8.dp), radius = 20.dp, padding = 14.dp) {
                Text("Resultado do período", style = MaterialTheme.typography.bodySmall, color = p.muted)
                MoneyText(result, color = if (result < 0) p.red else p.accent, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                Text(explanation, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (flow.income > 0L || flow.expense > 0L) item {
            val sources = remember(inRange) {
                inRange.asSequence()
                    .filter { it.kind == com.finanplus.core.Kind.INCOME && it.paid && it.isFlow }
                    .groupBy { it.category.ifBlank { "Sem categoria" } }
                    .map { (category, txs) -> category to txs.sumOf { it.value } }
                    .sortedByDescending { it.second }
            }
            val expenses = remember(s, f.from, f.to) { Finance.categoryTotals(s, f.from, f.to) }
            MoneyFlowCard(sources, expenses, flow.income, flow.expense, hide)
        }
        if (categories.isNotEmpty()) item {
            Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
                Column2("Despesas", "Por categoria")
                val total = categories.sumOf { it.second }
                categories.forEach { (cat, v) ->
                    val lim = s.limits[cat]
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cat, Modifier.widthIn(min = 90.dp, max = 110.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Bar(if (total > 0) v.toFloat() / total else 0f, if (lim != null && v > lim) p.red else p.red.copy(alpha = 0.75f), Modifier.weight(1f).padding(horizontal = 8.dp).sensitive(hide), height = 10.dp)
                        MoneyText(v, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    if (lim != null) Text(
                        (if (v > lim) "Acima do" else "Dentro do") + " limite mensal de " + (if (hide) "R$ ••••" else Money.format(lim)),
                        style = MaterialTheme.typography.labelSmall, color = if (v > lim) p.red else p.muted,
                    )
                }
            }
        }
        if (hasHistory) item {
            Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
                Column2("Evolução", "Últimos 6 meses")
                val max = maxOf(1L, months.maxOf { maxOf(it.second.income, it.second.expense) })
                val desc = months.joinToString("; ") { (ym, fl) ->
                    ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", BR)) + if (hide) "" else ": receitas ${Money.format(fl.income)}, despesas ${Money.format(fl.expense)}"
                }
                // Gráfico com descrição completa para leitores de tela.
                Canvas(Modifier.fillMaxWidth().height(110.dp).sensitive(hide).semantics { contentDescription = "Gráfico de receitas e despesas. $desc" }) {
                    val slot = size.width / months.size
                    val bw = slot * 0.3f
                    months.forEachIndexed { i, (_, fl) ->
                        val x = i * slot + slot * 0.18f
                        val hi = size.height * fl.income / max
                        val he = size.height * fl.expense / max
                        // mês sem valor não ganha barra; valor pequeno ganha um traço mínimo de 2 px
                        if (fl.income > 0) drawRoundRect(p.green, Offset(x, size.height - maxOf(hi, 2f)), Size(bw, maxOf(hi, 2f)), CornerRadius(8f, 8f))
                        if (fl.expense > 0) drawRoundRect(p.red, Offset(x + bw + 4f, size.height - maxOf(he, 2f)), Size(bw, maxOf(he, 2f)), CornerRadius(8f, 8f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    months.forEach { (ym, _) -> Text(ym.format(DateTimeFormatter.ofPattern("MMM", BR)), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = TextAlign.Center) }
                }
                Text("Verde: receitas · Vermelho: despesas", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private data class FlowPart(val label: String, val value: Long, val color: Color)

/** Fluxo agregado do período: mostra origens e destinos sem atribuir uma receita a uma despesa específica. */
@Composable
private fun MoneyFlowCard(
    sources: List<Pair<String, Long>>,
    expenses: List<Pair<String, Long>>,
    income: Long,
    expense: Long,
    hide: Boolean,
) {
    val p = Fin.c
    val colors = listOf(Color(0xFF00AFC1), p.red, Color(0xFF278AE5), p.green, Color(0xFF8C9499), p.accent)
    val incomeParts = sources.mapIndexed { i, (name, value) -> FlowPart(name, value, colors[i % colors.size]) }
    val outputParts = expenses.mapIndexed { i, (name, value) -> FlowPart(name, value, colors[(i + 1) % colors.size]) }.toMutableList()
    val leftover = (income - expense).coerceAtLeast(0L)
    if (leftover > 0L) outputParts += FlowPart("Sobrou", leftover, p.green)
    val scale = maxOf(income, expense, 1L)
    val description = buildString {
        append("Fluxo do dinheiro. Entradas realizadas: ")
        append(if (hide) "valores ocultos" else Money.format(income))
        append(". Despesas realizadas: ")
        append(if (hide) "valores ocultos" else Money.format(expense))
        if (leftover > 0L) append(". Sobra: ").append(if (hide) "valor oculto" else Money.format(leftover))
        else if (expense > income) append(". Despesas acima das receitas: ").append(if (hide) "valor oculto" else Money.format(expense - income))
    }
    Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
        Column2("Análises", "Fluxo do dinheiro")
        Text("De onde veio (em cima) e para onde foi (embaixo).", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(bottom = 12.dp))
        Text("De onde veio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        SegmentedFlowBar(incomeParts, scale, hide)
        FlowLegend(incomeParts, income, hide)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                if (hide) "Entradas ocultas" else "Entrou ${Money.format(income)}",
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(p.text).padding(horizontal = 20.dp, vertical = 8.dp),
                color = p.bg,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Text("Para onde foi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        if (outputParts.isEmpty()) {
            Text("Nenhuma despesa realizada; todo o valor recebido permaneceu disponível.", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
        } else {
            SegmentedFlowBar(outputParts, scale, hide)
            FlowLegend(outputParts, maxOf(income, expense), hide)
        }
        if (expense > income) {
            Text("As despesas passaram o valor recebido neste período.", style = MaterialTheme.typography.bodySmall, color = p.red, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            "Considera apenas lançamentos pagos ou recebidos; não associa uma entrada a uma despesa específica.",
            style = MaterialTheme.typography.labelSmall,
            color = p.muted,
            modifier = Modifier.padding(top = 10.dp).semantics { contentDescription = description },
        )
    }
}

@Composable
private fun SegmentedFlowBar(parts: List<FlowPart>, scale: Long, hide: Boolean) {
    val p = Fin.c
    Canvas(
        Modifier.fillMaxWidth().height(14.dp).padding(top = 4.dp).sensitive(hide)
            .semantics { contentDescription = "Gráfico de barras proporcionais do fluxo de dinheiro" },
    ) {
        val totalWidth = size.width
        var left = 0f
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        parts.forEachIndexed { index, part ->
            val width = totalWidth * (part.value.toDouble() / scale.coerceAtLeast(1L)).toFloat()
            if (width > 0f) {
                drawRoundRect(part.color, Offset(left, 0f), Size(width, size.height), radius)
                left += width
            }
        }
        if (left < totalWidth) drawRoundRect(p.track, Offset(left, 0f), Size(totalWidth - left, size.height), radius)
    }
}

@Composable
private fun FlowLegend(parts: List<FlowPart>, total: Long, hide: Boolean) {
    val p = Fin.c
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        parts.forEach { part ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(12.dp).height(12.dp)) { drawCircle(part.color) }
                Text(part.label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(part.value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (hide || total <= 0L) "••%" else "${(part.value * 100 / total).coerceAtMost(100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.muted,
                    )
                }
            }
        }
    }
}

/** Receitas ou despesas do período, com a variação embaixo. */
@Composable
private fun SumBox(label: String, icon: Ico, value: Long, color: androidx.compose.ui.graphics.Color, change: String, modifier: Modifier) {
    val p = Fin.c
    Glass(modifier, radius = 22.dp, padding = 13.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, p.muted, size = 14.dp)
            Spacer(Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        MoneyText(value, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
        if (change.isNotEmpty()) Text(change, style = MaterialTheme.typography.labelSmall, color = p.muted)
    }
}

@Composable
private fun MiniBox(label: String, value: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = Fin.c
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(p.accent2).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted)
        MoneyText(value, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
    }
}
