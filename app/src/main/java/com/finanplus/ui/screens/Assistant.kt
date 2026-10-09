// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.assist.Ask
import com.finanplus.core.assist.Categorizer
import com.finanplus.core.assist.Insight
import com.finanplus.core.assist.Insights
import com.finanplus.core.assist.MoneyFmt
import com.finanplus.core.assist.Source
import com.finanplus.data.AssistData
import com.finanplus.data.DevicePrefs
import com.finanplus.data.DeviceSettings
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Nav
import com.finanplus.ui.Sheet
import com.finanplus.ui.Tab
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.ManageItem
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.SwitchRow
import com.finanplus.ui.theme.Fin
import java.time.LocalDate

/*
 * Interface do assistente do Finan+. Todo o cálculo está em core/assist (sem Android, testado).
 * Aqui só mostramos os resultados, com "Por quê?" em cada item e respeitando "Ocultar valores".
 */

private val HIDDEN_MONEY: MoneyFmt = { _ -> "R$ ••••" }
private val VISIBLE_MONEY: MoneyFmt = { c -> Money.format(c) }

/** Formatação de valores que respeita "Ocultar valores". */
@Composable
fun rememberMoneyFmt(): MoneyFmt = if (LocalPrivacy.current) HIDDEN_MONEY else VISIBLE_MONEY

/** Abre a aba Lançamentos já filtrada pelo que a dica/resposta usou. */
private fun openMoves(nav: Nav, query: String?, from: LocalDate?, to: LocalDate?, kind: Kind? = null) {
    val f = nav.filters
    f.query = query ?: ""
    f.from = from; f.to = to
    f.kind = kind; f.paid = null
    nav.sheet = null
    nav.tab = Tab.MOVES
}

@Composable
private fun WhyToggle(why: String) {
    var open by remember(why) { mutableStateOf(false) }
    val p = Fin.c
    Text(
        if (open) "Ocultar explicação" else "Por quê?",
        color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "explicação aberta" else "explicação fechada" }.padding(vertical = 6.dp),
    )
    if (open) Text(
        why, style = MaterialTheme.typography.bodySmall, color = p.muted,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accent2.copy(alpha = 0.6f)).padding(10.dp),
    )
}

@Composable
private fun TipItem(t: Insight, onDismiss: (() -> Unit)?, onOpen: (() -> Unit)?) {
    val p = Fin.c
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(p.accent2.copy(alpha = 0.45f)).padding(12.dp)) {
        Eyebrow(t.type.label)
        Text(t.title, fontWeight = FontWeight.Bold)
        Text(t.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        WhyToggle(t.why)
        if (onOpen != null || onDismiss != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (onOpen != null) Pill("Ver lançamentos", onClick = onOpen)
            if (onDismiss != null) Pill("Dispensar", onClick = onDismiss)
        }
    }
}

private fun visibleTips(s: AppState, dev: DeviceSettings, money: MoneyFmt, today: LocalDate) =
    Insights.tips(s, today, money).filter { it.id !in dev.dismissedTips }

// ------------------------------------------------------------------ cartão no Início

@Composable
fun HomeAssistantCard(s: AppState) {
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val dev by prefs.flow.collectAsStateWithLifecycle()
    if (!dev.assistTips && !dev.assistAsk) return
    val nav = LocalNav.current
    val money = rememberMoneyFmt()
    val today = com.finanplus.ui.components.LocalToday.current
    val report = remember(s, money, today) { Insights.report(s, today, money) }
    val tips = remember(s, money, today, dev.dismissedTips) { visibleTips(s, dev, money, today) }
    val p = Fin.c

    // cartão compacto: as 2 frases mais úteis do mês (Insights.highlights), a dica principal e um único link.
    // O resumo completo, todas as dicas, "Por quê?" e as perguntas ficam na folha do assistente.
    Glass(Modifier.padding(top = 12.dp), radius = 26.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.ASSIST, p.muted, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text("Assistente", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.semantics { heading() })
        }
        if (dev.assistTips) {
            val lines = report.highlights.ifEmpty { report.lines.take(1) }
            Spacer(Modifier.height(4.dp))
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
            tips.firstOrNull()?.let { t ->
                TipItem(
                    t,
                    onDismiss = { prefs.update { it.copy(dismissedTips = it.dismissedTips + t.id) } },
                    onOpen = if (t.query != null || t.from != null) ({ openMoves(nav, t.query, t.from, t.to) }) else null,
                )
            }
        } else Text("Pergunte sobre seus gastos.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        val link = when {
            dev.assistTips && tips.size > 1 -> "Ver as ${tips.size} dicas"
            dev.assistTips -> "Abrir assistente"
            else -> "Perguntar"
        }
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { nav.open(Sheet.Assistant) }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(link, color = p.accent, fontWeight = FontWeight.Bold)
            com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.NEXT, p.accent, size = 18.dp)
        }
    }
}

