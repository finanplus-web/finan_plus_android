// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.finanplus.BuildConfig
import com.finanplus.core.AppState
import com.finanplus.core.Money
import com.finanplus.core.report.Reports
import com.finanplus.data.Repo
import com.finanplus.export.PdfReport
import com.finanplus.ui.components.DateField
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.LocalToday
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SwitchRow
import com.finanplus.ui.components.br
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Exportar relatório em PDF: escolhe o período (atalhos ou datas), decide se inclui a lista de
 * lançamentos e salva onde o usuário quiser (seletor de arquivos do Android).
 */
@Composable
fun ReportExportSheet(s: AppState, initialFrom: LocalDate?, initialTo: LocalDate?, close: () -> Unit) {
    val p = Fin.c
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val hide = LocalPrivacy.current
    val first = remember(s.txs) { s.txs.minOfOrNull { it.date } }
    val last = remember(s.txs) { s.txs.maxOfOrNull { it.date } }
    val month = Reports.preset("mes", today, first, last)
    var from by remember { mutableStateOf<LocalDate?>(initialFrom ?: month.first) }
    var to by remember { mutableStateOf<LocalDate?>(initialTo ?: month.second) }
    var withTxs by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        com.finanplus.security.AppLock.endExternal()
        val a = from; val b = to
        if (uri == null || a == null || b == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val st = Repo.state.value
                    val report = Reports.build(st, a, b, LocalDate.now())
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                        PdfReport.write(out, report, st, PdfReport.Options(includeTransactions = withTxs, appVersion = BuildConfig.VERSION_NAME))
                    }
                }.isSuccess
            }
            busy = false
            if (ok) { close(); dialogs.notice("PDF salvo", "Relatório de ${a.br()} a ${b.br()} salvo. Abra pelo app de arquivos ou compartilhe por onde preferir.") }
            else dialogs.notice("Não foi possível gerar o PDF", "Tente de novo ou escolha outro local para salvar.")
        }
    }

    Text("Relatório em PDF", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(
        "Resumo com receitas, despesas e saldo, gráfico por categoria, evolução mensal, maiores despesas, contas, metas e a lista de lançamentos do período.",
        style = MaterialTheme.typography.bodyMedium, color = p.muted,
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("mes" to "Este mês", "anterior" to "Mês passado", "ano" to "Este ano", "12m" to "12 meses", "tudo" to "Tudo").forEach { (k, l) ->
            val (a, b) = Reports.preset(k, today, first, last)
            Pill(l, selected = from == a && to == b) { from = a; to = b }
        }
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField("De", from, { from = it }, Modifier.weight(1f))
        DateField("Até", to, { to = it }, Modifier.weight(1f))
    }
    SwitchRow("Incluir a lista de lançamentos", "Todos os lançamentos do período, inclusive pendentes", withTxs) { withTxs = it }

    val a = from; val b = to
    if (a != null && b != null && !b.isBefore(a)) {
        val r = remember(s, a, b) { Reports.build(s, a, b, today) }
        val m: (Long) -> String = { if (hide) "R$ ••••" else Money.format(it) }
        Text(
            "${r.txs.size} lançamento(s) · receitas ${m(r.income)} · despesas ${m(r.expense)} · saldo ${m(r.balance)}",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    Text(
        "O PDF mostra os valores mesmo com “Ocultar valores” ligado e não é criptografado: guarde em local seguro e cuidado ao compartilhar.",
        style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(bottom = 10.dp),
    )
    PrimaryButton(if (busy) "Gerando…" else "Gerar PDF") {
        val x = from; val y = to
        when {
            busy -> {}
            x == null || y == null -> dialogs.notice("Período incompleto", "Escolha as datas inicial e final.")
            y.isBefore(x) -> dialogs.notice("Período inválido", "A data final deve ser igual ou posterior à inicial.")
            else -> { com.finanplus.security.AppLock.allowExternalOnce(); save.launch(Reports.fileName(x, y)) }
        }
    }
}
