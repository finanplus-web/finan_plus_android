// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.core.AppState
import com.finanplus.core.CalDay
import com.finanplus.core.DayMark
import com.finanplus.core.Finance
import com.finanplus.core.InvoiceDue
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.MonthCalendar
import com.finanplus.core.Ops
import com.finanplus.core.ym
import com.finanplus.data.Repo
import com.finanplus.ui.MovesView
import com.finanplus.ui.Nav
import com.finanplus.ui.Sheet
import com.finanplus.ui.TxRow
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.theme.Fin
import java.time.LocalDate

/*
 * Calendário da aba Lançamentos: o mês em grade, com o saldo de cada dia, e os lançamentos do dia escolhido.
 * As contas ficam em core/MonthCalendar.kt (testadas em JVM); aqui só o desenho.
 */

/** Cor das marcas de cartão (compras, faturas e pagamentos de fatura). */
@Composable
private fun cardColor(): Color = if (Fin.c.dark) Color(0xFFC3A6FF) else Color(0xFF7446D0)

@Composable
private fun markColor(m: DayMark): Color = when (m) {
    DayMark.INCOME -> Fin.c.green
    DayMark.EXPENSE -> Fin.c.red
    DayMark.CARD -> cardColor()
}

/** Chave "Lista | Calendário" no topo da aba Lançamentos. */
@Composable
fun MovesViewSwitch(nav: Nav) {
    val p = Fin.c
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(p.surface).border(1.dp, p.border, shape).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((view, label, icon) in listOf(Triple(MovesView.LIST, "Lista", Ico.LIST), Triple(MovesView.CALENDAR, "Calendário", Ico.CALENDAR))) {
            val on = nav.movesView == view
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(20.dp)).background(if (on) p.accent else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { nav.movesView = view },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(icon, if (on) p.onAccent else p.muted, size = 19.dp)
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (on) p.onAccent else p.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

/** Itens da visão Calendário, dentro da LazyColumn da aba Lançamentos. */
fun LazyListScope.calendarItems(s: AppState, nav: Nav, days: Map<LocalDate, CalDay>, today: LocalDate, hide: Boolean) {
    item(key = "cal-grid") { CalendarCard(nav, days, today, hide) }
    item(key = "cal-totals") { MonthTotals(days) }
    val sel = nav.calDay?.takeIf { it.ym() == nav.calMonth }
    if (sel == null) {
        item(key = "cal-hint") {
            Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 16.dp) {
                Text("Toque num dia para ver os lançamentos dele.", color = Fin.c.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        return
    }
    val day = days[sel]
    item(key = "cal-day-head") { DayHeader(s, nav, sel, day, today, hide) }
    if (day == null || day.count == 0) item(key = "cal-day-empty") {
        Glass(Modifier.fillMaxWidth(), radius = 22.dp, padding = 16.dp) {
            Text("Nada neste dia", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Use Receita ou Despesa para lançar algo com esta data.", color = Fin.c.muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp))
        }
    } else {
        items(day.txs, key = { "cal-tx-" + it.id }) { t ->
            TxRow(t, s, onToggle = { Repo.update { Ops.togglePaid(it, t.id) } }, onOpen = { nav.open(Sheet.TxEdit(t.kind, t.id)) })
        }
        items(day.invoices, key = { "cal-inv-" + it.cardId + it.due }) { inv -> InvoiceRow(inv) { nav.open(Sheet.PayInvoice(inv.cardId)) } }
    }
}

// ------------------------------------------------------------------ grade do mês
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarCard(nav: Nav, days: Map<LocalDate, CalDay>, today: LocalDate, hide: Boolean) {
    val p = Fin.c
    val ym = nav.calMonth
    // muda de mês: no mês atual, o dia escolhido volta a ser hoje; nos outros, nenhum
    val go: (Long) -> Unit = { delta ->
        val n = nav.calMonth.plusMonths(delta)
        nav.calMonth = n
        nav.calDay = if (n == today.ym()) today else null
    }
    val cells = remember(ym) { MonthCalendar.cells(ym).chunked(7) }
    Glass(
        // deslizar para o lado também troca de mês (as setas continuam sendo o caminho acessível)
        Modifier.pointerInput(today) {
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = { if (dx > 120f) go(-1) else if (dx < -120f) go(1) },
            ) { change, amount -> change.consume(); dx += amount } // consome: o gesto não troca de aba
        },
        radius = 26.dp, padding = 10.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Ico.PREV, "Mês anterior") { go(-1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    MonthCalendar.monthTitle(ym), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
                if (ym != today.ym()) Text(
                    "Voltar para hoje", color = p.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                        nav.calMonth = today.ym(); nav.calDay = today
                    }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            RoundIconButton(Ico.NEXT, "Próximo mês") { go(1) }
        }
        // cabeçalho dos dias da semana (decorativo: cada dia já é lido com o dia da semana)
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp).clearAndSetSemantics { }) {
            MonthCalendar.WEEK_HEADER.forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (week in cells) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (d in week) {
                        Box(Modifier.weight(1f)) {
                            if (d != null) DayCell(
                                d, days[d], today, selected = d == nav.calDay, hide = hide,
                                // 1º toque: mostra o dia; tocar de novo no dia escolhido: novo lançamento nessa data
                                onClick = { if (nav.calDay == d) nav.open(Sheet.TxEdit(Kind.EXPENSE, date = d)) else nav.calDay = d },
                                // tocar e segurar: novo lançamento direto, em qualquer dia
                                onLongClick = { nav.calDay = d; nav.open(Sheet.TxEdit(Kind.EXPENSE, date = d)) },
                            )
                        }
                    }
                }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            LegendDot(markColor(DayMark.INCOME), "Receita")
            LegendDot(markColor(DayMark.EXPENSE), "Despesa")
            LegendDot(markColor(DayMark.CARD), "Cartão")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Ico.WARNING, p.red, size = 12.dp)
                Spacer(Modifier.width(4.dp))
                Text("Em atraso", style = MaterialTheme.typography.labelSmall, color = p.muted)
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: Ico, description: String, onClick: () -> Unit) {
    val p = Fin.c
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.accent2), contentAlignment = Alignment.Center) { AppIcon(icon, p.accent, size = 22.dp) }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Fin.c.muted)
    }
}

