// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
fun HomeScreen(s: AppState, listState: LazyListState = rememberLazyListState()) {
    val nav = LocalNav.current
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val ym = today.ym()
    val flow = Finance.monthFlow(s, ym)
    val balance = Finance.currentBalance(s)
    val projectionEnd = ym.atEndOfMonth()
    val future = Finance.futureBalance(s, projectionEnd, today)
    val pending = androidx.compose.runtime.remember(s, ym, today) { com.finanplus.core.Period.monthPending(s, ym, today) }
    val nextDues = androidx.compose.runtime.remember(s, today) { Finance.upcomingDues(s, today) }
    // saldo no fim do mês anterior = saldo atual sem o que foi realizado nas contas a partir deste mês
    // (mesma regra de Finance.accountBalance); null quando ainda não há lançamentos antes deste mês
    val prevEnd: Long? = androidx.compose.runtime.remember(s, ym, balance) {
        if (s.txs.none { it.date.isBefore(ym.atDay(1)) }) null
        else {
            val ids = s.accounts.map { it.id }.toSet()
            balance - s.txs.filter { it.accountId in ids && !it.isCard && it.paid && !it.date.isBefore(ym.atDay(1)) }
                .fold(0L) { n, t -> if (t.kind == Kind.INCOME) n + t.value else n - t.value }
        }
    }

    LazyColumn(state = listState, contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        // topo: nome e data; a engrenagem saiu (a aba Ajustes faz o mesmo)
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Finan+", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    // data dinâmica (ex.: "Quinta, 8 de outubro"); muda sozinha com o dia
                    Text("Seu dinheiro, sem complicação.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
                    Text(com.finanplus.core.MonthCalendar.dayTitle(today, today), style = MaterialTheme.typography.labelSmall, color = p.muted)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(99.dp))
                        .background(if (hide) p.successContainer else p.accent2)
                        .clickable(role = Role.Button) { com.finanplus.data.Repo.update { it.copy(privacy = !it.privacy) } }
                        .semantics { contentDescription = if (hide) "Mostrar valores financeiros" else "Ocultar valores financeiros" }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.SHIELD, if (hide) p.green else p.accent, size = 16.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(if (hide) "Mostrar valores" else "Ocultar valores", color = if (hide) p.green else p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Glass(radius = 30.dp, padding = 16.dp) {
                // saldo atual em destaque; as ondas ficam junto ao rótulo, sem passar pelo valor
                Box(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Ico.WALLET, p.accent, p.accent2, size = 44.dp, iconSize = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Saldo atual", style = MaterialTheme.typography.bodyMedium, color = p.muted, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        MoneyText(
                            balance, color = if (balance < 0) p.red else p.text,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // saldo previsto: faixa interna, como no conceito
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(innerBg()).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(Ico.FORECAST, p.accent, p.accent2)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Saldo previsto até ${projectionEnd.br()}", style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MoneyText(future, color = if (future < 0) p.red else p.accent, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                        Text(
                            "Considera valores pendentes, faturas e recorrências até esta data.",
                            style = MaterialTheme.typography.labelSmall, color = p.muted, maxLines = 2,
                        )
                        // comparação com o fim do mês anterior; no primeiro mês não há com o que comparar
                        if (!hide && prevEnd != null) {
                            Spacer(Modifier.height(6.dp))
                            val (chipText, chipColor) = when {
                                prevEnd == null -> "1º mês · sem comparação" to p.muted
                                else -> {
                                    val d = future - prevEnd
                                    val sign = if (d >= 0) "+" else "−"
                                    "$sign${com.finanplus.core.Money.format(kotlin.math.abs(d))} vs. fim de ${com.finanplus.core.MonthCalendar.monthName(ym.minusMonths(1))}" to (if (d >= 0) p.green else p.red)
                                }
                            }
                            Box(Modifier.clip(RoundedCornerShape(99.dp)).background(chipColor.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                Text(chipText, color = chipColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                // como o previsto foi calculado (só quando a conta fecha com os números da tela)
                if (!hide && (pending.toReceive > 0 || pending.toPay > 0) && balance + pending.toReceive - pending.toPay == future) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hoje ${com.finanplus.core.Money.format(balance)} + ${com.finanplus.core.Money.format(pending.toReceive)} a receber − ${com.finanplus.core.Money.format(pending.toPay)} a pagar",
                        style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                // uso das receitas: só quando já entrou alguma receita no mês
                if (flow.income > 0) {
                    Spacer(Modifier.height(14.dp))
                    Bar(flow.expense.toFloat() / flow.income, p.accent, Modifier.sensitive(hide))
                    Spacer(Modifier.height(8.dp))
                    val used = flow.expense * 100.0 / flow.income
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hide) "Valores ocultos." else "Despesas equivalem a ${"%.1f".format(BR, used)}% das receitas recebidas.",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.muted, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        // Radar: saldo previsto dia a dia e atalho para o "Posso comprar?" (RADAR.md)
        item { RadarHomeCard(s) }
        // pendências e progresso deixam claro o que falta entrar e sair, sem misturar valores realizados.
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PendingCard("Falta receber", pending.toReceive, flow.income, "recebido", p.green, Ico.INCOME, Modifier.weight(1f))
                // Em despesas, faturas abertas podem corresponder a compras já registradas; não exibimos uma porcentagem que as conte duas vezes.
                PendingCard("Falta pagar", pending.toPay, flow.expense, "pago", p.red, Ico.EXPENSE, Modifier.weight(1f), showProgress = false)
            }
        }
        item {
            SectionHead(null, "Próximos vencimentos")
            if (nextDues.isEmpty()) NextPaymentCard(null) {}
            else nextDues.forEach { due ->
                NextPaymentCard(due) {
                    if (due.type == com.finanplus.core.ReminderType.INVOICE_DUE) nav.open(Sheet.PayInvoice(due.refId))
                    else s.txs.firstOrNull { it.id == due.refId }?.let { nav.open(Sheet.TxEdit(it.kind, it.id)) }
                }
            }
        }
        // assistente: as 2 frases mais úteis do mês e a dica mais importante (pode ser desligado em Ajustes)
        item { HomeAssistantCard(s) }

    }
}
/** Cartão do que ainda falta entrar/sair; realizado e pendente permanecem separados. */
@Composable
private fun PendingCard(
    label: String,
    pending: Long,
    realized: Long,
    realizedLabel: String,
    color: Color,
    icon: Ico,
    modifier: Modifier,
    showProgress: Boolean = true,
) {
    val p = Fin.c
    val hide = LocalPrivacy.current
    val nav = LocalNav.current
    val total = pending + realized
    val progress = if (total > 0) realized.toFloat() / total else 0f
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxHeight().clip(shape).background(p.surface, shape)
            .background(color.copy(alpha = if (p.dark) 0.10f else 0.07f), shape)
            .border(1.dp, p.border, shape).clickable(role = Role.Button) { nav.tab = Tab.MOVES }
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, color, color.copy(alpha = 0.14f), size = 34.dp, iconSize = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(7.dp))
        MoneyText(pending, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
        if (showProgress && total > 0) {
            Spacer(Modifier.height(7.dp))
            Bar(progress, color, Modifier.sensitive(hide), height = 6.dp)
            Text(
                if (hide) "Progresso oculto" else "${"%.0f".format(BR, progress * 100)}% $realizedLabel",
                style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 5.dp),
            )
        } else Text(
            when {
                !showProgress && pending > 0 -> "Contas e faturas em aberto neste mês"
                !showProgress -> "Tudo pago neste mês"
                else -> "Nada registrado neste mês"
            },
            style = MaterialTheme.typography.labelSmall, color = p.muted,
        )
    }
}

@Composable
private fun NextPaymentCard(due: com.finanplus.core.Reminder?, onOpen: () -> Unit) {
    val p = Fin.c
    if (due == null) {
        Glass(radius = 20.dp, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Ico.CHECK, p.green, p.green.copy(alpha = 0.12f))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Nenhum pagamento pendente", fontWeight = FontWeight.Bold)
                    Text("Quando houver uma conta ou fatura, ela aparecerá aqui.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                }
            }
        }
        return
    }
    val overdue = due.date.isBefore(com.finanplus.ui.components.LocalToday.current)
    Glass(radius = 20.dp, padding = 14.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (overdue) Ico.WARNING else Ico.CALENDAR, if (overdue) p.red else p.accent, if (overdue) p.red.copy(alpha = 0.12f) else p.accent2)
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(due.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (overdue) "Vencido em " else "Vence em ") + due.date.br(),
                    style = MaterialTheme.typography.bodySmall, color = if (overdue) p.red else p.muted,
                )
            }
            MoneyText(due.amount, color = p.red, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
        }
        Text("Ver pagamento  ›", color = p.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 52.dp, top = 8.dp))
    }
}

