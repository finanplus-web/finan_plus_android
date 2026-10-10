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
 * Relatórios: o mesmo período da aba Lançamentos (‹ mês › e período livre), só com valores realizados.
 * Resumo com comparação justa, atalho para o simulador "E se…?", despesas por categoria e evolução.
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

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Relatórios", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
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
        item { WhatIfCard { nav.open(Sheet.Simulator()) } }
        item {
            Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
                Column2("Despesas", "Por categoria")
                val cats = Finance.categoryTotals(s, f.from, f.to)
                val total = cats.sumOf { it.second }
                if (cats.isEmpty()) Text("Sem despesas realizadas no período.", color = p.muted, style = MaterialTheme.typography.bodySmall)
                cats.forEach { (cat, v) ->
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
        item {
            val months = Finance.lastMonths(s, today)
            Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
                Column2("Evolução", "Últimos 6 meses")
                if (months.all { it.second.income == 0L && it.second.expense == 0L }) {
                    Text("Aparece quando houver pelo menos um mês com valores realizados.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                    return@Glass
                }
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

/** Atalho para o simulador, na cor de destaque do tema. */
@Composable
private fun WhatIfCard(onClick: () -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(22.dp)).background(p.accent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(Ico.ASSIST, p.onAccent, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("E se…?", color = p.onAccent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            Text("Simule economizar, comprar algo, uma mudança na renda ou antecipar uma dívida, sem mexer nos seus dados.", color = p.onAccent, style = MaterialTheme.typography.bodySmall)
        }
        AppIcon(Ico.NEXT, p.onAccent, size = 20.dp)
    }
}
