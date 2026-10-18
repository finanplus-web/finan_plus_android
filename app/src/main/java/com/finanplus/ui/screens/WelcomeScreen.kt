// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Boas-vindas: o que o Finan+ faz, em 4 páginas. Aparece na primeira abertura (sem lançamentos) e pode
 * ser revista em Ajustes › Sobre. Só apresenta: não pede dados nem permissões.
 */
object Welcome {
    /** "Ver apresentação" em Ajustes */
    val reopen = MutableStateFlow(false)
}

private data class Point(val icon: Ico, val text: String)
private data class Page(val icon: Ico, val eyebrow: String, val title: String, val lead: String, val points: List<Point>)

private val PAGES = listOf(
    Page(Ico.WALLET, "Bem-vindo", "Seu dinheiro, sem complicação.",
        "O Finan+ mostra quanto você tem, quanto vai pagar e quanto vai sobrar no mês.",
        listOf(
            Point(Ico.ADD, "Registre receitas e despesas em poucos toques"),
            Point(Ico.FORECAST, "Veja o saldo de hoje e o previsto até o fim do mês"),
            Point(Ico.CALENDAR, "Acompanhe vencimentos na lista ou no calendário"),
        )),
    Page(Ico.CARD, "Contas e cartões", "Tudo num lugar só.",
        "Contas, dinheiro e cartões de crédito juntos, com as faturas organizadas.",
        listOf(
            Point(Ico.BANK, "Várias contas, cada uma com seu saldo"),
            Point(Ico.CARD, "Cartões com fatura, limite, parcelas e vencimento"),
            Point(Ico.RECEIPT, "Leia uma fatura ou boleto em PDF para preencher o lançamento"),
            Point(Ico.REPEAT, "Salário, aluguel e assinaturas como recorrências"),
        )),
    Page(Ico.GOAL, "Planejamento", "Saiba para onde vai o dinheiro.",
        "Resumos claros e ferramentas para decidir com calma.",
        listOf(
            Point(Ico.REPORTS, "Resumo do mês por categoria, com relatório em PDF"),
            Point(Ico.LIMIT, "Limites por categoria, com aviso antes de passar"),
            Point(Ico.GOAL, "Metas para guardar dinheiro com prazo"),
            Point(Ico.ASSIST, "Assistente e simulador \"E se…?\", sem enviar nada para fora"),
        )),
    Page(Ico.SHIELD, "Privacidade", "Seus dados ficam com você.",
        "Sem cadastro, sem anúncios e sem servidor. Código aberto (GPL-3.0).",
        listOf(
            Point(Ico.SHIELD, "Tudo criptografado neste celular"),
            Point(Ico.FINGERPRINT, "Bloqueio com PIN ou digital e \"Ocultar valores\""),
            Point(Ico.WALLET, "Backup em arquivo, que também abre no Finan+ web e no Linux"),
        )),
)

/** [onDone] com `true` = "Já tenho um backup" (o app abre em Ajustes para restaurar). */
@Composable
fun WelcomeScreen(onDone: (restore: Boolean) -> Unit) {
    val p = Fin.c
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().padding(top = top + 8.dp, bottom = bottom + 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Finan+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (!last) TextButton({ onDone(false) }) { Text("Pular", color = p.accent, fontWeight = FontWeight.Bold) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i -> PageView(PAGES[i]) }
            // indicador de página (decorativo; a posição é anunciada no botão)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.Center) {
                PAGES.indices.forEach { i ->
                    val on = i == pager.currentPage
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (on) 22.dp else 8.dp).clip(CircleShape)
                        .background(if (on) p.accent else p.muted.copy(alpha = 0.35f)))
                }
            }
            Column(Modifier.padding(horizontal = 18.dp)) {
                if (last) {
                    PrimaryButton("Começar") { onDone(false) }
                    TextButton({ onDone(true) }, Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Já tenho um backup", color = p.accent, fontWeight = FontWeight.Bold)
                    }
                } else {
                    PrimaryButton("Continuar", Modifier.semantics { contentDescription = "Continuar, página ${pager.currentPage + 1} de ${PAGES.size}" }) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageView(pg: Page) {
    val p = Fin.c
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
        Spacer(Modifier.height(12.dp))
        IconTile(pg.icon, p.accent, p.accent2, size = 72.dp, iconSize = 36.dp)
        Spacer(Modifier.height(18.dp))
        Text(pg.eyebrow.uppercase(com.finanplus.ui.components.BR), style = MaterialTheme.typography.labelMedium, color = p.accent, fontWeight = FontWeight.Bold)
        Text(pg.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
        Text(pg.lead, style = MaterialTheme.typography.bodyLarge, color = p.muted, modifier = Modifier.padding(top = 6.dp))
        Glass(Modifier.padding(top = 18.dp), radius = 24.dp, padding = 14.dp) {
            pg.points.forEach { pt ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(p.accent2), contentAlignment = Alignment.Center) {
                        AppIcon(pt.icon, p.accent, size = 19.dp)
                    }
                    Text(pt.text, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (pg === PAGES.last()) Text(
            "Desenvolvido de forma independente por Juscelino Be.",
            style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}