/**
 * Ondas ao lado do saldo. São só decoração (não representam dados), por isso ficam suaves
 * e não têm ponto, eixo nem valor. Mesmas curvas do conceito (viewBox 230×120).
 */
@Composable
private fun BalanceWaves(modifier: Modifier) {
    val accent = Fin.c.accent
    val dark = Fin.c.dark
    androidx.compose.foundation.Canvas(modifier.clearAndSetSemantics { }) {
        val sx = size.width / 230f
        val sy = size.height / 120f
        fun main() = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 78f * sy)
            cubicTo(30f * sx, 70f * sy, 45f * sx, 40f * sy, 75f * sx, 46f * sy)
            cubicTo(105f * sx, 52f * sy, 112f * sx, 92f * sy, 142f * sx, 88f * sy)
            cubicTo(170f * sx, 84f * sy, 178f * sx, 50f * sy, 200f * sx, 44f * sy)
            cubicTo(214f * sx, 40f * sy, 222f * sx, 48f * sy, 230f * sx, 54f * sy)
        }
        // preenchimento em degradê embaixo da curva principal
        val fill = main().apply { lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(accent.copy(alpha = if (dark) 0.16f else 0.20f), accent.copy(alpha = 0f))))
        // onda de fundo, mais fina
        val back = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 92f * sy)
            cubicTo(35f * sx, 86f * sy, 55f * sx, 66f * sy, 85f * sx, 70f * sy)
            cubicTo(115f * sx, 74f * sy, 125f * sx, 100f * sy, 155f * sx, 98f * sy)
            cubicTo(185f * sx, 96f * sy, 195f * sx, 74f * sy, 230f * sx, 72f * sy)
        }
        drawPath(back, accent.copy(alpha = 0.22f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        drawPath(
            main(), accent.copy(alpha = 0.55f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/** Fundo das faixas internas dos cartões de vidro. */
@Composable
internal fun innerBg(): Color = if (Fin.c.dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)