// ------------------------------------------------------------------ folha do assistente

@Composable
fun AssistantSheet(s: AppState, close: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val dev by prefs.flow.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val money = rememberMoneyFmt()
    val today = com.finanplus.ui.components.LocalToday.current
    val p = Fin.c
    var question by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf("") }
    var showDismissed by remember { mutableStateOf(false) }

    Text("Assistente", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("Tudo é calculado neste aparelho, sem internet, a partir dos seus lançamentos. Toque em “Por quê?” para ver a regra usada.",
        style = MaterialTheme.typography.bodyMedium, color = p.muted)
    Spacer(Modifier.height(12.dp))

    if (dev.assistAsk) {
        Eyebrow("Pergunte")
        Field("Sua pergunta", question, { question = it }, placeholder = "Ex.: quanto gastei com mercado em agosto?", maxLength = 120)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ask.EXAMPLES.forEach { ex -> Pill(ex) { question = ex; asked = ex } }
        }
        com.finanplus.ui.components.PrimaryButton("Perguntar") { asked = question.trim() }
        if (asked.isNotEmpty()) {
            val a = remember(asked, s, money) { Ask.answer(asked, s, today, money) }
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(p.accent2.copy(alpha = 0.55f)).padding(14.dp)) {
                Text(a.text, fontWeight = FontWeight.SemiBold)
                Text(a.understood, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                if (a.matches.isNotEmpty()) Pill("Ver lançamentos", Modifier.padding(top = 8.dp)) {
                    openMoves(nav, a.parsed.category ?: a.parsed.words.firstOrNull(), a.parsed.period.from, a.parsed.period.to, a.parsed.kind)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (dev.assistTips) {
        val report = remember(s, money, today) { Insights.report(s, today, money) }
        Eyebrow("Resumo")
        Text(report.title, style = MaterialTheme.typography.titleMedium)
        report.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        WhyToggle(report.why)
        Spacer(Modifier.height(12.dp))

        val all = remember(s, money, today) { Insights.tips(s, today, money) }
        val visible = all.filter { it.id !in dev.dismissedTips }
        val dismissed = all.filter { it.id in dev.dismissedTips }
        Eyebrow("Dicas")
        if (visible.isEmpty()) Text("Nenhuma dica no momento: nada fora do padrão nos seus lançamentos.", style = MaterialTheme.typography.bodySmall, color = p.muted)
        visible.forEach { t ->
            TipItem(
                t,
                onDismiss = { prefs.update { it.copy(dismissedTips = it.dismissedTips + t.id) } },
                onOpen = if (t.query != null || t.from != null) ({ openMoves(nav, t.query, t.from, t.to) }) else null,
            )
        }
        if (dismissed.isNotEmpty()) {
            Pill(if (showDismissed) "Esconder dispensadas" else "Mostrar dispensadas (${dismissed.size})", Modifier.padding(top = 6.dp)) { showDismissed = !showDismissed }
            if (showDismissed) dismissed.forEach { t ->
                TipItem(t, onDismiss = null, onOpen = null)
                Pill("Mostrar de novo") { prefs.update { it.copy(dismissedTips = it.dismissedTips - t.id) } }
            }
        }
    }
    if (!dev.assistAsk && !dev.assistTips) Text("O resumo, as dicas e as perguntas estão desligados em Ajustes › Assistente.", color = p.muted)
    Spacer(Modifier.height(12.dp))
    com.finanplus.ui.components.PrimaryButton("Fechar", onClick = close)
}

// ------------------------------------------------------------------ sugestão de categoria no editor

/**
 * Mostra "Sugestão: X · Usar" abaixo da descrição. Só aparece quando há confiança suficiente
 * e quando a categoria sugerida é diferente da escolhida.
 */
@Composable
fun CategoryHint(s: AppState, kind: Kind, desc: String, current: String, enabled: Boolean, onUse: (String) -> Unit) {
    val ctx = LocalContext.current
    val dev by DevicePrefs.get(ctx).flow.collectAsStateWithLifecycle()
    if (!dev.assistCategory || !enabled) return
    val categorizer = remember(s.txs, s.cats, kind) { Categorizer.build(s, kind, AssistData.dictionary(ctx)) }
    val sug = remember(categorizer, desc) { if (desc.trim().length < 2) null else categorizer.suggest(desc) } ?: return
    if (sug.category == current) return
    val p = Fin.c
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(p.accent.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.ASSIST, p.accent, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Sugestão: ${sug.category}", fontWeight = FontWeight.Bold)
                }
                Text(
                    when (sug.source) { Source.SAME_DESCRIPTION -> "pelo que você já lançou"; Source.LEARNED -> "aprendido com seus lançamentos"; Source.DICTIONARY -> "pelo dicionário" },
                    style = MaterialTheme.typography.labelSmall, color = p.muted,
                )
            }
            Pill("Usar", selected = true) { onUse(sug.category) }
        }
        WhyToggle(sug.why)
    }
}