/**
 * Um dia da grade: número, saldo do dia (sem "R$") e os pontinhos de receita/despesa/cartão.
 * Hoje tem contorno; o dia escolhido fica preenchido. Com "Ocultar valores", só os pontinhos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(d: LocalDate, day: CalDay?, today: LocalDate, selected: Boolean, hide: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = Fin.c
    val shape = RoundedCornerShape(14.dp)
    val isToday = d == today
    val numberColor = when {
        selected -> p.onAccent
        isToday -> p.accent
        d.isBefore(today) -> p.muted
        else -> p.text
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(shape)
            .background(if (selected) p.accent else Color.Transparent)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, p.accent, shape) else Modifier)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = if (selected) "novo lançamento neste dia" else "ver lançamentos do dia",
                onLongClickLabel = "novo lançamento neste dia",
                onLongClick = onLongClick, onClick = onClick,
            )
            .semantics { contentDescription = MonthCalendar.describe(d, day, today, hide); this.selected = selected },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 5.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                d.dayOfMonth.toString(), color = numberColor, fontSize = 14.sp, maxLines = 1,
                fontWeight = if (isToday || selected) FontWeight.ExtraBold else if (d.isBefore(today)) FontWeight.Normal else FontWeight.SemiBold,
            )
            if (day != null && !hide && (day.income != 0L || day.expense != 0L)) {
                val color = when {
                    selected -> p.onAccent
                    day.net >= 0 -> p.green
                    else -> p.red
                }
                ShrinkText(MonthCalendar.signed(day.net), color)
            }
            if (day != null && day.marks.isNotEmpty()) Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                day.marks.forEach { m ->
                    // no dia escolhido (fundo azul) o pontinho ganha um aro branco por fora, sem perder a cor
                    if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(p.onAccent), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(markColor(m)))
                    } else Box(Modifier.size(6.dp).clip(CircleShape).background(markColor(m)))
                }
            }
        }
        if (day?.overdue == true) AppIcon(Ico.WARNING, if (selected) p.onAccent else p.red, Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp), size = 11.dp)
    }
}

/**
 * Texto pequeno do valor do dia. O quadradinho é estreito (≈ 40dp num celular de 360dp):
 * a fonte acompanha o tamanho de fonte do sistema até 1,15× e, se ainda não couber, diminui até caber.
 */
