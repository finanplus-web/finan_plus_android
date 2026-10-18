// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Cents
import com.finanplus.core.Finance
import com.finanplus.core.Money
import com.finanplus.core.Simulator
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.MoneyField
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.theme.Fin

/*
 * Simulador "E se…?" (Relatórios › E se…?). Só faz contas: nada aqui grava dados.
 * A única saída para os dados reais é "Transformar em meta", que abre o editor de meta já preenchido
 * (e ainda pede para salvar). As contas ficam em core/Simulator.kt; regras e exemplos em SIMULADOR.md.
 */

private enum class Scen(val key: String, val title: String, val sub: String, val icon: Ico) {
    SAVE("save", "E se eu economizar…", "Ex.: R$ 200 por mês, por 12 meses", Ico.INVEST),
    BUY("buy", "Quanto tempo para comprar…", "Ex.: um computador de R$ 4.500", Ico.SHOPPING),
    INCOME("income", "E se minha renda mudar…", "Ex.: diminuir 15% a partir do mês que vem", Ico.WORK),
    DEBT("debt", "E se eu antecipar uma dívida…", "Parcelas que faltam, valor para quitar e quanto sobra", Ico.BANK),
}

@Composable
fun SimulatorSheet(s: AppState, sheet: Sheet.Simulator, close: () -> Unit) {
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val auto = remember(s, today) { Simulator.base(s, today) }
    var scen by rememberSaveable { mutableStateOf(sheet.scenario) }
    // ajuste manual da base (quem tem pouco histórico ou quer testar outros números)
    var incTxt by rememberSaveable { mutableStateOf("") }
    var expTxt by rememberSaveable { mutableStateOf("") }
    val base = Simulator.Base(Money.parse(incTxt) ?: auto.income, Money.parse(expTxt) ?: auto.expense, auto.months)
    val m: (Cents) -> String = { if (hide) "R$ ••••" else Money.format(it) }
    val current = Scen.entries.firstOrNull { it.key == scen }

    if (current != null) Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { scen = null }.padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(Ico.PREV, p.accent, size = 20.dp)
        Text("E se…?", color = p.accent, fontWeight = FontWeight.Bold)
    }
    Text(current?.title?.removeSuffix("…")?.let { if (current == Scen.BUY) "Quanto tempo para comprar?" else "$it…?" } ?: "E se…?",
        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (current == null) Text("Teste decisões antes de tomá-las.", color = p.muted)
    SafeBadge()

    when (current) {
        null -> {
            BaseCard(base, auto, m, hide)
            BaseAdjust(auto, incTxt, { incTxt = it }, expTxt, { expTxt = it })
            Text("Escolha uma pergunta", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp).semantics { heading() })
            Scen.entries.forEach { sc -> ScenarioRow(sc) { scen = sc.key } }
        }
        Scen.SAVE -> SaveScenario(base, m, close)
        Scen.BUY -> BuyScenario(base, m, today, close)
        Scen.INCOME -> IncomeScenario(s, base, m)
        Scen.DEBT -> DebtScenario(s, m, today)
    }
}

