// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Money
import com.finanplus.core.ym
import com.finanplus.ui.LocalNav
import com.finanplus.ui.components.BR
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.br
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(s: AppState) {
    val nav = LocalNav.current
    val f = nav.filters
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val period = if (f.from == null && f.to == null) "Todo o histórico." else "Período: ${f.from?.br() ?: "início"} a ${f.to?.br() ?: "hoje"} (datas da aba Lançamentos)."

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item { PageTitle("Análise", "Relatórios", "$period Considera só valores realizados.") }
        // exportar o mesmo período em PDF (dá para trocar o período na própria folha)
        item {
            com.finanplus.ui.components.PrimaryButton("Exportar relatório em PDF", Modifier.padding(bottom = 12.dp)) {
                nav.open(com.finanplus.ui.Sheet.ReportPdf(f.from, f.to))
            }
        }
        item {
            Glass(radius = 25.dp) {
                Column2("Despesas", "Por categoria")
                val cats = Finance.categoryTotals(s, f.from, f.to)
                val total = cats.sumOf { it.second }
                if (cats.isEmpty()) Text("Sem despesas no período.", color = p.muted, style = MaterialTheme.typography.bodySmall)
                cats.forEach { (cat, v) ->
                    val lim = s.limits[cat]
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cat, Modifier.widthIn(min = 90.dp, max = 110.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Bar(if (total > 0) v.toFloat() / total else 0f, if (lim != null && v > lim) p.red else p.red.copy(alpha = 0.75f), Modifier.weight(1f).padding(horizontal = 8.dp).sensitive(hide), height = 10.dp)
                        MoneyText(v, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    if (lim != null) Text(
                        (if (v > lim) "⚠ Acima do" else "Dentro do") + " limite mensal de " + (if (hide) "R$ ••••" else Money.format(lim)),
                        style = MaterialTheme.typography.labelSmall, color = if (v > lim) p.red else p.muted,
                    )
                }
            }
        }
        item {
            val months = Finance.lastMonths(s, today)
            val max = maxOf(1L, months.maxOf { maxOf(it.second.income, it.second.expense) })
            val desc = months.joinToString("; ") { (ym, fl) ->
                ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", BR)) + if (hide) "" else ": receitas ${Money.format(fl.income)}, despesas ${Money.format(fl.expense)}"
            }
            Glass(Modifier.padding(top = 12.dp), radius = 25.dp) {
                Column2("Evolução", "Últimos 6 meses")
                // Gráfico com descrição completa para leitores de tela.
                Canvas(Modifier.fillMaxWidth().height(110.dp).sensitive(hide).semantics { contentDescription = "Gráfico de receitas e despesas. $desc" }) {
                    val slot = size.width / months.size
                    val bw = slot * 0.3f
                    months.forEachIndexed { i, (_, fl) ->
                        val x = i * slot + slot * 0.18f
                        val hi = size.height * fl.income / max
                        val he = size.height * fl.expense / max
                        drawRoundRect(p.green, Offset(x, size.height - hi), Size(bw, maxOf(hi, 2f)), CornerRadius(8f, 8f))
                        drawRoundRect(p.red, Offset(x + bw + 4f, size.height - he), Size(bw, maxOf(he, 2f)), CornerRadius(8f, 8f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    months.forEach { (ym, _) -> Text(ym.format(DateTimeFormatter.ofPattern("MMM", BR)), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = TextAlign.Center) }
                }
                Text("Verde: receitas · Vermelho: despesas", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            val cur = Finance.monthFlow(s, today.ym())
            val prev = Finance.monthFlow(s, today.ym().minusMonths(1))
            fun chg(a: Long, b: Long) = if (b == 0L) "Sem base" else { val c = (a - b) * 100.0 / b; (if (c >= 0) "↑ " else "↓ ") + "%.0f".format(BR, kotlin.math.abs(c)) + "% vs. mês anterior" }
            Glass(Modifier.padding(top = 12.dp), radius = 25.dp) {
                Column2("Comparação", "Este mês × mês anterior")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("Receitas", cur.income, chg(cur.income, prev.income)), Triple("Despesas", cur.expense, chg(cur.expense, prev.expense))).forEach { (l, v, c) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(p.accent2).padding(12.dp)) {
                            Text(l, style = MaterialTheme.typography.bodySmall, color = p.muted)
                            MoneyText(v, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                            Text(c, style = MaterialTheme.typography.labelSmall, color = p.muted)
                        }
                    }
                }
            }
        }
    }
}