// ------------------------------------------------------------------ Ajustes › Assistente

@Composable
fun AssistantSettings(s: AppState, dev: DeviceSettings) {
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val p = Fin.c
    Text("Funciona só neste aparelho, sem internet e sem enviar dados. Cada função pode ser desligada.",
        style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
    SwitchRow("Sugerir categoria", "Ao digitar a descrição de um lançamento novo", dev.assistCategory) { v -> prefs.update { it.copy(assistCategory = v) } }
    SwitchRow("Resumo e dicas", "No Início: resumo do mês, gastos fora do padrão, fixos, duplicados", dev.assistTips) { v -> prefs.update { it.copy(assistTips = v) } }
    SwitchRow("Perguntas rápidas", "Ex.: “quanto gastei com mercado em agosto?”", dev.assistAsk) { v -> prefs.update { it.copy(assistAsk = v) } }
    if (dev.dismissedTips.isNotEmpty()) Pill("Restaurar ${dev.dismissedTips.size} dica(s) dispensada(s)", Modifier.padding(top = 6.dp)) {
        prefs.update { it.copy(dismissedTips = emptySet()) }
    }

    var open by rememberSaveable { mutableStateOf(false) }
    Text(
        if (open) "Ocultar o que o assistente aprendeu" else "Ver o que o assistente aprendeu",
        color = p.accent, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = !open }.padding(vertical = 6.dp),
    )
    if (open) {
        val dict = AssistData.dictionary(ctx)
        Text(
            "O aprendizado vem dos seus próprios lançamentos (que ficam criptografados no aparelho). Não existe uma cópia separada: " +
                "corrigir a categoria de um lançamento corrige o aprendizado, e apagar o lançamento apaga o que ele ensinou.",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        for (k in listOf(Kind.EXPENSE, Kind.INCOME)) {
            val c = remember(s.txs, s.cats, k) { Categorizer.build(s, k, dict) }
            val words = c.learnedWords()
            Eyebrow(if (k == Kind.EXPENSE) "Despesas · ${c.trainingSize} lançamento(s) analisado(s)" else "Receitas · ${c.trainingSize} lançamento(s) analisado(s)", Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (words.isEmpty()) Text("Ainda não há palavras repetidas o suficiente.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            words.forEach { (cat, ws) -> ManageItem(cat, ws.joinToString(", ") { (w, n) -> "$w ($n)" }) {} }
        }
        Text(
            "Dicionário inicial: ${dict.sections.size} seções, arquivo aberto assets/assistente/dicionario.txt. " +
                "As regras de cada função estão descritas em ASSISTENTE.md no código-fonte.",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 10.dp),
        )
    }
}
