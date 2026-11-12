// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finanplus.core.MoneyView
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.clickable
import com.finanplus.ui.components.AppIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finanplus.R
import com.finanplus.core.Debts
import com.finanplus.core.Words
import com.finanplus.ui.components.BackLink
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Ops
import com.finanplus.core.Outcome
import com.finanplus.core.ym
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.BR
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.ManageItem
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.br
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin

/**
 * Meu dinheiro (REDESIGN-COERENCIA.md): resumo no topo (saldo nas contas, fatura aberta e guardado em metas),
 * alerta de meta fora do prazo e cards de navegação. Cada card abre a sua tela, com "‹ Meu dinheiro" para voltar.
 */
@Composable
fun AccountsScreen(s: AppState) {
    val nav = LocalNav.current
    val page = nav.moneyPage
    androidx.activity.compose.BackHandler(enabled = page != null) { nav.moneyPage = null }
    val list = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(page) { list.scrollToItem(0) }
    LazyColumn(state = list, contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        when (page) {
            PAGE_ACCOUNTS -> accountsPage(s)
            PAGE_DEBTS -> debtsPage(s)
            PAGE_LIMITS -> item { PageHead(stringResource(R.string.money_limits), stringResource(R.string.limits_sheet_subtitle)); LimitsListContent(s) }
            PAGE_GOALS -> item { PageHead(stringResource(R.string.money_goals), stringResource(R.string.goals_sheet_subtitle)); GoalsListContent(s) }
            PAGE_CATEGORIES -> item { PageHead(stringResource(R.string.cats_title), stringResource(R.string.cats_subtitle)); CategoriesPage(s) }
            else -> overview(s)
        }
    }
}

internal const val PAGE_ACCOUNTS = "contas"
internal const val PAGE_DEBTS = "dividas"
internal const val PAGE_LIMITS = "limites"
internal const val PAGE_GOALS = "metas"
internal const val PAGE_CATEGORIES = "categorias"

/** Cabeçalho das telas de Meu dinheiro: "‹ Meu dinheiro", título e linha de apoio. */
@Composable
private fun PageHead(title: String, subtitle: String) {
    val nav = LocalNav.current
    BackLink(stringResource(R.string.money_title)) { nav.moneyPage = null }
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fin.c.muted, modifier = Modifier.padding(bottom = 6.dp))
}

private fun androidx.compose.foundation.lazy.LazyListScope.overview(s: AppState) {
    item {
        Text(stringResource(R.string.money_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.money_subtitle), style = MaterialTheme.typography.bodyMedium, color = Fin.c.muted)
    }
    item { SummaryCard(s) }
    item { GoalAlert(s) }
    item {
        val nav = LocalNav.current
        val hide = LocalPrivacy.current
        val today = com.finanplus.ui.components.LocalToday.current
        val usage = remember(s, today) { Finance.budgetUsage(s, today.ym()) }
        val counts = Words.count(s.accounts.size, "conta", "contas") + " · " + Words.count(s.cards.size, "cartão", "cartões")
        NavCard(Ico.BANK, stringResource(R.string.money_accounts_cards), counts) { nav.moneyPage = PAGE_ACCOUNTS }
        NavCard(Ico.DEBT, stringResource(R.string.money_debts), Debts.summary(s, hide) ?: stringResource(R.string.money_debts_empty)) { nav.moneyPage = PAGE_DEBTS }
        NavCard(Ico.SPEED, stringResource(R.string.money_limits), MoneyView.limitsSummary(s.limits, usage) ?: stringResource(R.string.money_limits_none)) { nav.moneyPage = PAGE_LIMITS }
        val saved = if (hide) "R$ ••••" else Money.format(s.goals.sumOf { it.saved })
        NavCard(Ico.GOAL, stringResource(R.string.money_goals), if (s.goals.isEmpty()) stringResource(R.string.money_goals_empty) else Words.count(s.goals.size, "meta", "metas") + " · $saved guardados") { nav.moneyPage = PAGE_GOALS }
        NavCard(Ico.GROUP, stringResource(R.string.money_shared), sharedCardSubtitle(s, hide)) { nav.open(Sheet.SharedList) }
        NavCard(Ico.SCIENCE, stringResource(R.string.money_whatif), stringResource(R.string.money_whatif_sub)) { nav.open(Sheet.Simulator()) }
        NavCard(Ico.TAG, stringResource(R.string.cats_title), "${s.cats.expense.size} de despesa · ${s.cats.income.size} de receita") { nav.moneyPage = PAGE_CATEGORIES }
    }
}

