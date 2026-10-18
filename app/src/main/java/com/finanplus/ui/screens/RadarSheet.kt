// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Cents
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Radar
import com.finanplus.data.Repo
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.TxPrefill
import com.finanplus.ui.components.DateField
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.LocalToday
import com.finanplus.ui.components.MoneyField
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.theme.Fin
import java.time.LocalDate

/*
 * Radar (saldo previsto dia a dia), o cenário "Posso comprar?" do E se…? e o cartão do Início.
 * Só leitura: as contas ficam em core/Radar.kt; regras e exemplos em RADAR.md.
 * As únicas saídas para os dados reais abrem formulários já preenchidos (que ainda pedem para salvar).
 */

/** "3 nov" */
internal fun shortDay(d: LocalDate): String = "${d.dayOfMonth} ${com.finanplus.core.assist.Br.MONTHS[d.monthValue - 1].take(3)}"

private data class LevelLook(val label: String, val fg: Color, val bg: Color, val icon: Ico)

@Composable
private fun look(level: Radar.Level): LevelLook {
    val p = Fin.c
    return when (level) {
        Radar.Level.NEGATIVE -> LevelLook("Risco de saldo negativo", p.red, p.dangerContainer, Ico.WARNING)
        Radar.Level.LOW -> LevelLook("Abaixo da reserva", p.warning, p.warningContainer, Ico.WARNING)
        Radar.Level.OK -> LevelLook("Sem aperto previsto", p.green, p.successContainer, Ico.SHIELD)
    }
}

private fun sourceLabel(src: Radar.Source): String = when (src) {
    Radar.Source.PENDING -> "Previsto"
    Radar.Source.OVERDUE -> "Atrasado"
    Radar.Source.RECURRING -> "Recorrente"
    Radar.Source.INVOICE -> "Fatura"
    Radar.Source.SIMULATED -> "Simulado"
}

/** Frase principal do Radar, com o dia e o valor que importam. */
private fun headline(r: Radar.Result, m: (Cents) -> String): String {
    val w = r.worst
    return when (r.level) {
        Radar.Level.NEGATIVE -> "Em ${shortDay(w.date)} o saldo pode ficar em ${m(w.low)}."
        Radar.Level.LOW -> {
            val d = r.firstBelowReserve ?: r.min
            "Em ${shortDay(d.date)} o saldo fica ${m(r.reserve - d.low)} abaixo da reserva de ${m(r.reserve)}."
        }
        Radar.Level.OK -> if (r.reserve > 0) "O menor saldo é ${m(r.min.low)} em ${shortDay(r.min.date)}, ${m(r.margin)} acima da reserva."
            else "O menor saldo é ${m(r.min.low)} em ${shortDay(r.min.date)}."
    }
}

/**
 * Gráfico em degraus do saldo previsto ([days]; usa o menor ponto de cada dia). [compare] = outra série,
 * tracejada (ex.: sem a compra); linha tracejada na reserva e linha do zero quando há saldo negativo.
 */
