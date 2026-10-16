// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.ManageItem
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SectionHead
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.br
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin

@Composable
fun AccountsScreen(s: AppState) {
    val nav = LocalNav.current
    val dialogs = LocalDialogs.current
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val ym = today.ym()
    val usage = Finance.budgetUsage(s, ym)

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.fillMaxWidth()) {
        item {
            Text("Contas", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("Contas, cartões e seus planos financeiros.", style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(bottom = 4.dp))
        }

        item {
            SectionHead("ORGANIZAÇÃO", "Contas e cartões")
            Glass(radius = 22.dp, padding = 14.dp) {
                if (s.accounts.isEmpty() && s.cards.isEmpty()) {
                    Text("Cadastre uma conta ou cartão para organizar seus lançamentos.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                }
                s.accounts.forEach { account ->
                    ManageItem(account.name, "Conta · Saldo ${if (hide) "R$ ••••" else Money.format(Finance.accountBalance(s, account))}") {
                        Pill("Editar") { nav.open(Sheet.AccountEdit(account.id)) }
                    }
                }
                s.cards.forEach { card ->
                    val status = Finance.cardStatus(s, card, today)
                    val limitText = if (hide) "R$ ••••" else Money.format(card.limit)
                    val usedText = if (hide) "R$ ••••" else Money.format(status.used)
                    ManageItem(card.name, "Cartão · limite $limitText · usado $usedText · fecha dia ${card.close} · vence dia ${card.due}") {
                        Pill("Editar") { nav.open(Sheet.CardEdit(card.id)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Adicionar conta", Modifier.weight(1f), icon = Ico.ADD) { nav.open(Sheet.AccountEdit()) }
                    Pill("Adicionar cartão", Modifier.weight(1f), icon = Ico.ADD) { nav.open(Sheet.CardEdit()) }
                }
                s.cards.forEach { card ->
                    val statement = Finance.cardStatus(s, card, today).current
                    if (statement != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Fatura ${statement.ym.label()} · vence ${statement.due.br()}", style = MaterialTheme.typography.bodySmall, color = p.muted)
                                MoneyText(statement.open, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                            }
                            Pill("Pagar fatura") { nav.open(Sheet.PayInvoice(card.id)) }
                        }
                    }
                }
            }
        }

        item {
            SectionHead("PLANEJAMENTO", "Limites mensais", "Adicionar", Ico.ADD) { nav.open(Sheet.LimitEdit()) }
            Glass(radius = 22.dp, padding = 14.dp) {
                if (s.limits.isEmpty()) Text("Defina um limite para acompanhar seus gastos por categoria.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                else {
                    Text("Inclui lançamentos pendentes deste mês.", style = MaterialTheme.typography.labelSmall, color = p.muted)
                    s.limits.forEach { (category, limit) ->
                        val spent = usage[category] ?: 0L
                        val pct = if (limit > 0) spent * 100.0 / limit else 0.0
                        val color = when { spent > limit -> p.red; pct >= 80 -> androidx.compose.ui.graphics.Color(0xFFE6A23C); else -> p.accent }
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(category, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                MoneyText(spent, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                Text(" / ", style = MaterialTheme.typography.bodySmall)
                                MoneyText(limit, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                Pill("Editar") { nav.open(Sheet.LimitEdit(category)) }
                            }
                            Bar((pct / 100).toFloat(), color, Modifier.sensitive(hide).padding(top = 4.dp))
                            Text(
                                when {
                                    spent > limit -> "Limite ultrapassado"
                                    pct >= 100 -> "Limite atingido"
                                    hide -> if (pct >= 80) "Atenção: perto do limite" else "Dentro do limite"
                                    pct >= 80 -> "Atenção: ${"%.0f".format(BR, pct)}% usado"
                                    else -> "${"%.0f".format(BR, pct)}% usado"
                                },
                                style = MaterialTheme.typography.labelSmall, color = p.muted,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHead("PLANOS", "Metas", "Criar meta", Ico.ADD) { nav.open(Sheet.GoalEdit()) }
            if (s.goals.isEmpty()) Glass(radius = 20.dp, padding = 14.dp) {
                Text("Ainda não há metas cadastradas.", fontWeight = FontWeight.Bold)
                Text("Crie uma meta para acompanhar quanto falta e o prazo do seu objetivo.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }
        items(s.goals.size, key = { s.goals[it].id }) { index ->
            val goal = s.goals[index]
            val plan = Finance.goalPlan(goal, today)
            Glass(Modifier.fillMaxWidth().padding(vertical = 4.dp), radius = 20.dp, padding = 14.dp, onClick = { nav.open(Sheet.GoalEdit(goal.id)) }) {
                Row(Modifier.fillMaxWidth()) {
                    Text(goal.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MoneyText(goal.saved); Text(" / "); MoneyText(goal.target)
                }
                    Spacer(Modifier.padding(top = 3.dp))
                Bar(if (goal.target > 0) goal.saved.toFloat() / goal.target else 0f, p.accent, Modifier.sensitive(hide))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(goal.deadline?.let { "Até ${it.br()}" } ?: "Sem prazo", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.weight(1f))
                    Text(
                        if (plan.done) "Meta atingida" else listOfNotNull(
                            plan.needed?.let { if (hide) "Precisa de R$ ••••/mês" else "Precisa de ${Money.format(it)}/mês" },
                            plan.eta?.let { "Conclui em ${it.label()}" + if (plan.late) " (após o prazo)" else "" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End,
                    )
                }
            }
        }

        item {
            SectionHead("FERRAMENTAS", "Simular meu dinheiro")
            Glass(radius = 22.dp, padding = 14.dp) {
                Text("Explore compras, mudanças na renda, economias e quitação de dívidas sem alterar seus dados.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                Pill("Abrir simulação", Modifier.padding(top = 10.dp), icon = Ico.ASSIST) { nav.open(Sheet.Simulator()) }
            }
        }

        item {
            CategoryManager(s)
        }
    }
}

@Composable
private fun CategoryManager(s: AppState) {
    val p = Fin.c
    val dialogs = LocalDialogs.current
    var newCategory by rememberSaveable { mutableStateOf("") }
    var newKind by rememberSaveable { mutableStateOf(Kind.EXPENSE) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    SectionHead(
        "PERSONALIZAÇÃO", "Categorias",
        action = if (expanded) "Minimizar" else "Expandir",
        actionIcon = Ico.DROPDOWN,
        onAction = { expanded = !expanded },
    )
    if (expanded) Glass(radius = 22.dp, padding = 14.dp) {
        Text("As categorias ajudam a organizar relatórios e lançamentos.", style = MaterialTheme.typography.bodySmall, color = p.muted)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("Nova categoria", newCategory, { newCategory = it }, Modifier.weight(1.3f), maxLength = 40)
            SelectField("Tipo", listOf(Kind.EXPENSE to "Despesa", Kind.INCOME to "Receita"), newKind, { newKind = it }, Modifier.weight(1f))
        }
        PrimaryButton("Adicionar categoria") {
            when (val result = Ops.addCategory(com.finanplus.data.Repo.state.value, newKind, newCategory)) {
                is Outcome.Err -> dialogs.notice(result.title, result.message)
                is Outcome.Ok -> { com.finanplus.data.Repo.replace(result.state); newCategory = "" }
            }
        }
        listOf(Kind.EXPENSE to "Despesas", Kind.INCOME to "Receitas").forEach { (kind, label) ->
            Eyebrow(label, Modifier.padding(top = 14.dp, bottom = 4.dp))
            s.cats.of(kind).forEach { category ->
                ManageItem(category, if (kind == Kind.EXPENSE) "Despesa" else "Receita") {
                    Pill("Renomear") {
                        dialogs.input("Renomear categoria", "Lançamentos, recorrências e limites de “$category” passam a usar o novo nome.", "Novo nome", category) { name ->
                            com.finanplus.data.Repo.commit(Ops.renameCategory(com.finanplus.data.Repo.state.value, kind, category, name))?.let { dialogs.notice(it.title, it.message) }
                        }
                    }
                    Pill("Excluir", danger = true) {
                        when (val check = Ops.checkDeleteCategory(com.finanplus.data.Repo.state.value, kind, category)) {
                            is Outcome.Err -> dialogs.notice(check.title, check.message)
                            is Outcome.Ok -> {
                                val count = Ops.categoryUseCount(com.finanplus.data.Repo.state.value, kind, category)
                                val message = if (count > 0) "Excluir “$category” da lista? $count lançamento(s) antigo(s) continuará(ão) com essa categoria no histórico." else "Excluir a categoria “$category”?"
                                dialogs.confirm("Excluir categoria", message, ok = "Excluir", danger = true) {
                                    com.finanplus.data.Repo.replace(Ops.deleteCategory(com.finanplus.data.Repo.state.value, kind, category))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
