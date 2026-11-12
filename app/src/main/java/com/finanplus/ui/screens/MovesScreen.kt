// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import java.time.LocalDate

@Composable
fun PageTitle(eyebrow: String, title: String, subtitle: String) {
    Eyebrow(eyebrow)
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fin.c.muted, modifier = Modifier.padding(bottom = 14.dp))
}

@Composable
fun MovesScreen(s: AppState, listState: LazyListState = rememberLazyListState()) {
    val nav = LocalNav.current
    val f = nav.filters
    val p = Fin.c
    val hide = LocalPrivacy.current
    // busca sem diferenciar acento: "cafe" encontra "Café"
    val q = com.finanplus.core.assist.Text.fold(f.query)
    // texto de busca já normalizado, calculado uma vez por versão dos dados (não a cada tecla, para cada lançamento)
    val folded = androidx.compose.runtime.remember(s.txs) { s.txs.associate { it.id to com.finanplus.core.assist.Text.fold(it.desc + " " + it.category) } }
    val today0 = com.finanplus.ui.components.LocalToday.current
    val list = androidx.compose.runtime.remember(s, f.from, f.to, f.paid, f.kind, q, today0) {
        // recorrências previstas dos meses que ainda não chegaram (só com fim de período definido; não são gravadas)
        val projected = f.to?.let { to -> com.finanplus.core.Projection.between(s, f.from ?: today0, to, today0) } ?: emptyList()
        (s.txs + projected).filter {
            f.inRange(it) && (f.paid == null || it.paid == f.paid) && (f.kind == null || (it.kind == f.kind && !it.isTransfer)) &&
                (q.isEmpty() || (folded[it.id] ?: com.finanplus.core.assist.Text.fold(it.desc + " " + it.category)).contains(q))
        }.sortedWith(compareByDescending<com.finanplus.core.Tx> { it.date }.thenByDescending { it.id })
    }
    val flow = androidx.compose.runtime.remember(list) { Finance.flow(list) }
    val pending = androidx.compose.runtime.remember(list) { com.finanplus.core.Period.pending(list) }
    // opção "Acumular saldo entre os meses" (Ajustes › Cálculo do saldo; desligada por padrão)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val accumulate = com.finanplus.data.DevicePrefs.get(ctx).flow.collectAsStateWithLifecycle().value.accumulateBalance
    // com filtro de tipo, situação ou busca o saldo acumulado não faria sentido: fica a visão do período
    val filtered = f.kind != null || f.paid != null || q.isNotEmpty()
    val accumulated = androidx.compose.runtime.remember(s, f.to, accumulate, filtered, today0) {
        if (!accumulate || filtered) null
        else {
            // sem fim de período: até o último lançamento (ou hoje); sem recorrências previstas, como a lista
            val until = f.to ?: maxOf(today0, s.txs.maxOfOrNull { it.date } ?: today0)
            Accumulated(Finance.balanceAt(s, until), Finance.forecastAt(s, until, today0, withProjection = f.to != null))
        }
    }
    // lista agrupada por dia (a ordem do mapa segue a lista: mais recentes primeiro)
    val groups = androidx.compose.runtime.remember(list) { list.groupBy { it.date } }
    val calendar = nav.movesView == com.finanplus.ui.MovesView.CALENDAR
    // busca recolhível: o texto fica nos filtros (vale ao trocar de mês); aberta, "Voltar" só recolhe o campo
    var searchOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = searchOpen && !calendar) { searchOpen = false }
    val today = com.finanplus.ui.components.LocalToday.current
    // calendário: dias do mês mostrado (calculado só nessa visão)
    val days = if (calendar) androidx.compose.runtime.remember(s, nav.calMonth, today) { com.finanplus.core.MonthCalendar.build(s, nav.calMonth, today) } else emptyMap()

    LazyColumn(state = listState, contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item(key = "moves-heading") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Lançamentos",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // busca só na lista (o calendário mostra o mês inteiro)
                if (!calendar) SearchToggle(searchOpen, active = f.query.isNotEmpty()) { searchOpen = !searchOpen }
                MovesViewSwitch(nav)
            }
        }
        if (!calendar) item(key = "moves-search") {
            androidx.compose.animation.AnimatedVisibility(
                visible = searchOpen,
                enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)),
                exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(140)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
            ) {
                SearchField(f.query, { f.query = it }) { f.query = ""; searchOpen = false }
            }
        }
        if (calendar) {
            calendarItems(s, nav, days, today, hide)
            return@LazyColumn
        }
        // período: ‹ mês › e, ao lado, período livre e situação
        item { PeriodBar(nav, today) }
        item { FilterChips(nav) }
        // mês inteiro sem filtro: os números do glossário único (os mesmos do Início e do calendário)
        val monthOnly = com.finanplus.core.Period.fullMonth(f.from, f.to)
        if (monthOnly != null && !filtered) item {
            val totals = androidx.compose.runtime.remember(s, monthOnly, today0) { com.finanplus.core.Totals.month(s, monthOnly, today0) }
            MonthSummaryCard(totals, accumulate)
        } else item { PeriodSummary(flow, pending, hide, accumulated, accumulate && filtered) }
        if (list.isEmpty() && q.isNotEmpty()) item {
            Glass(Modifier.fillMaxWidth().padding(top = 14.dp), radius = 24.dp) {
                Text(androidx.compose.ui.res.stringResource(com.finanplus.R.string.moves_search_empty), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(androidx.compose.ui.res.stringResource(com.finanplus.R.string.moves_search_empty_hint), color = p.muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else if (list.isEmpty()) item {
            Glass(Modifier.fillMaxWidth().padding(top = 14.dp), radius = 24.dp) {
                Text("Nenhum lançamento neste período", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text("Troque o mês ou ajuste os filtros. Você também pode começar por aqui:", color = p.muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Despesa", Modifier.weight(1f), icon = com.finanplus.ui.components.Ico.DOWN) {
                        nav.open(Sheet.TxEdit(Kind.EXPENSE))
                    }
                    Pill("Receita", Modifier.weight(1f), icon = com.finanplus.ui.components.Ico.UP) {
                        nav.open(Sheet.TxEdit(Kind.INCOME))
                    }
                }
            }
        }
        groups.forEach { (date, txs) ->
            item(key = "dia-$date") { DayGroupHeader(date, com.finanplus.core.Period.dayTotal(txs), today, hide) }
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
private fun SearchToggle(open: Boolean, active: Boolean, onClick: () -> Unit) {
    val p = Fin.c
    // destacado com a busca aberta ou com texto buscado (a lista está filtrada mesmo com o campo recolhido)
    val on = open || active
    val label = androidx.compose.ui.res.stringResource(com.finanplus.R.string.moves_search_open)
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (on) p.accent else p.accent2)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; stateDescription = if (open) "aberta" else if (active) "filtrando" else "fechada" },
        contentAlignment = Alignment.Center,
    ) { com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.SEARCH, if (on) p.onAccent else p.accent, size = 21.dp) }
}

/** Campo da busca: aparece abaixo do cabeçalho com foco e teclado; "X" limpa e recolhe. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val p = Fin.c
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    androidx.compose.material3.OutlinedTextField(
        value, { onChange(it.take(60)) }, Modifier.fillMaxWidth().padding(bottom = 6.dp).focusRequester(focus),
        placeholder = { Text(androidx.compose.ui.res.stringResource(com.finanplus.R.string.moves_search_placeholder)) }, singleLine = true,
        leadingIcon = { com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.SEARCH, p.muted, size = 20.dp) },
        trailingIcon = {
            val clear = androidx.compose.ui.res.stringResource(com.finanplus.R.string.moves_search_clear)
            androidx.compose.foundation.layout.Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { keyboard?.hide(); onClose() }
                    .semantics { contentDescription = clear },
                contentAlignment = Alignment.Center,
            ) { com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.CLOSE, p.muted, size = 20.dp) }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
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
@Composable
private fun FilterChips(nav: com.finanplus.ui.Nav) {
    val f = nav.filters
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Pill("Todos", selected = f.kind == null && f.paid == null) { f.kind = null; f.paid = null }
        Pill("Receitas", selected = f.kind == Kind.INCOME) { f.kind = if (f.kind == Kind.INCOME) null else Kind.INCOME }
        Pill("Despesas", selected = f.kind == Kind.EXPENSE) { f.kind = if (f.kind == Kind.EXPENSE) null else Kind.EXPENSE }
        Pill("Pendentes", selected = f.paid == false) { f.paid = if (f.paid == false) null else false }
    }
}

/** Saldo das contas ao fim do período somando os meses anteriores: realizado e previsto. */
private data class Accumulated(val balance: Long, val forecast: Long)

/**
 * Receitas, despesas e saldo do período (realizados) e, embaixo, o que está pendente.
 * Com [acc] (opção "Acumular saldo entre os meses"), a coluna Saldo mostra o saldo das contas ao fim do
 * período, com o saldo inicial e os meses anteriores; receitas e despesas continuam sendo só as do período.
 */
@Composable
private fun PeriodSummary(flow: com.finanplus.core.Flow, pending: com.finanplus.core.Pending, hide: Boolean, acc: Accumulated? = null, accOffByFilter: Boolean = false) {
    val p = Fin.c
    val balance = acc?.balance ?: flow.balance
    val forecast = acc?.forecast ?: (flow.balance + pending.toReceive - pending.toPay)
    Glass(Modifier.padding(top = 10.dp), radius = 24.dp, padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCol("Receitas", flow.income, p.green, "a receber", pending.toReceive, p.green, Modifier.weight(1f))
            SummaryCol("Despesas", flow.expense, p.red, "a pagar", pending.toPay, p.red, Modifier.weight(1f))
            SummaryCol("Saldo", balance, if (balance < 0) p.red else p.text, "previsto", forecast, if (forecast < 0) p.red else p.accent, Modifier.weight(1f),
                showSub = if (acc != null) forecast != balance else pending.toReceive > 0 || pending.toPay > 0)
        }
        if ((flow.income > 0 || flow.expense > 0) && !hide) Text(
            when {
                flow.income == flow.expense -> "Neste período, você gastou o mesmo valor que recebeu."
                flow.expense > flow.income -> "Você gastou ${Money.format(flow.expense - flow.income)} a mais do que recebeu."
                else -> "Depois das despesas, sobraram ${Money.format(flow.income - flow.expense)}."
            },
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 10.dp),
        )
        if (acc != null || accOffByFilter) Text(
            if (acc != null) "Saldo acumulado: inclui o saldo das contas que vem dos meses anteriores."
            else "Com filtro, o saldo mostra só este período (sem acumular).",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp),
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
private fun DayGroupHeader(date: LocalDate, total: com.finanplus.core.DayTotal, today: LocalDate, hide: Boolean) {
    val p = Fin.c
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (date == today) "Hoje · " else "") + com.finanplus.core.MonthCalendar.dayTitle(date, today),
            Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        )
        if (!hide) androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
            // total do dia: só receitas e despesas (verde entrou, vermelho saiu); reservas para meta na linha de baixo
            val net = total.net
            if (total.hasFlow) Text(
                (if (net > 0) "+ " else if (net < 0) "− " else "") + Money.format(kotlin.math.abs(net)),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (net < 0) p.red else p.green,
            )
            if (total.goals != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                // guardado: cor primária (continua seu, separado na meta); retirado: verde (voltou para a conta)
                val color = if (total.goals > 0) p.goal else p.green
                com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.GOAL, color, size = 14.dp)
                Text(
                    androidx.compose.ui.res.stringResource(
                        if (total.goals > 0) com.finanplus.R.string.day_goals_saved else com.finanplus.R.string.day_goals_withdrawn,
                        Money.format(kotlin.math.abs(total.goals)),
                    ),
                    Modifier.padding(start = 4.dp),
                    style = if (total.hasFlow) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = color,
                )
            }
        }
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