@Composable
internal fun RadarChart(days: List<Radar.Day>, reserve: Cents, modifier: Modifier = Modifier, compare: List<Radar.Day>? = null, height: Dp = 150.dp, color: Color = Fin.c.accent, labels: Boolean = true) {
    val p = Fin.c
    val hide = LocalPrivacy.current
    if (days.size < 2) return
    val all = days.map { it.low } + (compare?.map { it.low } ?: emptyList())
    val lo = minOf(all.min(), if (reserve > 0) reserve else Long.MAX_VALUE, 0L)
    val hi = maxOf(all.max(), reserve, 1L)
    val min = days.reduce { a, b -> if (b.low < a.low) b else a }
    val desc = "Saldo previsto de ${shortDay(days.first().date)} a ${shortDay(days.last().date)}. " +
        (if (hide) "Valores ocultos." else "Menor saldo ${Money.format(min.low)} em ${shortDay(min.date)}.")
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height).sensitive(hide).semantics { contentDescription = desc }) {
            val top = 8.dp.toPx(); val bottom = size.height - 8.dp.toPx()
            val span = (hi - lo).toFloat().coerceAtLeast(1f)
            fun y(v: Long) = bottom - (v - lo) / span * (bottom - top)
            fun x(i: Int) = i * size.width / (days.size - 1)
            fun path(list: List<Radar.Day>) = Path().apply {
                moveTo(x(0), y(list[0].low))
                for (i in 1 until list.size) { lineTo(x(i), y(list[i - 1].low)); lineTo(x(i), y(list[i].low)) }
            }
            if (lo < 0) drawLine(p.red.copy(alpha = 0.45f), Offset(0f, y(0)), Offset(size.width, y(0)), 1.dp.toPx())
            if (reserve > 0) drawLine(p.warning, Offset(0f, y(reserve)), Offset(size.width, y(reserve)), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            if (compare != null && compare.size == days.size) drawPath(path(compare), p.muted,
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))))
            val main = path(days)
            val fill = Path().apply { addPath(main); lineTo(size.width, bottom); lineTo(0f, bottom); close() }
            drawPath(fill, color.copy(alpha = 0.08f))
            drawPath(main, color, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
            val mi = days.indexOf(min)
            val dot = if (min.low < 0) p.red else if (reserve > 0 && min.low < reserve) p.warning else color
            drawCircle(Color.White, 6.dp.toPx(), Offset(x(mi), y(min.low)))
            drawCircle(dot, 6.dp.toPx(), Offset(x(mi), y(min.low)), style = Stroke(3.dp.toPx()))
        }
        if (labels) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val mid = days[days.size / 2].date
            Text(shortDay(days.first().date), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted)
            Text(shortDay(mid), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(shortDay(days.last().date), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun StatusCard(r: Radar.Result, m: (Cents) -> String) {
    val l = look(r.level)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(l.bg).padding(14.dp), verticalAlignment = Alignment.Top) {
        IconTile(l.icon, l.fg, Color.White.copy(alpha = if (Fin.c.dark) 0.08f else 0.7f), size = 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(l.label.uppercase(com.finanplus.ui.components.BR), style = MaterialTheme.typography.labelMedium, color = l.fg, fontWeight = FontWeight.Bold)
            Text(headline(r, m), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Radar.causes(r).lastOrNull { it.delta > 0 }?.let { rec ->
                if (r.level != Radar.Level.OK) Text("Volta a subir com ${rec.desc} em ${shortDay(rec.date)}.", style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: @Composable () -> Unit, sub: String, modifier: Modifier) {
    Glass(modifier, radius = 18.dp, padding = 12.dp) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
        value()
        Text(sub, style = MaterialTheme.typography.labelSmall, color = Fin.c.muted)
    }
}

@Composable
private fun EventRow(e: Radar.Event, m: (Cents) -> String) {
    val p = Fin.c
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(shortDay(e.date), Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall, color = p.muted)
        Column(Modifier.weight(1f)) {
            Text(e.desc, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(sourceLabel(e.source), style = MaterialTheme.typography.labelSmall, color = if (e.source == Radar.Source.OVERDUE) p.red else p.muted)
        }
        MoneyText(kotlin.math.abs(e.delta), color = if (e.delta > 0) p.green else p.text, prefix = if (e.delta > 0) "+ " else "− ",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
    }
}

// ------------------------------------------------------------------ folha do Radar
@Composable
fun RadarSheet(s: AppState, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    val hide = LocalPrivacy.current
    val today = LocalToday.current
    val m: (Cents) -> String = { if (hide) "R$ ••••" else Money.format(it) }
    var days by rememberSaveable { mutableStateOf(Radar.DAYS) }
    val r = remember(s, today, days) { Radar.run(s, today, days) }

    Text("Radar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("Saldo previsto dia a dia, somando todas as contas.", color = p.muted)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30, 60, 90).forEach { n -> Pill("$n dias", selected = n == days) { days = n } }
    }
    StatusCard(r, m)
    Glass(Modifier.padding(top = 12.dp), radius = 22.dp, padding = 14.dp) {
        Eyebrow("Saldo previsto")
        RadarChart(r.days, r.reserve, Modifier.padding(top = 8.dp))
        if (r.reserve > 0) Text("Linha tracejada: sua reserva de ${m(r.reserve)}", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 4.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Menor saldo", { MoneyText(r.min.low, color = if (r.min.low < 0) p.red else p.text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)) },
            "em ${shortDay(r.min.date)}", Modifier.weight(1f))
        Tile("Dias negativos", {
            val neg = r.days.count { it.low < 0 }
            Text(if (neg == 0) "Nenhum" else "$neg ${if (neg == 1) "dia" else "dias"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = if (neg == 0) p.green else p.red)
        }, r.firstNegative?.let { "a partir de ${shortDay(it.date)}" } ?: "nos próximos $days dias", Modifier.weight(1f))
    }

    val why = Radar.causes(r)
    if (why.isNotEmpty()) Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        Text(if (r.level == Radar.Level.OK) "Maiores movimentos até o menor saldo" else "O que explica a queda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        why.forEach { EventRow(it, m) }
    }

    // o Radar só enxerga o que está lançado: deixa claro o que entrou na conta
    val pend = r.events.count { it.source == Radar.Source.PENDING || it.source == Radar.Source.OVERDUE }
    val recs = r.events.count { it.source == Radar.Source.RECURRING }
    val invs = r.events.count { it.source == Radar.Source.INVOICE }
    Text(
        "Considera o saldo de hoje, $pend ${if (pend == 1) "lançamento a pagar ou receber" else "lançamentos a pagar ou receber"}, " +
            "$recs ${if (recs == 1) "recorrência prevista" else "recorrências previstas"} e $invs ${if (invs == 1) "fatura" else "faturas"}. " +
            "Gastos do dia a dia que ainda não foram lançados ficam de fora.",
        style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 10.dp),
    )
    if (!r.hasIncome) Text("Nenhuma receita prevista no período. Lance o salário como recorrência para a previsão ficar completa.",
        style = MaterialTheme.typography.bodySmall, color = p.warning, modifier = Modifier.padding(top = 6.dp))

    ReserveEditor(s.reserve, m)
    PrimaryButton("Posso comprar?", Modifier.padding(top = 12.dp)) { nav.open(Sheet.Simulator("canbuy")) }
}

@Composable
private fun ReserveEditor(reserve: Cents, m: (Cents) -> String) {
    val p = Fin.c
    var open by rememberSaveable { mutableStateOf(false) }
    var txt by rememberSaveable { mutableStateOf(if (reserve > 0) Money.input(reserve) else "") }
    Glass(Modifier.padding(top = 12.dp), radius = 20.dp, padding = 12.dp, onClick = if (open) null else ({ open = true })) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ico.SHIELD, p.green, p.successContainer, size = 36.dp, iconSize = 18.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Reserva mínima", fontWeight = FontWeight.Bold)
                Text("O Radar avisa quando o saldo ficar abaixo dela", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Text(if (reserve > 0) m(reserve) else "Não definida", fontWeight = FontWeight.Bold, color = if (reserve > 0) p.text else p.muted)
        }
        if (open) {
            MoneyField("Reserva mínima (R$)", txt, { txt = it }, Modifier.padding(top = 8.dp), placeholder = "Ex.: 500,00")
            Text("Vazio ou zero: o Radar só avisa quando o saldo ficar negativo.", style = MaterialTheme.typography.labelSmall, color = p.muted)
            val v = if (txt.isBlank()) 0L else Money.parse(txt)
            val ok = v != null && v >= 0
            PrimaryButton(if (ok) "Salvar reserva" else "Valor inválido", Modifier.padding(top = 8.dp)) {
                if (v != null && v >= 0) { Repo.update { it.copy(reserve = v) }; open = false }
            }
        }
    }
}

// ------------------------------------------------------------------ cartão do Início
@Composable
fun RadarHomeCard(s: AppState) {
    val p = Fin.c
    val nav = LocalNav.current
    val hide = LocalPrivacy.current
    val today = LocalToday.current
    val m: (Cents) -> String = { if (hide) "R$ ••••" else Money.format(it) }
    val r = remember(s, today) { Radar.run(s, today) }
    val l = look(r.level)
    Glass(Modifier.padding(top = 12.dp), radius = 24.dp, padding = 14.dp, onClick = { nav.open(Sheet.Radar) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ico.FORECAST, p.accent, p.accent2, size = 38.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Radar", fontWeight = FontWeight.Bold)
                Text("Próximos ${Radar.DAYS} dias", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Text(when (r.level) { Radar.Level.OK -> "Tranquilo"; Radar.Level.LOW -> "Atenção"; Radar.Level.NEGATIVE -> "Risco" },
                Modifier.clip(RoundedCornerShape(99.dp)).background(l.bg).padding(horizontal = 10.dp, vertical = 4.dp),
                color = l.fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        RadarChart(r.days, r.reserve, Modifier.padding(top = 10.dp), height = 56.dp, labels = false)
        Text(headline(r, m), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Ver Radar", Modifier.weight(1f)) { nav.open(Sheet.Radar) }
            Pill("Posso comprar?", Modifier.weight(1f), selected = true, icon = Ico.SHOPPING) { nav.open(Sheet.Simulator("canbuy")) }
        }
    }
}

// ------------------------------------------------------------------ E se…? › Posso comprar?
@Composable
internal fun CanBuyScenario(s: AppState, m: (Cents) -> String, today: LocalDate, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    var what by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(today) }
    var onCard by rememberSaveable { mutableStateOf(false) }
    var accountId by rememberSaveable { mutableStateOf(s.accounts[0].id) }
    var cardId by rememberSaveable { mutableStateOf(s.cards.firstOrNull()?.id ?: "") }
    var parcels by rememberSaveable { mutableStateOf("1") }
    val card = onCard && s.card(cardId) != null

    Field("O que (opcional)", what, { what = it }, Modifier.padding(top = 10.dp), placeholder = "Ex.: Celular novo", maxLength = 40)
    MoneyField("Quanto custa (R$)", price, { price = it })
    DateField("Quando", date, { date = it })
    if (s.cards.isNotEmpty()) SelectField("Como vai pagar", listOf(false to "À vista (conta / dinheiro)", true to "No cartão de crédito"), onCard, { onCard = it })
    if (card) {
        SelectField("Cartão", s.cards.map { it.id to it.name }, cardId, { cardId = it })
        Field("Parcelas", parcels, { parcels = it.filter(Char::isDigit) }, keyboard = androidx.compose.ui.text.input.KeyboardType.Number, maxLength = 2)
    } else if (s.accounts.size > 1) SelectField("Sai de", s.accounts.map { it.id to it.name }, accountId, { accountId = it })

    val value = Money.parse(price)?.takeIf { it > 0 }
    val n = if (card) (parcels.toIntOrNull() ?: 1).coerceIn(1, 60) else 1
    val d = date?.takeIf { !it.isBefore(today) }
    if (date != null && d == null) Text("Escolha hoje ou uma data futura.", style = MaterialTheme.typography.bodySmall, color = p.red)
    if (value == null || d == null) {
        Glass(Modifier.padding(top = 12.dp), radius = 20.dp, padding = 14.dp) { Text("Informe o valor e a data da compra.", color = p.muted) }
        return
    }
    if (value < n) {
        Glass(Modifier.padding(top = 12.dp), radius = 20.dp, padding = 14.dp) { Text("O valor é pequeno demais para tantas parcelas.", color = p.muted) }
        return
    }
    val purchase = Radar.Purchase(value, d, accountId, if (card) cardId else "", n, what.trim().ifEmpty { "Compra" })
    val c = remember(s, today, purchase) { Radar.check(s, today, purchase) }
    val alts = remember(s, today, purchase) { Radar.alternatives(s, today, purchase) }
    val l = look(c.sim.level)

    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(l.bg).padding(14.dp)) {
        Text(l.label.uppercase(com.finanplus.ui.components.BR), style = MaterialTheme.typography.labelMedium, color = l.fg, fontWeight = FontWeight.Bold)
        Text(
            when (c.sim.level) {
                Radar.Level.NEGATIVE -> "Em ${shortDay(c.sim.worst.date)} faltariam ${m(-c.sim.worst.low)}"
                Radar.Level.LOW -> "O saldo ficaria abaixo da sua reserva"
                Radar.Level.OK -> "O saldo previsto continua positivo"
            },
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
        )
        RadarChart(c.sim.days, c.sim.reserve, Modifier.padding(top = 8.dp), compare = c.base.days, height = 120.dp, color = if (c.sim.level == Radar.Level.NEGATIVE) p.red else p.accent)
        Text("Tracejado: sem a compra", style = MaterialTheme.typography.labelSmall, color = p.muted)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Menor saldo sem", { MoneyText(c.base.min.low, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)) }, shortDay(c.base.min.date), Modifier.weight(1f))
            Tile("Menor saldo com", { MoneyText(c.sim.min.low, color = if (c.sim.min.low < 0) p.red else p.text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)) }, shortDay(c.sim.min.date), Modifier.weight(1f))
        }
        val drops = Radar.causes(c.sim).filter { it.delta < 0 && it.source != Radar.Source.SIMULATED }.take(2)
        val rec = Radar.causes(c.sim).lastOrNull { it.delta > 0 }
        if (c.sim.level != Radar.Level.OK && drops.isNotEmpty()) Text(
            "Por quê: " + drops.joinToString(" e ") { "${it.desc} (${shortDay(it.date)})" } +
                (if (drops.size == 1) " sai" else " saem") + (rec?.let { " antes de ${it.desc} (${shortDay(it.date)})." } ?: " no período."),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
        )
        if (card) Text("No cartão, ${if (n > 1) "cada parcela sai" else "o valor sai"} da conta no vencimento da fatura.", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
        if (c.noIncome) Text("Nenhuma receita prevista no período: a previsão pode estar incompleta.", style = MaterialTheme.typography.bodySmall, color = p.warning, modifier = Modifier.padding(top = 6.dp))
    }

    if (alts.isNotEmpty()) {
        Text("Alternativas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp).semantics { heading() })
        Text("Toque numa opção para continuar. Nada é lançado sem você salvar.", style = MaterialTheme.typography.bodySmall, color = p.muted)
        alts.forEach { a -> AltRow(s, a, m, close) }
    }

    PrimaryButton("Lançar como previsto", Modifier.padding(top = 12.dp)) {
        close(); nav.open(prefillSheet(purchase))
    }
    Text("Abre o formulário de despesa já preenchido, como \"a pagar\". Só é gravado se você salvar.", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 4.dp))
}

private fun prefillSheet(q: Radar.Purchase) = Sheet.TxEdit(Kind.EXPENSE, date = q.date,
    prefill = TxPrefill(q.value, if (q.desc == "Compra") "" else q.desc, q.accountId, q.cardId, q.parcels))

@Composable
private fun AltRow(s: AppState, a: Radar.Alternative, m: (Cents) -> String, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    val l = look(a.result.level)
    val (title, detail) = when (a.kind) {
        Radar.AltKind.POSTPONE -> "Comprar em ${shortDay(a.purchase.date)}" to
            (a.result.events.lastOrNull { it.delta > 0 && it.date.isBefore(a.purchase.date) }
                ?.let { "Depois de ${it.desc} (${shortDay(it.date)})." } ?: "Quando o saldo previsto comporta a compra.")
        Radar.AltKind.PARCELS -> {
            val name = s.card(a.purchase.cardId)?.name ?: "cartão"
            "Parcelar em ${a.purchase.parcels}× no $name" to "${m(a.purchase.value / a.purchase.parcels)} por fatura. Se houver juros, o custo total aumenta."
        }
        Radar.AltKind.GOAL -> "Usar ${m(a.amount)} da meta ${a.goalName}" to "O valor volta da meta para a conta; a meta fica mais longe."
    }
    Glass(Modifier.padding(top = 8.dp), radius = 18.dp, padding = 12.dp, onClick = {
        close()
        when (a.kind) {
            Radar.AltKind.GOAL -> nav.open(Sheet.GoalEdit(a.goalId, move = -a.amount))
            else -> nav.open(prefillSheet(a.purchase))
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = p.muted)
                Spacer(Modifier.height(4.dp))
                Text("${l.label} · menor saldo ${m(a.result.min.low)} em ${shortDay(a.result.min.date)}", style = MaterialTheme.typography.labelMedium, color = l.fg, fontWeight = FontWeight.Bold)
            }
            com.finanplus.ui.components.AppIcon(Ico.NEXT, p.muted, size = 20.dp)
        }
    }
}
