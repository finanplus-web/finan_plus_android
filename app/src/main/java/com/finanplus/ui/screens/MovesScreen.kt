// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
    // texto de busca já normalizado, calculado uma vez por versão dos dados (não a cada tecla, para cada lançamento)
    val folded = androidx.compose.runtime.remember(s.txs) { s.txs.associate { it.id to com.finanplus.core.assist.Text.fold(it.desc + " " + it.category) } }
    val list = androidx.compose.runtime.remember(s.txs, f.from, f.to, f.paid, f.kind, q) {
        s.txs.filter {
            f.inRange(it) && (f.paid == null || it.paid == f.paid) && (f.kind == null || it.kind == f.kind) &&
                (q.isEmpty() || folded[it.id]?.contains(q) == true)
        }.sortedWith(compareByDescending<com.finanplus.core.Tx> { it.date }.thenByDescending { it.id })
    }
    val flow = androidx.compose.runtime.remember(list) { Finance.flow(list) }
    val pending = androidx.compose.runtime.remember(list) { com.finanplus.core.Period.pending(list) }
    // lista agrupada por dia (a ordem do mapa segue a lista: mais recentes primeiro)
    val groups = androidx.compose.runtime.remember(list) { list.groupBy { it.date } }
    val calendar = nav.movesView == com.finanplus.ui.MovesView.CALENDAR
    val today = com.finanplus.ui.components.LocalToday.current
    // calendário: dias do mês mostrado (calculado só nessa visão)
    val days = if (calendar) androidx.compose.runtime.remember(s, nav.calMonth, today) { com.finanplus.core.MonthCalendar.build(s, nav.calMonth, today) } else emptyMap()

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item { Text("Lançamentos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp).semantics { heading() }) }
        item { MovesViewSwitch(nav) }
        if (calendar) {
            calendarItems(s, nav, days, today, hide)
            return@LazyColumn
        }
        // período: ‹ mês › e, ao lado, período livre e situação
        item { PeriodBar(nav, today) }
        item { SearchBar(f.query) { f.query = it } }
        item { FilterChips(nav) }
        item { PeriodSummary(flow, pending, hide) }
        if (list.isEmpty()) item {
            Glass(Modifier.fillMaxWidth().padding(top = 14.dp), radius = 24.dp) {
                Text("Nenhum lançamento neste período", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Troque o mês, ajuste os filtros ou adicione uma movimentação no +.", color = p.muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
            }
        }
        groups.forEach { (date, txs) ->
            item(key = "dia-$date") { DayGroupHeader(date, com.finanplus.core.Period.cashNet(txs), txs.any { !it.isCard }, today, hide) }
            items(txs, key = { it.id }) { t ->
                TxRow(t, s, onToggle = { Repo.update { Ops.togglePaid(it, t.id) } }, onOpen = { nav.open(Sheet.TxEdit(t.kind, t.id)) })
            }
        }
    }
}

/** ‹ Outubro de 2026 › + botão de período livre e situação. As setas andam um mês inteiro. Usado em Lançamentos e Relatórios. */
@Composable
internal fun PeriodBar(nav: com.finanplus.ui.Nav, today: LocalDate) {
    val f = nav.filters
    val p = Fin.c
    val shift: (Long) -> Unit = { d -> val (a, b) = com.finanplus.core.Period.shift(f.from, f.to, d, today); f.from = a; f.to = b }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(com.finanplus.ui.components.Ico.PREV, p.accent2, p.accent, "Mês anterior") { shift(-1) }
        Text(
            com.finanplus.core.Period.label(f.from, f.to), Modifier.weight(1f).semantics { heading(); liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
        )
        RoundButton(com.finanplus.ui.components.Ico.NEXT, p.accent2, p.accent, "Próximo mês") { shift(1) }
        // destacado quando há período livre ou filtro de situação "Realizados" (o que os botões rápidos não mostram)
        val custom = com.finanplus.core.Period.fullMonth(f.from, f.to) == null || f.paid == true
        RoundButton(com.finanplus.ui.components.Ico.TUNE, if (custom) p.accent else p.accent2, if (custom) p.onAccent else p.accent, "Período e filtros") {
            nav.open(Sheet.MovesFilters)
        }
    }
}

@Composable
private fun RoundButton(icon: com.finanplus.ui.components.Ico, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, description: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            com.finanplus.ui.components.AppIcon(icon, fg, size = 21.dp)
        }
    }
}

/** Busca compacta, com lupa. */
@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit) {
    val p = Fin.c
    androidx.compose.material3.OutlinedTextField(
        value, { onChange(it.take(60)) }, Modifier.fillMaxWidth().padding(top = 4.dp),
        placeholder = { Text("Buscar descrição ou categoria") }, singleLine = true,
        leadingIcon = { com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.SEARCH, p.muted, size = 20.dp) },
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent, unfocusedBorderColor = p.border,
            focusedContainerColor = p.surface, unfocusedContainerColor = p.surface,
        ),
    )
}