/** Resumo do topo: saldo nas contas (o mesmo do Início), fatura aberta e guardado em metas. */
@Composable
private fun SummaryCard(s: AppState) {
    val p = Fin.c
    val today = com.finanplus.ui.components.LocalToday.current
    val o = remember(s, today) { MoneyView.overview(s, today) }
    Glass(Modifier.fillMaxWidth().padding(top = 12.dp), radius = 28.dp, padding = 16.dp) {
        Text(stringResource(R.string.money_in_accounts_title), style = MaterialTheme.typography.bodyMedium, color = p.muted, fontWeight = FontWeight.SemiBold)
        MoneyText(o.inAccounts, color = if (o.inAccounts < 0) p.red else p.text, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(stringResource(R.string.money_open_invoice), o.openInvoices, p.card, p.cardContainer, Modifier.weight(1f).fillMaxHeight())
            Tile(stringResource(R.string.money_saved_goals), o.inGoals, p.goal, p.goalContainer, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun Tile(label: String, value: Long, color: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Fin.c.text, maxLines = 1)
        MoneyText(value, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/** Meta fora do prazo vira alerta com ação ("Ajustar contribuição"). Mostra a mais urgente. */
@Composable
private fun GoalAlert(s: AppState) {
    val p = Fin.c
    val nav = LocalNav.current
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val (goal, plan) = s.goals.asSequence().map { it to Finance.goalPlan(it, today) }
        .filter { (_, pl) -> !pl.done && (pl.late || pl.pastDue) }.minByOrNull { (g, _) -> g.deadline ?: java.time.LocalDate.MAX } ?: return
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(22.dp)).background(p.warningContainer).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(Ico.WARNING, p.warning, size = 22.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                if (plan.pastDue) stringResource(R.string.money_goal_past, goal.name) else stringResource(R.string.money_goal_late, goal.name),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = p.text,
            )
            val needed = plan.needed
            if (needed != null && goal.deadline != null) Text(
                stringResource(R.string.money_goal_needed, com.finanplus.core.MonthCalendar.monthName(goal.deadline.ym()) + " de " + goal.deadline.year, if (hide) "R$ ••••" else Money.format(needed)),
                style = MaterialTheme.typography.bodyMedium, color = p.text,
            )
            androidx.compose.material3.TextButton(onClick = { nav.open(Sheet.GoalEdit(goal.id)) }, Modifier.heightIn(min = 48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.money_goal_adjust), color = p.warning, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Card de navegação: ícone, título, resumo e chevron; abre a tela do item. */
@Composable
private fun NavCard(icon: Ico, title: String, summary: String, onClick: () -> Unit) {
    val p = Fin.c
    Glass(Modifier.fillMaxWidth().padding(top = 10.dp), radius = 22.dp, padding = 0.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, p.accent, p.accent2, size = 40.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            AppIcon(Ico.NEXT, p.muted, size = 22.dp)
        }
    }
}

/** Contas e cartões: contas em linhas tocáveis e um card por cartão com a fatura dentro. */
private fun androidx.compose.foundation.lazy.LazyListScope.accountsPage(s: AppState) {
    item { PageHead(stringResource(R.string.money_accounts_cards), "") }
    item {
        val nav = LocalNav.current
        val p = Fin.c
        val hide = LocalPrivacy.current
        SectionAdd(stringResource(R.string.money_accounts), stringResource(R.string.money_add_account), stringResource(R.string.money_add_account_cd)) { nav.open(Sheet.AccountEdit()) }
        Glass(Modifier.fillMaxWidth().padding(top = 4.dp), radius = 22.dp, padding = 0.dp) {
            if (s.accounts.isEmpty()) Text(stringResource(R.string.money_accounts_empty), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(16.dp))
            s.accounts.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 1.dp, color = p.border)
                val reserved = MoneyView.reservedFrom(s, a)
                ListRow(
                    icon = Ico.BANK, title = a.name,
                    support = if (reserved > 0) stringResource(R.string.money_account_reserved, if (hide) "R$ ••••" else Money.format(reserved)) else stringResource(R.string.money_account_balance),
                    value = Finance.accountBalance(s, a),
                ) { nav.open(Sheet.AccountEdit(a.id)) }
            }
        }
        // transferência entre contas (TRANSFERENCIAS.md): só faz sentido com duas contas ou mais
        if (s.accounts.size > 1) Pill(stringResource(R.string.transfer_choice), Modifier.fillMaxWidth().padding(top = 8.dp), icon = Ico.TRANSFER) {
            nav.open(Sheet.TransferEdit())
        }
    }
    item {
        val nav = LocalNav.current
        SectionAdd(stringResource(R.string.money_cards), stringResource(R.string.money_add_card), stringResource(R.string.money_add_card_cd)) { nav.open(Sheet.CardEdit()) }
        if (s.cards.isEmpty()) Text(stringResource(R.string.money_cards_empty), style = MaterialTheme.typography.bodySmall, color = Fin.c.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
    item {
        val nav = LocalNav.current
        val today = com.finanplus.ui.components.LocalToday.current
        val cards = remember(s, today) { MoneyView.cards(s, today) }
        val firstDue = cards.firstOrNull { it.invoice != null }?.card?.id
        cards.forEach { r -> CardCard(r, primaryPay = r.card.id == firstDue, onOpen = { nav.open(Sheet.CardDetail(r.card.id)) }, onPay = { nav.open(Sheet.PayInvoice(r.card.id)) }) }
    }
}

/** Dívidas: empréstimos e financiamentos montados das parcelas da categoria Dívidas. */
private fun androidx.compose.foundation.lazy.LazyListScope.debtsPage(s: AppState) {
    item { PageHead(stringResource(R.string.money_debts), stringResource(R.string.debts_subtitle)) }
    item {
        val nav = LocalNav.current
        val p = Fin.c
        val hide = LocalPrivacy.current
        val debts = remember(s) { Debts.list(s) }
        Pill(stringResource(R.string.debts_new), Modifier.fillMaxWidth().padding(top = 6.dp), icon = Ico.ADD) { nav.open(Sheet.TxEdit(com.finanplus.core.Kind.EXPENSE, category = Debts.CATEGORY)) }
        if (debts.isEmpty()) Text(stringResource(R.string.debts_empty), style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = 12.dp))
        debts.forEach { d ->
            val next = d.next
            Glass(Modifier.fillMaxWidth().padding(top = 10.dp), radius = 22.dp, padding = 14.dp, onClick = { (next ?: d.parcels.last()).let { nav.open(Sheet.TxEdit(it.kind, it.id)) } }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Ico.DEBT, if (d.done) p.muted else p.red, if (d.done) p.accent2 else p.dangerContainer, size = 40.dp, iconSize = 22.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(com.finanplus.core.Words.ordinal(d.name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (next == null) stringResource(R.string.debts_done)
                            else stringResource(R.string.debts_next, if (hide) "R$ ••••" else Money.format(next.value), next.date.br()),
                            style = MaterialTheme.typography.bodySmall, color = if (next != null && next.date.isBefore(com.finanplus.ui.components.LocalToday.current)) p.red else p.muted,
                        )
                    }
                    if (!d.done) MoneyText(d.pendingTotal, color = p.red, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End))
                }
                Bar(if (d.totalCount > 0) d.paidCount.toFloat() / d.totalCount else 0f, if (d.done) p.green else p.accent, Modifier.padding(top = 12.dp).sensitive(hide))
                Text(
                    stringResource(R.string.debts_progress, d.paidCount, d.totalCount) + if (d.done) "" else " · " + stringResource(R.string.debts_left, if (hide) "R$ ••••" else Money.format(d.pendingTotal)),
                    style = MaterialTheme.typography.labelMedium, color = p.muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Título de seção com o botão "+" à direita (contentDescription "Adicionar …"). */
@Composable
private fun SectionAdd(title: String, addLabel: String, addDescription: String, onAdd: () -> Unit) {
    val p = Fin.c
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(start = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(p.accent2)
                .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = addDescription }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(Ico.ADD, p.accent, size = 18.dp)
            Text(addLabel, Modifier.padding(start = 4.dp), color = p.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Linha tocável: ícone em caixa, título, linha de apoio, valor à direita (opcional) e chevron. */
@Composable
private fun ListRow(icon: Ico, title: String, support: String, value: Long? = null, onClick: () -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, p.accent, p.accent2, size = 40.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(support, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) MoneyText(value, color = if (value < 0) p.red else p.text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End))
        AppIcon(Ico.NEXT, p.muted, Modifier.padding(start = 6.dp), size = 22.dp)
    }
}

/** Cartão com a fatura dentro: (1) nome, vencimento e valor; (2) barra de uso; (3) ciclo e "Pagar fatura". */
@Composable
private fun CardCard(r: MoneyView.CardRow, primaryPay: Boolean, onOpen: () -> Unit, onPay: () -> Unit) {
    val p = Fin.c
    val hide = LocalPrivacy.current
    val barColor = when (r.band) { MoneyView.Band.NORMAL -> p.accent; else -> p.warning }
    Glass(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("money_card_" + r.card.id), radius = 22.dp, padding = 16.dp, onClick = onOpen) {
        // 1: ícone, nome, vencimento e valor da fatura aberta
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ico.CARD, p.accent, p.accent2, size = 40.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.card.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.invoice != null) {
                    val suffix = MoneyView.dueSuffix(r.daysToDue)
                    Text(
                        stringResource(R.string.money_card_due, MoneyView.dm(r.invoice.due)) + (suffix?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = if (r.dueSoon) p.red else p.muted,
                        fontWeight = if (r.dueSoon) FontWeight.SemiBold else FontWeight.Normal,
                    )
                } else Text(stringResource(R.string.money_card_no_invoice), style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            if (r.invoice != null) MoneyText(r.open, color = p.red, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End))
        }
        // 2: barra de uso do limite
        val barCd = MoneyView.barDescription(r, hide)
        Column(Modifier.padding(top = 14.dp).semantics(mergeDescendants = true) { contentDescription = barCd }) {
            Bar(r.pct / 100f, barColor, Modifier.sensitive(hide))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.band == MoneyView.Band.CRITICAL) AppIcon(Ico.WARNING, p.warning, Modifier.padding(end = 4.dp), size = 16.dp)
                Text(
                    if (hide) stringResource(R.string.money_card_limit_hidden) else stringResource(R.string.money_card_limit_line, r.pct, Money.format(r.available)),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = if (r.band == MoneyView.Band.NORMAL) p.muted else p.text,
                    fontWeight = if (r.band == MoneyView.Band.CRITICAL) FontWeight.Bold else FontWeight.Normal,
                )
                Text(stringResource(R.string.money_card_limit_of, if (hide) "R$ ••••" else Money.format(r.card.limit)), style = MaterialTheme.typography.labelMedium, color = p.muted, textAlign = TextAlign.End)
            }
        }
        // 3: ciclo e pagar fatura
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.money_card_cycle, r.card.close, r.card.due), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.muted)
            if (r.invoice != null) {
                val label = stringResource(R.string.money_card_pay)
                if (primaryPay) Button(onClick = onPay, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent)) { Text(label, fontWeight = FontWeight.Bold) }
                else FilledTonalButton(onClick = onPay, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = p.accent2, contentColor = p.accent)) { Text(label, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

