// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.finanplus.BuildConfig
import com.finanplus.MainActivity
import com.finanplus.core.AppState
import com.finanplus.core.Backup
import com.finanplus.core.BackupException
import com.finanplus.core.Csv
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Ops
import com.finanplus.core.Outcome
import com.finanplus.core.ThemeId
import com.finanplus.data.DevicePrefs
import com.finanplus.data.DeviceSettings
import com.finanplus.data.Repo
import com.finanplus.notify.Reminders
import com.finanplus.security.AppLock
import com.finanplus.security.Pin
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.ManageItem
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.SwitchRow
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

private const val MAX_BACKUP_BYTES = 30_000_000

private val THEME_SWATCH = mapOf(
    ThemeId.AUTO to (0xFFD9E5FF to 0xFF181A1F), ThemeId.LIGHT to (0xFFEEF4FF to 0xFF3A5FC8), ThemeId.MATERIAL_YOU to (0xFFDBE7FF to 0xFF0B57D0),
    ThemeId.OLED to (0xFF181A1F to 0xFF8AA8FF), ThemeId.TOKYO to (0xFF1A1B26 to 0xFF7AA2F7), ThemeId.NORD to (0xFF2E3440 to 0xFF88C0D0),
)

@Composable
fun SettingsScreen(s: AppState, dev: DeviceSettings, activity: MainActivity) {
    val nav = LocalNav.current
    val dialogs = LocalDialogs.current
    val p = Fin.c
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val scope = rememberCoroutineScope()
    val today = com.finanplus.ui.components.LocalToday.current

    // ---------- arquivos (Storage Access Framework: o usuário escolhe onde salvar) ----------
    // Grava com "wt" (trunca: sobrescrever um arquivo maior não deixa sobra no fim) e sempre avisa o resultado.
    fun saveTo(uri: android.net.Uri, what: String, bytes: () -> ByteArray) = scope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes()) } != null }.getOrDefault(false)
        }
        if (ok) dialogs.notice("$what salvo", if (what == "Backup") "O arquivo não é criptografado: guarde em local seguro." else "Arquivo gravado no local escolhido.")
        else dialogs.notice("Não foi possível salvar", "O arquivo não foi gravado (sem espaço ou local indisponível). Tente de novo ou escolha outro local.")
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) saveTo(uri, "Backup") {
            val meta = linkedMapOf<String, Any?>("app" to "Finan+", "version" to Backup.VERSION, "appVersion" to BuildConfig.VERSION_NAME, "createdAt" to Instant.now().toString())
            Backup.toJson(Repo.state.value, meta).toByteArray()
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) saveTo(uri, "CSV") { Csv.build(Repo.state.value).toByteArray() }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // lê no máximo o limite + 1 byte (nunca o arquivo inteiro antes de checar) e interpreta fora da thread da tela
            val text: String? = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { ins ->
                        val buf = java.io.ByteArrayOutputStream(); val chunk = ByteArray(64 * 1024); var total = 0
                        while (true) { val r = ins.read(chunk); if (r < 0) break; total += r; if (total > MAX_BACKUP_BYTES) return@use null; buf.write(chunk, 0, r) }
                        buf.toString(Charsets.UTF_8.name())
                    }
                }.getOrNull()
            }
            if (text == null) { dialogs.notice("Não foi possível restaurar", "Arquivo grande demais (máximo 30 MB) ou ilegível. Nada foi alterado."); return@launch }
            val n = withContext(Dispatchers.Default) {
                try { Backup.parse(text) } catch (e: BackupException) { null } catch (e: Throwable) { null } // inclui falta de memória
            }
            if (n == null) { dialogs.notice("Não foi possível restaurar", "Arquivo de backup inválido ou danificado. Nada foi alterado."); return@launch }
            val st = n.state
            val bad = n.dropped.total
            dialogs.confirm(
                "Revisar restauração",
                "Backup com ${st.txs.size} lançamentos, ${st.accounts.size} contas, ${st.cards.size} cartões e ${st.goals.size} metas." +
                    (if (bad > 0) "\n$bad item(ns) inválido(s) será(ão) ignorado(s)." else "") +
                    (if (n.newer) "\nEste backup foi feito por uma versão mais nova do Finan+: dados de recursos que esta versão não conhece serão ignorados." else "") +
                    "\nSubstituir os dados atuais? O PIN e o bloqueio deste aparelho são mantidos.",
                ok = "Substituir", danger = true,
            ) { Repo.replace(Finance.generateRecurring(st, LocalDate.now()).first) }
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.update { it.copy(notifications = granted) }
        if (!granted) dialogs.notice("Notificações desativadas", "Para receber avisos de vencimento, permita as notificações do Finan+ nas configurações do Android.")
    }

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item { PageTitle("Configurações", "Ajustes", "Tudo fica salvo e criptografado neste aparelho.") }

        item {
            Collapsible("Aparência", "Tema: ${s.theme.label}") {
                ThemeId.entries.chunked(2).forEach { row ->
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { t ->
                            val on = s.theme == t
                            val (a, b) = THEME_SWATCH.getValue(t)
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(p.accent2)
                                    .then(if (on) Modifier.background(p.accent.copy(alpha = 0.18f)) else Modifier)
                                    .clickable(role = Role.RadioButton) { Repo.update { it.copy(theme = t) } }
                                    .semantics { stateDescription = if (on) "selecionado" else "" }.padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.label, fontWeight = FontWeight.Bold)
                                    if (on) { Spacer(Modifier.width(6.dp)); com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.CHECK, p.accent, size = 18.dp) }
                                }
                                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(18.dp, 8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(a)).border(1.dp, p.muted.copy(alpha = 0.5f), CircleShape))
                                    Box(Modifier.size(18.dp, 8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(b)).border(1.dp, p.muted.copy(alpha = 0.5f), CircleShape))
                                }
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Text("Material You usa as cores do papel de parede a partir do Android 12.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
        }

        item {
            Collapsible("Privacidade e segurança", (if (dev.lockEnabled) "Bloqueio ativo" else "Bloqueio desativado") + if (s.privacy) " · valores ocultos" else "") {
                Text("Bloqueio do app", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        dev.hasPin && dev.biometric -> "Ativo: PIN ou digital"
                        dev.hasPin -> "Ativo: PIN"
                        dev.biometric -> "Ativo: digital (alternativa: bloqueio de tela do Android)"
                        else -> "Desativado. Defina um PIN, ative a digital, ou os dois."
                    },
                    style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (dev.hasPin) "Remover PIN" else "Definir PIN", Modifier.weight(1f)) {
                        if (dev.hasPin) dialogs.input("Remover PIN", "Digite o PIN atual para confirmar.", "PIN atual", password = true) { pin ->
                            val wait = AppLock.waitSeconds()
                            if (wait > 0) { dialogs.notice("Aguarde", "Muitas tentativas erradas. Tente de novo em $wait s."); return@input }
                            AppLock.registerAttempt() // mesmo limite de tentativas da tela de bloqueio
                            scope.launch {
                                if (Pin.verify(pin, dev.pinHash)) { AppLock.resetFails(); prefs.update { it.copy(pinHash = "") } }
                                else dialogs.notice("Não foi possível remover", "PIN incorreto.")
                            }
                        } else dialogs.input("Definir PIN", "Use de 4 a 8 números.", "Novo PIN", password = true) { p1 ->
                            if (!Pin.isValidFormat(p1)) { dialogs.notice("PIN inválido", "Use de 4 a 8 números."); return@input }
                            dialogs.input("Confirmar PIN", "Digite o PIN de novo.", "Repita o PIN", password = true) { p2 ->
                                if (p1 != p2) { dialogs.notice("PIN não definido", "Os PINs não conferem."); return@input }
                                scope.launch {
                                    val h = Pin.hash(p1)
                                    val hadWidgetValues = prefs.value.widgetValues
                                    // com bloqueio, o widget passa a ocultar os valores (senão o saldo aparece na tela inicial sem PIN)
                                    prefs.update { it.copy(pinHash = h, widgetValues = false) }
                                    com.finanplus.widget.BalanceWidget.refresh(ctx)
                                    dialogs.notice("PIN ativado", "O PIN será pedido ao abrir o app." + if (hadWidgetValues) " Os valores do widget foram ocultados; dá para mostrar de novo em “Notificações e widget”." else "")
                                }
                            }
                        }
                    }
                }
                if (activity.canUseBiometric()) {
                    SwitchRow(
                        "Desbloquear com digital",
                        if (dev.hasPin) "Use a digital ou o PIN, o que preferir" else "Funciona sozinha ou junto com o PIN",
                        dev.biometric,
                    ) { v ->
                        // Confirma com a digital antes de ligar (garante que funciona) ou de desligar.
                        activity.promptBiometric(
                            title = if (v) "Ativar desbloqueio por digital" else "Desativar desbloqueio por digital",
                            withPinFallback = false,
                            onSuccess = {
                                prefs.update { it.copy(biometric = v, widgetValues = if (v) false else it.widgetValues) }
                                scope.launch { com.finanplus.widget.BalanceWidget.refresh(ctx) }
                            },
                            onFail = { dialogs.notice("Digital", it) },
                        )
                    }
                } else if (dev.biometric) {
                    SwitchRow("Desbloquear com digital", "Nenhuma biometria forte (digital ou rosto 3D) disponível no aparelho agora", true) { prefs.update { it.copy(biometric = false) } }
                } else {
                    Text("Para usar a digital, cadastre uma em Configurações do Android › Segurança.", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(vertical = 6.dp))
                }
                SwitchRow("Ocultar valores", "Esconde os valores em reais na tela, no widget e nas notificações", s.privacy) { v -> Repo.update { it.copy(privacy = v) } }
                if (dev.lockEnabled) SelectField(
                    "Pedir desbloqueio ao voltar ao app",
                    AppLock.AUTOLOCK_OPTIONS.map {
                        it to when (it) {
                            AppLock.AUTOLOCK_IMMEDIATE -> "Imediatamente"
                            AppLock.AUTOLOCK_ON_OPEN -> "Só ao abrir o app"
                            else -> "Após $it minuto${if (it > 1) "s" else ""} em segundo plano"
                        }
                    },
                    dev.autoLock, { v -> prefs.update { it.copy(autoLock = v) } },
                )
                SwitchRow("Bloquear capturas de tela", "Também esconde o conteúdo na lista de apps recentes", dev.secureScreen) { v -> prefs.update { it.copy(secureScreen = v) } }
                Text(
                    "Os dados ficam criptografados (AES-256) com uma chave guardada no Android Keystore deste aparelho. O PIN e a biometria impedem o acesso pelo app; o PIN nunca vai para o backup.",
                    style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            Collapsible("Notificações e widget", if (dev.notifications && Reminders.canNotify(ctx)) "Avisos de vencimento ligados" else "Avisos de vencimento desligados") {
                SwitchRow("Avisar vencimentos", "Contas a pagar, valores a receber e faturas, por volta das 9h", dev.notifications && Reminders.canNotify(ctx)) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else prefs.update { it.copy(notifications = v) }
                }
                SwitchRow("Mostrar valores no widget", "Desligado, o widget mostra “R$ ••••”", dev.widgetValues) { v ->
                    prefs.update { it.copy(widgetValues = v) }
                    scope.launch { com.finanplus.widget.BalanceWidget.refresh(ctx) }
                }
            }
        }

        item {
            Collapsible("Assistente", "${listOf(dev.assistCategory, dev.assistTips, dev.assistAsk).count { it }} de 3 funções ligadas") { AssistantSettings(s, dev) }
        }

        item {
            Collapsible("Contas e cartões", "${s.accounts.size} conta(s) · ${s.cards.size} cartão(ões)") {
                s.accounts.forEach { a ->
                    ManageItem(a.name, "Saldo ${if (s.privacy) "R$ ••••" else Money.format(Finance.accountBalance(s, a))}") { Pill("Editar") { nav.open(Sheet.AccountEdit(a.id)) } }
                }
                s.cards.forEach { c ->
                    val st = Finance.cardStatus(s, c, today)
                    val v = if (s.privacy) "R$ ••••" else Money.format(c.limit)
                    val u = if (s.privacy) "R$ ••••" else Money.format(st.used)
                    ManageItem("Cartão ${c.name}", "Limite $v · usado $u · fecha dia ${c.close} · vence dia ${c.due}") { Pill("Editar") { nav.open(Sheet.CardEdit(c.id)) } }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Conta", Modifier.weight(1f), icon = com.finanplus.ui.components.Ico.ADD) { nav.open(Sheet.AccountEdit()) }
                    Pill("Cartão", Modifier.weight(1f), icon = com.finanplus.ui.components.Ico.ADD) { nav.open(Sheet.CardEdit()) }
                }
            }
        }

        item {
            Collapsible("Recorrências", if (s.recurring.isEmpty()) "Nenhuma recorrência cadastrada" else "${s.recurring.size} recorrência(s) cadastrada(s)") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Pill("Nova", icon = com.finanplus.ui.components.Ico.ADD) { nav.open(Sheet.RecurringEdit()) } }
                if (s.recurring.isEmpty()) Text("Você também pode marcar “Repetir mensalmente” ao criar um lançamento.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                s.recurring.forEach { r ->
                    val where = if (r.cardId.isNotEmpty()) "Cartão " + (s.card(r.cardId)?.name ?: "") else s.account(r.accountId)?.name ?: ""
                    val v = if (s.privacy) "R$ ••••" else Money.format(r.value)
                    ManageItem(r.desc, "${if (r.kind == Kind.INCOME) "Receita" else "Despesa"} · $v · dia ${r.day} · ${r.category} · $where${if (r.active) "" else " · pausada"}") {
                        Pill("Editar") { nav.open(Sheet.RecurringEdit(r.id)) }
                    }
                }
            }
        }

        item {
            Collapsible("Limites mensais", if (s.limits.isEmpty()) "Nenhum limite definido" else "${s.limits.size} limite(s) definido(s)") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Pill("Adicionar", icon = com.finanplus.ui.components.Ico.ADD) { nav.open(Sheet.LimitEdit()) } }
                s.limits.forEach { (c, v) ->
                    ManageItem(c, "${if (s.privacy) "R$ ••••" else Money.format(v)} por mês") { Pill("Editar") { nav.open(Sheet.LimitEdit(c)) } }
                }
            }
        }

        item {
            var newCat by rememberSaveable { mutableStateOf("") }
            var newKind by remember { mutableStateOf(Kind.EXPENSE) }
            Collapsible("Categorias", "${s.cats.expense.size + s.cats.income.size} categorias cadastradas") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field("Nova categoria", newCat, { newCat = it }, Modifier.weight(1.3f), maxLength = 40)
                    SelectField("Tipo", listOf(Kind.EXPENSE to "Despesa", Kind.INCOME to "Receita"), newKind, { newKind = it }, Modifier.weight(1f))
                }
                PrimaryButton("Adicionar categoria") {
                    when (val o = Ops.addCategory(Repo.state.value, newKind, newCat)) {
                        is Outcome.Err -> dialogs.notice(o.title, o.message)
                        is Outcome.Ok -> { Repo.replace(o.state); newCat = "" }
                    }
                }
                listOf(Kind.EXPENSE to "Despesas", Kind.INCOME to "Receitas").forEach { (k, label) ->
                    Eyebrow(label, Modifier.padding(top = 14.dp, bottom = 4.dp))
                    s.cats.of(k).forEach { c ->
                        ManageItem(c, if (k == Kind.EXPENSE) "Despesa" else "Receita") {
                            Pill("Renomear") {
                                dialogs.input("Renomear categoria", "Lançamentos, recorrências e limites de “$c” passam a usar o novo nome.", "Novo nome", c) { n ->
                                    Repo.commit(Ops.renameCategory(Repo.state.value, k, c, n))?.let { dialogs.notice(it.title, it.message) }
                                }
                            }
                            Pill("Excluir", danger = true) {
                                when (val chk = Ops.checkDeleteCategory(Repo.state.value, k, c)) {
                                    is Outcome.Err -> dialogs.notice(chk.title, chk.message)
                                    is Outcome.Ok -> {
                                        val used = Ops.categoryUseCount(Repo.state.value, k, c)
                                        val msg = if (used > 0) "Excluir “$c” da lista de categorias? $used lançamento(s) antigo(s) continuará(ão) com essa categoria no histórico." else "Excluir a categoria “$c”?"
                                        dialogs.confirm("Excluir categoria", msg, ok = "Excluir", danger = true) { Repo.replace(Ops.deleteCategory(Repo.state.value, k, c)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Collapsible("Dados", "Backup, restauração, CSV e relatório em PDF") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Exportar CSV", Modifier.weight(1f)) { AppLock.allowExternalOnce(); exportCsv.launch("lancamentos-${LocalDate.now()}.csv") }
                    Pill("Backup JSON", Modifier.weight(1f)) { AppLock.allowExternalOnce(); exportJson.launch("backup-finan-plus-${LocalDate.now()}.json") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Relatório em PDF", Modifier.weight(1f)) { nav.open(Sheet.ReportPdf()) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Restaurar", Modifier.weight(1f)) { AppLock.allowExternalOnce(); importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    Pill("Apagar tudo", Modifier.weight(1f), danger = true) {
                        dialogs.confirm("Apagar todos os dados", "Apagar TODOS os dados deste aparelho, inclusive o PIN? Faça um backup antes.", ok = "Apagar tudo", danger = true) {
                            Repo.wipe(); AppLock.unlock()
                        }
                    }
                }
                Text("O backup JSON é compatível com o Finan+ web: dá para levar os dados de um para o outro.", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }

        item {
            Collapsible("Sobre", "Conheça o Finan+ · versão ${BuildConfig.VERSION_NAME}") {
                AboutText()
            }
        }
    }
}

/** Cartão de Ajustes que abre e fecha com o botão +/− (todas as seções usam). */
@Composable
private fun Collapsible(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val p = Fin.c
    Glass(Modifier.padding(bottom = 14.dp), radius = 25.dp, padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.semantics { stateDescription = if (open) "expandido" else "recolhido" }.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(summary, style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(p.accent2), contentAlignment = Alignment.Center) {
                com.finanplus.ui.components.AppIcon(if (open) com.finanplus.ui.components.Ico.REMOVE else com.finanplus.ui.components.Ico.ADD, p.text, size = 22.dp)
            }
        }
        if (open) Column(Modifier.padding(start = 17.dp, end = 17.dp, bottom = 17.dp), content = content)
    }
}

@Composable
private fun AboutText() {
    val p = Fin.c
    val paras = listOf(
        "Finan+ é um aplicativo para gerenciamento financeiro pessoal, desenvolvido com foco em simplicidade, privacidade, leveza e funcionamento offline.",
        "O aplicativo permite organizar receitas, despesas, contas, cartões, categorias, limites mensais, metas e lançamentos recorrentes, além de acompanhar saldos e relatórios financeiros.",
        "Esta versão é nativa para Android, construída com Kotlin e Jetpack Compose. Os dados ficam no aparelho, criptografados com AES-256 e chave no Android Keystore, sem conta, cadastro ou servidor.",
        "Inclui widget de saldo na tela inicial, avisos de vencimento, desbloqueio por PIN ou biometria e backup em JSON compatível com o Finan+ web.",
        "O assistente (sugestão de categoria, resumo do mês, dicas de economia e perguntas rápidas) funciona inteiro no aparelho, sem internet e sem modelo de IA externo: são regras e um classificador simples, com código aberto e explicação em cada resposta.",
        "A interface combina conceitos do Material 3 com elementos visuais inspirados em Liquid Glass, com os temas Material You, OLED, Tokyo Night e Nord.",
    )
    paras.forEach { Text(it, color = p.muted, modifier = Modifier.padding(bottom = 12.dp)) }
    Text("Privacidade em primeiro lugar: seus dados financeiros permanecem no seu dispositivo.", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
    Text("Desenvolvimento", style = MaterialTheme.typography.titleMedium)
    Text(
        "Finan+ é um projeto independente desenvolvido de forma colaborativa com auxílio de inteligência artificial. A concepção, as decisões de produto, os testes e o direcionamento da experiência são realizados por Juscelino Be, autor e idealizador do projeto, enquanto a inteligência artificial auxilia na implementação, revisão e evolução do código.",
        color = p.muted, modifier = Modifier.padding(vertical = 8.dp),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.accent2).padding(17.dp)) {
        Eyebrow("Idealizado e desenvolvido por")
        Text("Juscelino Be", style = MaterialTheme.typography.titleLarge)
    }
    LicenseNotice()
}

/** Avisos legais exigidos pela GPL (seção 5d) e o texto completo da licença, que vai dentro do app. */
@Composable
private fun LicenseNotice() {
    val p = Fin.c
    var full by rememberSaveable { mutableStateOf(false) }
    Text("Licença", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
    Text(
        "Finan+ — Copyright (C) 2026 Juscelino Be.\n\n" +
            "Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob os termos da Licença Pública Geral GNU (GNU GPL), " +
            "publicada pela Free Software Foundation, na versão 3 da licença ou (a seu critério) qualquer versão posterior.\n\n" +
            "Este programa é distribuído na esperança de que seja útil, mas SEM NENHUMA GARANTIA, nem mesmo a garantia implícita de " +
            "COMERCIABILIDADE ou de ADEQUAÇÃO A UMA FINALIDADE ESPECÍFICA. Veja a licença completa para mais detalhes.",
        color = p.muted, modifier = Modifier.padding(vertical = 8.dp),
    )
    Pill(if (full) "Ocultar licença completa" else "Ver licença completa (GNU GPL v3)") { full = !full }
    if (full) LicenseText("licenca/LICENSE.txt", "https://www.gnu.org/licenses/gpl-3.0.html")

    // componente de terceiros: ícones Material Symbols
    var icons by rememberSaveable { mutableStateOf(false) }
    Text(
        "Ícones: Material Symbols, © Google, sob a Licença Apache 2.0 (compatível com a GPL v3).",
        color = p.muted, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
    Pill(if (icons) "Ocultar licença dos ícones" else "Ver licença dos ícones (Apache 2.0)") { icons = !icons }
    if (icons) LicenseText("licenca/APACHE-2.0.txt", "https://www.apache.org/licenses/LICENSE-2.0")
}

@Composable
private fun LicenseText(asset: String, fallbackUrl: String) {
    val p = Fin.c
    val ctx = LocalContext.current
    val text = remember(asset) {
        runCatching { ctx.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrElse { "Texto da licença: $fallbackUrl" }
    }
    Text(
        text, color = p.muted, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.accent2.copy(alpha = 0.5f)).padding(12.dp),
    )
}