@Composable
private fun ShrinkText(text: String, color: Color) {
    val density = LocalDensity.current
    val base = with(density) { (9.5f * minOf(density.fontScale, 1.15f)).dp.toSp() }
    var size by remember(text, base) { mutableStateOf(base) }
    var fits by remember(text, base) { mutableStateOf(false) }
    Text(
        text, Modifier.padding(top = 1.dp, start = 1.dp, end = 1.dp).drawWithContent { if (fits) drawContent() },
        color = color, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        onTextLayout = { r ->
            if (r.didOverflowWidth && size.value > 6.5f) size = (size.value * 0.9f).sp else fits = true
        },
    )
}

// ------------------------------------------------------------------ totais do mês
@Composable
private fun MonthTotals(days: Map<LocalDate, CalDay>) {
    val p = Fin.c
    val t = remember(days) { MonthCalendar.totals(days) }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TotalTile("Entradas", t.income, p.green, Modifier.weight(1f))
        TotalTile("Saídas", t.expense, p.red, Modifier.weight(1f))
        TotalTile("Resultado", t.net, if (t.net < 0) p.red else p.accent, Modifier.weight(1f))
    }
    Text(
        "Inclui o que ainda está pendente e as faturas no dia do vencimento. Compras no cartão aparecem no dia, mas só contam na fatura.",
        style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
    )
}

@Composable
private fun TotalTile(label: String, value: Long, color: Color, modifier: Modifier) {
    Glass(modifier, radius = 20.dp, padding = 12.dp) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Fin.c.muted, maxLines = 1)
        MoneyText(value, color = color, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
    }
}

// ------------------------------------------------------------------ dia escolhido
@Composable
private fun DayHeader(s: AppState, nav: Nav, d: LocalDate, day: CalDay?, today: LocalDate, hide: Boolean) {
    val p = Fin.c
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow(if (d == today) "Hoje" else if (d.isBefore(today)) "Dia escolhido" else "Previsto")
            Text(MonthCalendar.dayTitle(d, today), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            val n = day?.count ?: 0
            val countText = when (n) { 0 -> "Sem lançamentos"; 1 -> "1 lançamento"; else -> "$n lançamentos" }
            val net = day?.net ?: 0L
            val netText = if (hide || day == null || (day.income == 0L && day.expense == 0L)) "" else
                " · saldo do dia " + (if (net > 0) "+ " else if (net < 0) "− " else "") + Money.format(kotlin.math.abs(net))
            Text(countText + netText, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
    }
    // lançar direto nesta data, já como receita ou despesa
    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Receita", Modifier.weight(1f), icon = Ico.ADD) { nav.open(Sheet.TxEdit(Kind.INCOME, date = d)) }
        Pill("Despesa", Modifier.weight(1f), icon = Ico.REMOVE) { nav.open(Sheet.TxEdit(Kind.EXPENSE, date = d)) }
    }
    // hoje ou depois: quanto deve sobrar nas contas ao fim do dia (mesma conta do "Saldo previsto" do Início)
    if (!d.isBefore(today)) {
        val forecast = remember(s, d, today) { Finance.futureBalance(s, d, today) }
        Glass(Modifier.padding(bottom = 6.dp), radius = 20.dp, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Saldo previsto ao fim do dia", Modifier.weight(1f), color = p.muted, style = MaterialTheme.typography.bodyMedium)
                MoneyText(forecast, color = if (forecast < 0) p.red else p.text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}

/** Fatura em aberto que vence no dia. Toque abre "Pagar fatura". */
@Composable
private fun InvoiceRow(inv: InvoiceDue, onOpen: () -> Unit) {
    val p = Fin.c
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(p.surface).border(1.dp, p.border, shape)
            .clickable(role = Role.Button, onClick = onOpen).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.accent2), contentAlignment = Alignment.Center) {
            AppIcon(Ico.CARD, cardColor(), size = 20.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Fatura ${inv.cardName}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (inv.overdue) "Vencida · em aberto" else "Vence neste dia · toque para pagar",
                style = MaterialTheme.typography.bodySmall, color = if (inv.overdue) p.red else p.muted, maxLines = 1,
            )
        }
        MoneyText(inv.amount, prefix = "− ", color = p.red, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold))
    }
}