@Composable
private fun SafeBadge() {
    val p = Fin.c
    Row(
        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(99.dp)).background(p.green.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(Ico.SHIELD, p.green, size = 15.dp)
        Spacer(Modifier.width(6.dp))
        Text("Só simulação: seus dados não mudam", color = p.green, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Box2(content: @Composable () -> Unit) {
    val p = Fin.c
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(p.accent2.copy(alpha = 0.55f)).padding(14.dp)) { content() }
}

@Composable
private fun BaseCard(base: Simulator.Base, auto: Simulator.Base, m: (Cents) -> String, hide: Boolean) {
    val p = Fin.c
    Box2 {
        Eyebrow(if (auto.months > 0) "Sua base · média dos últimos ${auto.months} ${if (auto.months == 1) "mês" else "meses"}" else "Sua base")
        if (auto.months == 0 && base.income == 0L && base.expense == 0L)
            Text("Ainda não há meses completos com valores realizados. Informe abaixo quanto entra e sai num mês típico.", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 6.dp)) {
            listOf(Triple("Entra", base.income, p.green), Triple("Sai", base.expense, p.red), Triple("Sobra", base.left, if (base.left < 0) p.red else p.accent)).forEach { (l, v, c) ->
                Column(Modifier.weight(1f)) {
                    Text(l, style = MaterialTheme.typography.bodySmall, color = p.muted)
                    MoneyText(v, color = c, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
        }
        if (auto.months in 1..2) Text("Pouco histórico: a média usa só ${auto.months} ${if (auto.months == 1) "mês" else "meses"}. Os resultados são aproximados.",
            style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BaseAdjust(auto: Simulator.Base, inc: String, onInc: (String) -> Unit, exp: String, onExp: (String) -> Unit) {
    val p = Fin.c
    var open by rememberSaveable { mutableStateOf(auto.months == 0) }
    Text(if (open) "Ocultar ajuste da base" else "Ajustar a base", color = p.accent, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = !open }.padding(vertical = 8.dp, horizontal = 2.dp))
    if (open) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField("Entra por mês", inc, onInc, Modifier.weight(1f), placeholder = Money.input(auto.income))
            MoneyField("Sai por mês", exp, onExp, Modifier.weight(1f), placeholder = Money.input(auto.expense))
        }
        Text("Vazio = usa a média. Vale só para esta simulação.", style = MaterialTheme.typography.labelSmall, color = p.muted)
    }
}

@Composable
private fun ScenarioRow(sc: Scen, onClick: () -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(p.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.accent2), contentAlignment = Alignment.Center) { AppIcon(sc.icon, p.accent, size = 22.dp) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(sc.title, fontWeight = FontWeight.Bold)
            Text(sc.sub, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        AppIcon(Ico.NEXT, p.muted, size = 20.dp)
    }
}

/** número grande do resultado + frase */
@Composable
private fun Result(big: String, sentence: String, color: Color = Fin.c.accent) {
    Eyebrow("Resultado")
    Text(big, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color)
    if (sentence.isNotEmpty()) Text(sentence, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Line(label: String, value: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Why(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = Fin.c.muted, modifier = Modifier.padding(top = 8.dp))

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

// ------------------------------------------------------------------ economizar
@Composable
private fun SaveScenario(base: Simulator.Base, m: (Cents) -> String, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    var per by rememberSaveable { mutableStateOf("200,00") }
    var months by rememberSaveable { mutableStateOf("12") }
    MoneyField("Guardar por mês (R$)", per, { per = it }, Modifier.padding(top = 10.dp))
    Field("Por quantos meses", months, { months = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, maxLength = 3)
    val v = Money.parse(per)?.takeIf { it > 0 }
    val n = months.toIntOrNull()?.takeIf { it in 1..600 }
    Box2 {
        if (v == null || n == null) { Text("Informe o valor por mês e por quantos meses.", color = p.muted); return@Box2 }
        val r = Simulator.save(base, v, n)
        Result(m(r.total), "juntados em ${plural(n, "mês", "meses")}.")
        Line("Sobra por mês hoje", m(base.left))
        Line("Sobra por mês guardando ${m(v)}", m(r.newLeft), if (r.newLeft < 0) p.red else Color.Unspecified)
        if (r.overLeft) Text("Esse valor é maior do que sobra por mês: faltaria dinheiro para as despesas de sempre.", color = p.red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        listOf(6, 12, 24).filter { it != n }.forEach { k -> Line("Em $k meses", m(v * k)) }
        Why("Conta: ${m(v)} × $n meses. Não considera rendimento: o Finan+ não sabe quanto o dinheiro guardado renderia.")
    }
    if (v != null) PrimaryButton("Transformar em meta", Modifier.padding(top = 12.dp)) {
        close(); nav.open(Sheet.GoalEdit(name = "Reserva", target = v * (n ?: 12), monthly = v))
    }
}

// ------------------------------------------------------------------ comprar
@Composable
private fun BuyScenario(base: Simulator.Base, m: (Cents) -> String, today: java.time.LocalDate, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    val hide = LocalPrivacy.current
    var what by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var have by rememberSaveable { mutableStateOf("") }
    var per by rememberSaveable { mutableStateOf("") }
    Field("O que", what, { what = it }, Modifier.padding(top = 10.dp), placeholder = "Ex.: Computador", maxLength = 40)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MoneyField("Preço (R$)", price, { price = it }, Modifier.weight(1f))
        MoneyField("Já tenho (R$)", have, { have = it }, Modifier.weight(1f), placeholder = "0,00")
    }
    MoneyField("Guardar por mês (R$)", per, { per = it })
    val pr = Money.parse(price)?.takeIf { it > 0 }
    val hv = Money.parse(have)?.coerceAtLeast(0) ?: 0
    val pm = Money.parse(per) ?: 0
    if (pm > 0 && base.left > 0 && !hide) Text("${Math.round(pm * 100.0 / base.left)}% do que sobra por mês (${Money.format(base.left)})", style = MaterialTheme.typography.labelSmall, color = p.muted)
    Box2 {
        if (pr == null) { Text("Informe o preço.", color = p.muted); return@Box2 }
        val r = Simulator.buy(pr, hv, pm, today)
        if (r == null) { Text("Informe quanto dá para guardar por mês.", color = p.muted); return@Box2 }
        if (r.months == 0) { Result("Já dá", "Você já tem o valor."); return@Box2 }
        Result(plural(r.months, "mês", "meses"), "Você teria o valor em ${Simulator.monthYear(r.doneYm)}.")
        // acúmulo mês a mês (até 36 barras); a última, quando o valor fica completo, em verde
        val bars = minOf(r.months, 36)
        Canvas(Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp).sensitive(hide).semantics { contentDescription = "Gráfico do valor juntado mês a mês" }) {
            val slot = size.width / bars; val w = slot * 0.7f
            for (i in 0 until bars) {
                val frac = (i + 1f) / bars
                val h = size.height * (0.12f + 0.88f * frac)
                drawRoundRect(if (i == bars - 1) p.green else p.accent.copy(alpha = 0.45f), Offset(i * slot, size.height - h), Size(w, h), CornerRadius(6f, 6f))
            }
        }
        listOf(pm * 2, pm / 2).filter { it > 0 }.forEach { alt ->
            Simulator.buy(pr, hv, alt, today)?.let { a -> Line("Guardando ${m(alt)}/mês", "${plural(a.months, "mês", "meses")} · ${Simulator.monthYear(a.doneYm)}") }
        }
        Why("Conta: falta ${m(r.missing)} ÷ ${m(pm)} por mês = ${plural(r.months, "mês", "meses")} (arredondado para cima), começando no mês que vem. Não considera rendimento nem mudança de preço.")
    }
    if (pr != null && pm > 0) PrimaryButton("Transformar em meta", Modifier.padding(top = 12.dp)) {
        close(); nav.open(Sheet.GoalEdit(name = what.trim().ifEmpty { "Compra" }, target = pr, monthly = pm))
    }
}

// ------------------------------------------------------------------ renda
@Composable
private fun IncomeScenario(s: AppState, base: Simulator.Base, m: (Cents) -> String) {
    val p = Fin.c
    var pct by rememberSaveable { mutableStateOf("-15") }
    Field("Mudança na renda (%)", pct, { pct = it.filter { c -> c.isDigit() || c in "-,." } }, Modifier.padding(top = 10.dp), placeholder = "Ex.: -15 ou 10", keyboard = KeyboardType.Number, maxLength = 6)
    val v = pct.replace(',', '.').toDoubleOrNull()?.takeIf { it > -100.0 && it <= 1000.0 }
    Box2 {
        if (v == null) { Text("Informe a mudança em porcentagem (ex.: -15 para diminuir 15%).", color = p.muted); return@Box2 }
        if (base.income == 0L) { Text("Sem renda na base: ajuste a base em \"E se…?\".", color = p.muted); return@Box2 }
        val r = Simulator.income(base, v, Simulator.goalsMonthly(s))
        Result(m(r.newLeft), if (r.newLeft >= 0) "passaria a sobrar por mês." else "faltariam por mês.", if (r.newLeft < 0) p.red else p.accent)
        Line("Renda por mês", "${m(base.income)} → ${m(r.newIncome)}")
        Line("Diferença por mês", (if (r.diff >= 0) "+ " else "− ") + m(kotlin.math.abs(r.diff)), if (r.diff < 0) p.red else p.green)
        Line("Em 12 meses", (if (r.yearDiff >= 0) "+ " else "− ") + m(kotlin.math.abs(r.yearDiff)), if (r.yearDiff < 0) p.red else p.green)
        if (r.goalsMonthly > 0) Text(
            if (r.newLeft >= r.goalsMonthly) "Suas metas pedem ${m(r.goalsMonthly)} por mês: ainda cabe no que sobra."
            else "Suas metas pedem ${m(r.goalsMonthly)} por mês: não cabe no que sobraria. Os prazos das metas atrasariam.",
            style = MaterialTheme.typography.bodySmall, color = if (r.newLeft >= r.goalsMonthly) p.muted else p.red, modifier = Modifier.padding(top = 6.dp),
        )
        Why("Conta: renda da base × (1 ${if (v >= 0) "+" else "−"} ${kotlin.math.abs(v)}%), com as despesas da base iguais. Começa a valer no mês que vem.")
    }
}

// ------------------------------------------------------------------ dívida
@Composable
private fun DebtScenario(s: AppState, m: (Cents) -> String, today: java.time.LocalDate) {
    val p = Fin.c
    val debts = remember(s, today) { Simulator.debts(s, today) }
    if (debts.isEmpty()) {
        Box2 { Text("Nenhuma compra parcelada com parcelas a pagar. As parcelas aparecem aqui quando um lançamento é feito com \"Parcelas\" maior que 1.", color = p.muted) }
        return
    }
    var sel by rememberSaveable { mutableStateOf(debts[0].groupId) }
    val d = debts.firstOrNull { it.groupId == sel } ?: debts[0]
    var payTxt by rememberSaveable(d.groupId) { mutableStateOf(Money.input(d.left)) }
    Text("Qual parcelamento", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    debts.forEach { x ->
        val on = x.groupId == d.groupId
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(if (on) p.accent.copy(alpha = 0.15f) else p.surface)
                .clickable(role = Role.RadioButton) { sel = x.groupId }.semantics { selected = on }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(x.name, fontWeight = FontWeight.Bold)
                Text("${plural(x.remaining, "parcela restante", "parcelas restantes")} de ${m(x.parcel)}${if (x.card) " · cartão" else ""}", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            MoneyText(x.left, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
    MoneyField("Valor para quitar hoje (R$)", payTxt, { payTxt = it }, Modifier.padding(top = 8.dp))
    Text("Use o valor que o credor ou o banco oferecer para quitar.", style = MaterialTheme.typography.labelSmall, color = p.muted)
    val pay = Money.parse(payTxt)?.takeIf { it > 0 }
    Box2 {
        if (pay == null) { Text("Informe o valor para quitar.", color = p.muted); return@Box2 }
        val r = Simulator.payoff(d, pay, Finance.currentBalance(s))
        Result(if (r.saved > 0) m(r.saved) else "Sem desconto", if (r.saved > 0) "a menos do que pagar as ${plural(r.months, "parcela", "parcelas")}." else "Pagar hoje o mesmo valor só adianta a saída do dinheiro.",
            if (r.saved > 0) p.green else p.muted)
        Line("Pagaria hoje", m(r.payNow))
        Line("Deixaria de pagar", "${plural(r.months, "parcela", "parcelas")} de ${m(d.parcel)}")
        Line("A partir do mês que vem, sobra a mais", "+ ${m(r.freedPerMonth)} por mês", p.green)
        Line("Saldo das contas depois de pagar", m(r.balanceAfter), if (r.balanceAfter < 0) p.red else Color.Unspecified)
        if (r.balanceAfter < 0) Text("O saldo atual não cobre esse pagamento.", color = p.red, style = MaterialTheme.typography.bodySmall)
        Why("O Finan+ não conhece os juros do parcelamento: a economia é só a diferença entre o que falta (${m(d.left)}) e o valor para quitar. " +
            if (d.card) "No cartão, a antecipação é feita com o banco do cartão." else "Confirme o valor com o credor.")
    }
}
