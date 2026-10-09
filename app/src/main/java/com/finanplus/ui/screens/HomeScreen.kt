// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.ym
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.Tab
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.BR
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.SectionHead
import com.finanplus.ui.components.br
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** "03 de Outubro de 2026" — nomes próprios, sem depender do idioma do aparelho. */
fun fullDate(d: LocalDate): String =
    String.format(java.util.Locale.ROOT, "%02d de %s de %d", d.dayOfMonth, com.finanplus.core.assist.Br.MONTHS[d.monthValue - 1].replaceFirstChar { it.uppercase() }, d.year)

fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("MMM 'de' yyyy", BR))

@Composable
fun HomeScreen(s: AppState) {
    val nav = LocalNav.current
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val ym = today.ym()
    val flow = Finance.monthFlow(s, ym)
    val balance = Finance.currentBalance(s)
    val future = Finance.futureBalance(s, ym.atEndOfMonth(), today)
    val pending = androidx.compose.runtime.remember(s, ym, today) { com.finanplus.core.Period.monthPending(s, ym, today) }

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        // topo: nome e data; a engrenagem saiu (a aba Ajustes faz o mesmo)
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Finan+", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    // data dinâmica (ex.: "Quinta, 8 de outubro"); muda sozinha com o dia
                    Text(com.finanplus.core.MonthCalendar.dayTitle(today, today), style = MaterialTheme.typography.bodyMedium, color = p.muted)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(99.dp)).background(p.green.copy(alpha = 0.14f)).padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.SHIELD, p.green, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Privado", color = p.green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Glass(radius = 30.dp, padding = 16.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Saldo atual", balance, Modifier.weight(1f), big = true, color = if (balance < 0) p.red else p.text)
                    StatBox("Saldo previsto", future, Modifier.weight(1f), big = true, color = if (future < 0) p.red else p.accent, note = "no fim do mês")
                }
                Spacer(Modifier.height(10.dp))
                // realizados no mês, e embaixo o que ainda está pendente (para o R$ 0,00 não esconder o que vem)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Receitas do mês", flow.income, Modifier.weight(1f), color = p.green, icon = com.finanplus.ui.components.Ico.UP,
                        sub = if (pending.toReceive > 0) "a receber " to pending.toReceive else null)
                    StatBox("Despesas do mês", flow.expense, Modifier.weight(1f), color = p.red, icon = com.finanplus.ui.components.Ico.DOWN,
                        sub = if (pending.toPay > 0) "a pagar " to pending.toPay else null)
                }
                // uso das receitas: só quando já entrou alguma receita no mês
                if (flow.income > 0) {
                    Spacer(Modifier.height(14.dp))
                    Bar(flow.expense.toFloat() / flow.income, p.accent, Modifier.sensitive(hide))
                    Spacer(Modifier.height(8.dp))
                    val used = flow.expense * 100.0 / flow.income
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hide) "Valores ocultos." else "Neste mês você usou ${"%.1f".format(BR, used)}% das receitas.",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.muted, fontWeight = FontWeight.SemiBold,
                        )
                        if (!hide) Box(Modifier.clip(RoundedCornerShape(99.dp)).background(p.accent.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                            Text("${"%.0f".format(BR, maxOf(0.0, 100 - used))}% economizado", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // assistente: as 2 frases mais úteis do mês e a dica mais importante (pode ser desligado em Ajustes)
        item { HomeAssistantCard(s) }
        item { SectionHead(null, "Contas e cartões", "Gerenciar") { nav.tab = Tab.PREFS } }
        item {
            if (s.accounts.size == 1 && s.cards.isEmpty()) {
                // uma conta só: linha inteira
                val a = s.accounts[0]
                Glass(radius = 22.dp, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(p.accent2), contentAlignment = Alignment.Center) {
                            com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.BANK, p.accent, size = 20.dp)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(a.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Conta", style = MaterialTheme.typography.bodySmall, color = p.muted)
                        }
                        MoneyText(Finance.accountBalance(s, a), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
            } else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(s.accounts, key = { "a" + it.id }) { a ->
                    Glass(Modifier.width(190.dp), radius = 20.dp, padding = 14.dp) {
                        Text(a.name, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MoneyText(Finance.accountBalance(s, a), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                        Text("Conta", style = MaterialTheme.typography.labelSmall, color = p.muted)
                    }
                }
                items(s.cards, key = { "c" + it.id }) { c ->
                    val st = Finance.cardStatus(s, c, today)
                    val cur = st.current
                    Glass(Modifier.width(230.dp), radius = 20.dp, padding = 14.dp) {
                        Text(
                            c.name + " · " + (cur?.let { "Fatura ${it.ym.label()} · vence ${it.due.br()}" + if (it.closed) " · fechada" else "" } ?: "Sem fatura em aberto"),
                            style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2,
                        )
                        MoneyText(cur?.open ?: 0, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                        Row { Text("Disponível ", style = MaterialTheme.typography.labelSmall, color = p.muted); MoneyText(st.available, style = MaterialTheme.typography.labelSmall, color = p.muted) }
                        if (cur != null) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(12.dp)).background(p.accent).clickable(role = Role.Button) { nav.open(Sheet.PayInvoice(c.id)) }
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                            ) { Text("Pagar fatura", color = p.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        // limites e metas: a seção só aparece quando existe algo; enquanto não existe, um atalho em "Comece por aqui"
        if (s.limits.isNotEmpty()) {
            item { SectionHead(null, "Limites do mês", "Novo") { nav.open(Sheet.LimitEdit()) } }
            item {
                Glass(radius = 23.dp, padding = 15.dp) {
                    Text("Inclui o que ainda está pendente.", style = MaterialTheme.typography.labelSmall, color = p.muted)
                    val usage = Finance.budgetUsage(s, ym)
                    s.limits.forEach { (cat, lim) ->
                        val u = usage[cat] ?: 0L
                        val pct = if (lim > 0) u * 100.0 / lim else 0.0
                        val color = when { u > lim -> p.red; pct >= 80 -> androidx.compose.ui.graphics.Color(0xFFE6A23C); else -> p.accent }
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(cat, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                MoneyText(u, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                Text(" / ", style = MaterialTheme.typography.bodySmall)
                                MoneyText(lim, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(Modifier.height(6.dp))
                            Bar((pct / 100).toFloat(), color, Modifier.sensitive(hide), height = 7.dp)
                            Text(
                                when {
                                    u > lim -> "Limite ultrapassado"
                                    pct >= 100 -> "Limite atingido"
                                    hide -> if (pct >= 80) "Atenção: perto do limite" else "Dentro do limite" // porcentagem também revela valores
                                    pct >= 80 -> "Atenção: ${"%.0f".format(BR, pct)}% usado"
                                    else -> "${"%.0f".format(BR, pct)}% usado"
                                },
                                style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        if (s.goals.isNotEmpty()) {
            item { SectionHead(null, "Metas", "Nova") { nav.open(Sheet.GoalEdit()) } }
            items(s.goals, key = { it.id }) { g ->
                val plan = Finance.goalPlan(g, today)
                Glass(Modifier.fillMaxWidth().padding(vertical = 4.dp), radius = 20.dp, padding = 14.dp, onClick = { nav.open(Sheet.GoalEdit(g.id)) }) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MoneyText(g.saved); Text(" / "); MoneyText(g.target)
                    }
                    Spacer(Modifier.height(8.dp))
                    Bar(g.saved.toFloat() / g.target, p.accent, Modifier.sensitive(hide))
                    Spacer(Modifier.height(6.dp))
                    val info = (g.deadline?.let { "Até ${it.br()}" } ?: "Sem prazo") + if (plan.pastDue) " · prazo vencido" else ""
                    val planText = when {
                        plan.done -> "Meta atingida"
                        else -> listOfNotNull(
                            plan.needed?.let { if (hide) "Precisa de R$ ••••/mês" else "Precisa de ${com.finanplus.core.Money.format(it)}/mês" },
                            plan.eta?.let { "Plano: conclui em ${it.label()}" + if (plan.late) " (após o prazo)" else "" },
                        ).joinToString(" · ")
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(info, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.weight(1f))
                        Text(planText, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        if (s.limits.isEmpty() || s.goals.isEmpty()) {
            item { SectionHead(null, "Comece por aqui") }
            item {
                Glass(radius = 24.dp, padding = 6.dp) {
                    if (s.limits.isEmpty()) StartRow(com.finanplus.ui.components.Ico.SALARY, "Definir um limite mensal", "Acompanhe quanto gasta por categoria") { nav.open(Sheet.LimitEdit()) }
                    if (s.goals.isEmpty()) StartRow(com.finanplus.ui.components.Ico.GOAL, "Criar uma meta", "Junte para um objetivo com prazo") { nav.open(Sheet.GoalEdit()) }
                }
            }
        }
    }
}

/** Linha de "Comece por aqui": ícone, título, explicação e seta. */
@Composable
private fun StartRow(icon: com.finanplus.ui.components.Ico, title: String, subtitle: String, onClick: () -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(p.accent2), contentAlignment = Alignment.Center) {
            com.finanplus.ui.components.AppIcon(icon, p.accent, size = 20.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.NEXT, p.muted, size = 20.dp)
    }
}

@Composable
private fun StatBox(
    label: String, value: Long, modifier: Modifier, big: Boolean = false, color: androidx.compose.ui.graphics.Color,
    icon: com.finanplus.ui.components.Ico? = null, note: String? = null, sub: Pair<String, Long>? = null,
) {
    val p = Fin.c
    Column(modifier.clip(RoundedCornerShape(if (big) 21.dp else 19.dp)).background(if (p.dark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { com.finanplus.ui.components.AppIcon(icon, p.muted, size = 14.dp); Spacer(Modifier.width(4.dp)) }
            Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(5.dp))
        MoneyText(value, color = color, style = if (big) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold) else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = p.muted)
        if (sub != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sub.first, style = MaterialTheme.typography.labelSmall, color = p.muted, maxLines = 1)
            MoneyText(sub.second, color = color, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}
