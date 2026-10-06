// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Ops
import com.finanplus.core.totalOf
import com.finanplus.data.Repo
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.TxRow
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.BR
import com.finanplus.ui.components.DateField
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.SectionHead
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin
import java.time.LocalDate

@Composable
fun PageTitle(eyebrow: String, title: String, subtitle: String) {
    Eyebrow(eyebrow)
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fin.c.muted, modifier = Modifier.padding(bottom = 14.dp))
}

@Composable
fun MovesScreen(s: AppState) {
    val nav = LocalNav.current
    val f = nav.filters
    val p = Fin.c
    val hide = LocalPrivacy.current
    // busca sem diferenciar acento: "cafe" encontra "Café"
    val q = com.finanplus.core.assist.Text.fold(f.query)
    val list = s.txs.filter {
        f.inRange(it) && (f.paid == null || it.paid == f.paid) && (f.kind == null || it.kind == f.kind) &&
            (q.isEmpty() || com.finanplus.core.assist.Text.fold(it.desc + " " + it.category).contains(q))
    }.sortedWith(compareByDescending<com.finanplus.core.Tx> { it.date }.thenByDescending { it.id })
    val flow = Finance.flow(list)
    val total = flow.income + flow.expense
    val pending = list.filter { it.isFlow && !it.paid }

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item { PageTitle("Movimentações", "Lançamentos", "Compare suas receitas e despesas em qualquer período.") }
        item {
            Glass(radius = 26.dp, padding = 15.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateField("De", f.from, { f.from = it }, Modifier.weight(1f), allowClear = true)
                    DateField("Até", f.to, { f.to = it }, Modifier.weight(1f), allowClear = true)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Pill("Este mês", Modifier.weight(1f)) { f.thisMonth() }
                    Pill("30 dias", Modifier.weight(1f)) { val t = LocalDate.now(); f.to = t; f.from = t.minusDays(29) }
                    Pill("Tudo", Modifier.weight(1f)) { val ds = s.txs.map { it.date }; f.from = ds.minOrNull(); f.to = ds.maxOrNull() }
                }
            }
        }
        item {
            Glass(Modifier.padding(top = 10.dp), radius = 24.dp, padding = 14.dp) {
                Eyebrow("Filtros da lista")
                Field("Buscar lançamentos", f.query, { f.query = it }, placeholder = "Descrição ou categoria", maxLength = 60)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectField("Tipo", listOf<Pair<Kind?, String>>(null to "Todos", Kind.INCOME to "Receitas", Kind.EXPENSE to "Despesas"), f.kind, { f.kind = it }, Modifier.weight(1f))
                    SelectField("Situação", listOf<Pair<Boolean?, String>>(null to "Todos", true to "Realizados", false to "Pendentes"), f.paid, { f.paid = it }, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Glass(Modifier.weight(1f), radius = 22.dp, padding = 15.dp) {
                    Text("↑ Receitas", style = MaterialTheme.typography.bodySmall, color = p.muted)
                    MoneyText(flow.income, color = p.green, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                }
                Glass(Modifier.weight(1f), radius = 22.dp, padding = 15.dp) {
                    Text("↓ Despesas", style = MaterialTheme.typography.bodySmall, color = p.muted)
                    MoneyText(flow.expense, color = p.red, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
        }
        item {
            Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saldo do período", Modifier.weight(1f), color = p.muted)
                    MoneyText(flow.balance, color = if (flow.balance < 0) p.red else p.text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
        }
        item {
            Glass(Modifier.padding(top = 12.dp), radius = 25.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column2("Comparação", "Receitas × despesas", Modifier.weight(1f))
                    Text(if (flow.income > 0) "${flow.expense * 100 / flow.income}% gasto" else "—", color = p.muted, fontWeight = FontWeight.Bold)
                }
                CompareBar("Receitas", if (total > 0) flow.income.toFloat() / total else 0f, p.green, hide)
                CompareBar("Despesas", if (total > 0) flow.expense.toFloat() / total else 0f, p.red, hide)
                val summary = when {
                    flow.income > 0 -> "As despesas representam ${"%.1f".format(BR, flow.expense * 100.0 / flow.income)}% das receitas do período."
                    flow.expense > 0 -> "Há despesas, mas nenhuma receita neste período."
                    else -> "Nenhuma movimentação no período selecionado."
                }
                val pend = if (pending.isEmpty()) "" else if (hide) " Há valores pendentes." else
                    " Pendente: a receber ${Money.format(pending.totalOf(Kind.INCOME))} · a pagar ${Money.format(pending.totalOf(Kind.EXPENSE))}."
                Text(summary + pend, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { SectionHead("No período", "Todos os lançamentos") }
                Pill(list.size.toString(), selected = true) {}
            }
        }
        if (list.isEmpty()) item {
            Glass(Modifier.fillMaxWidth(), radius = 24.dp) {
                Text("Nenhum lançamento neste período", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Altere as datas ou adicione uma movimentação.", color = p.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        items(list, key = { it.id }) { t ->
            TxRow(t, s, onToggle = { Repo.update { Ops.togglePaid(it, t.id) } }, onOpen = { nav.open(Sheet.TxEdit(t.kind, t.id)) })
        }
    }
}

@Composable
fun Column2(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
    }
}

@Composable
private fun CompareBar(label: String, frac: Float, color: androidx.compose.ui.graphics.Color, hide: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 8.dp).widthIn(min = 70.dp), style = MaterialTheme.typography.bodySmall)
        Bar(frac, color, Modifier.weight(1f).sensitive(hide), height = 10.dp)
        Text("${(frac * 100).toInt()}%", Modifier.padding(start = 8.dp).widthIn(min = 38.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(2.dp))
}