/**
 * Filtros de um toque: Todos, Receitas, Despesas, Pendentes.
 * Receitas/Despesas e Pendentes combinam (ex.: despesas pendentes). "Realizados" fica no botão de ajuste.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(nav: com.finanplus.ui.Nav) {
    val f = nav.filters
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Pill("Todos", selected = f.kind == null && f.paid == null) { f.kind = null; f.paid = null }
        Pill("Receitas", selected = f.kind == Kind.INCOME) { f.kind = if (f.kind == Kind.INCOME) null else Kind.INCOME }
        Pill("Despesas", selected = f.kind == Kind.EXPENSE) { f.kind = if (f.kind == Kind.EXPENSE) null else Kind.EXPENSE }
        Pill("Pendentes", selected = f.paid == false) { f.paid = if (f.paid == false) null else false }
    }
}

/** Receitas, despesas e saldo do período (realizados) e, embaixo, o que está pendente. */
@Composable
private fun PeriodSummary(flow: com.finanplus.core.Flow, pending: com.finanplus.core.Pending, hide: Boolean) {
    val p = Fin.c
    val forecast = flow.balance + pending.toReceive - pending.toPay
    Glass(Modifier.padding(top = 10.dp), radius = 24.dp, padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCol("Receitas", flow.income, p.green, "a receber", pending.toReceive, p.green, Modifier.weight(1f))
            SummaryCol("Despesas", flow.expense, p.red, "a pagar", pending.toPay, p.red, Modifier.weight(1f))
            SummaryCol("Saldo", flow.balance, if (flow.balance < 0) p.red else p.text, "previsto", forecast, if (forecast < 0) p.red else p.accent, Modifier.weight(1f),
                showSub = pending.toReceive > 0 || pending.toPay > 0)
        }
        if (flow.income > 0 && !hide) Text(
            "As despesas são ${"%.1f".format(BR, flow.expense * 100.0 / flow.income)}% das receitas do período.",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun SummaryCol(
    label: String, value: Long, color: androidx.compose.ui.graphics.Color, subLabel: String, sub: Long, subColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier, showSub: Boolean = sub > 0,
) {
    val p = Fin.c
    androidx.compose.foundation.layout.Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1)
        MoneyText(value, color = color, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
        if (showSub) {
            Text(subLabel, style = MaterialTheme.typography.labelSmall, color = p.muted, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            MoneyText(sub, color = subColor, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** Cabeçalho de cada dia na lista: "Quinta, 15 de outubro" e o saldo do dia (mesma regra do calendário). */
@Composable
private fun DayGroupHeader(date: LocalDate, net: Long, hasCash: Boolean, today: LocalDate, hide: Boolean) {
    val p = Fin.c
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (date == today) "Hoje · " else "") + com.finanplus.core.MonthCalendar.dayTitle(date, today),
            Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        )
        if (hasCash && !hide) Text(
            (if (net > 0) "+ " else if (net < 0) "− " else "") + Money.format(kotlin.math.abs(net)),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (net < 0) p.red else p.green,
        )
    }
}

/** Folha "Período e filtros": datas livres, atalhos e situação (inclui "Realizados"). As mudanças valem na hora. */
@Composable
fun MovesFiltersSheet(s: AppState, close: () -> Unit) {
    val f = LocalNav.current.filters
    val p = Fin.c
    Text("Período e filtros", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("O período também vale para Relatórios.", style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(bottom = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DateField("De", f.from, { f.from = it }, Modifier.weight(1f), allowClear = true)
        DateField("Até", f.to, { f.to = it }, Modifier.weight(1f), allowClear = true)
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Pill("Este mês", Modifier.weight(1f)) { f.thisMonth() }
        Pill("30 dias", Modifier.weight(1f)) { val t = LocalDate.now(); f.to = t; f.from = t.minusDays(29) }
        Pill("Tudo", Modifier.weight(1f)) { val ds = s.txs.map { it.date }; f.from = ds.minOrNull(); f.to = ds.maxOrNull() }
    }
    SelectField(
        "Situação", listOf<Pair<Boolean?, String>>(null to "Todos", true to "Realizados", false to "Pendentes"), f.paid, { f.paid = it },
        Modifier.padding(top = 10.dp),
    )
    Spacer(Modifier.height(12.dp))
    com.finanplus.ui.components.PrimaryButton("Pronto", onClick = close)
}

@Composable
fun Column2(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier) {
        Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
    }
}
