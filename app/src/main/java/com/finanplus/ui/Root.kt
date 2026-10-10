// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui

import android.Manifest
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finanplus.MainActivity
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Ops
import com.finanplus.core.Tx
import com.finanplus.data.DevicePrefs
import com.finanplus.data.DeviceSettings
import com.finanplus.data.LoadProblem
import com.finanplus.data.Repo
import com.finanplus.notify.Reminders
import com.finanplus.security.AppLock
import com.finanplus.ui.components.AppBackground
import com.finanplus.ui.components.DialogHost
import com.finanplus.ui.components.Dialogs
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.br
import com.finanplus.ui.screens.HomeScreen
import com.finanplus.ui.screens.LockScreen
import com.finanplus.ui.screens.MovesScreen
import com.finanplus.ui.screens.ReportsScreen
import com.finanplus.ui.screens.SettingsScreen
import com.finanplus.ui.screens.SheetHost
import com.finanplus.ui.theme.Fin
import com.finanplus.ui.theme.FinanTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab(val label: String, val icon: com.finanplus.ui.components.Ico) {
    HOME("Início", com.finanplus.ui.components.Ico.HOME), MOVES("Lançamentos", com.finanplus.ui.components.Ico.MOVES),
    REPORTS("Relatórios", com.finanplus.ui.components.Ico.REPORTS), PREFS("Ajustes", com.finanplus.ui.components.Ico.SETTINGS),
}

/** Folhas (formulários) abertas. Serializable para sobreviver a girar a tela e ao bloqueio. */
sealed interface Sheet : java.io.Serializable {
    /** [date]: data inicial de um lançamento novo (ex.: o dia escolhido no calendário) */
    data class TxEdit(val kind: Kind, val id: String? = null, val date: LocalDate? = null) : Sheet
    /** [name]/[target]/[monthly]: valores iniciais de uma meta nova (ex.: vindos do simulador) */
    data class GoalEdit(val id: String? = null, val name: String = "", val target: Long = 0, val monthly: Long = 0) : Sheet
    data class AccountEdit(val id: String? = null) : Sheet
    data class CardEdit(val id: String? = null) : Sheet
    data class RecurringEdit(val id: String? = null) : Sheet
    data class LimitEdit(val category: String? = null) : Sheet
    data class PayInvoice(val cardId: String) : Sheet
    /** assistente: perguntas, resumo e todas as dicas */
    data object Assistant : Sheet
    /** exportar relatório em PDF (período inicial opcional) */
    data class ReportPdf(val from: java.time.LocalDate? = null, val to: java.time.LocalDate? = null) : Sheet
    /** Lançamentos › Lista: período livre e situação (o ícone de ajuste ao lado do mês) */
    data object MovesFilters : Sheet
    /** simulador "E se…?" ([scenario]: save, buy, income, debt; null = escolher) */
    data class Simulator(val scenario: String? = null) : Sheet
}

/** Filtros da aba Lançamentos (o período também vale para Relatórios). */
class Filters {
    var from by mutableStateOf<LocalDate?>(LocalDate.now().withDayOfMonth(1))
    var to by mutableStateOf<LocalDate?>(LocalDate.now().let { it.withDayOfMonth(it.lengthOfMonth()) })
    var query by mutableStateOf("")
    var kind by mutableStateOf<Kind?>(null)
    /** null = todos, true = realizados, false = pendentes */
    var paid by mutableStateOf<Boolean?>(null)
    fun inRange(t: Tx) = (from == null || !t.date.isBefore(from)) && (to == null || !t.date.isAfter(to))
    fun thisMonth() { val n = LocalDate.now(); from = n.withDayOfMonth(1); to = n.withDayOfMonth(n.lengthOfMonth()) }
}

/** Modo de exibição da aba Lançamentos. */
enum class MovesView { LIST, CALENDAR }

class Nav {
    var tab by mutableStateOf(Tab.HOME)
    var sheet by mutableStateOf<Sheet?>(null)
    val filters = Filters()
    /** Lançamentos: lista ou calendário */
    var movesView by mutableStateOf(MovesView.LIST)
    /** calendário: mês mostrado e dia escolhido (null = nenhum) */
    var calMonth by mutableStateOf(java.time.YearMonth.now())
    var calDay by mutableStateOf<LocalDate?>(LocalDate.now())
    fun open(s: Sheet) { sheet = s }

    companion object {
        /** Aba, folha aberta e filtros sobrevivem a girar a tela, mudar a fonte e ao bloqueio do app. */
        val Saver = listSaver<Nav, Any?>(
            save = { n -> listOf(n.tab.name, n.sheet, n.filters.from, n.filters.to, n.filters.query, n.filters.kind?.name, n.filters.paid, n.movesView.name, n.calMonth, n.calDay) },
            restore = { l ->
                Nav().apply {
                    tab = runCatching { Tab.valueOf(l[0] as String) }.getOrDefault(Tab.HOME)
                    sheet = l[1] as Sheet?
                    filters.from = l[2] as LocalDate?
                    filters.to = l[3] as LocalDate?
                    filters.query = l[4] as String? ?: ""
                    filters.kind = (l[5] as String?)?.let { runCatching { Kind.valueOf(it) }.getOrNull() }
                    filters.paid = l[6] as Boolean?
                    movesView = runCatching { MovesView.valueOf(l.getOrNull(7) as String) }.getOrDefault(MovesView.LIST)
                    calMonth = l.getOrNull(8) as java.time.YearMonth? ?: java.time.YearMonth.now()
                    calDay = if (l.size > 9) l[9] as LocalDate? else LocalDate.now()
                }
            },
        )
    }
}

val LocalNav = staticCompositionLocalOf { Nav() }

/** Padding inferior para o conteúdo não ficar sob a barra de navegação flutuante. */
@Composable
fun bottomSpace(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 110.dp

@Composable
fun screenPadding() = PaddingValues(start = 18.dp, end = 18.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp, bottom = bottomSpace())

@Composable
fun FinanRoot(activity: MainActivity) {
    val s by Repo.state.collectAsStateWithLifecycle()
    val dev by DevicePrefs.get(activity).flow.collectAsStateWithLifecycle()
    val locked by AppLock.locked.collectAsStateWithLifecycle()
    FinanTheme(s.theme) {
        val dark = Fin.c.dark
        LaunchedEffect(dark) {
            val t = android.graphics.Color.TRANSPARENT
            val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        val dialogs = remember { Dialogs() }
        val nav = rememberSaveable(saver = Nav.Saver) { Nav() }
        // guarda o estado "salvável" da tela principal enquanto ela está fora da composição (app bloqueado):
        // ao desbloquear, a folha aberta e o que foi digitado continuam lá
        val holder = rememberSaveableStateHolder()
        val showLock = locked && dev.lockEnabled
        // Bloqueou: fecha qualquer diálogo aberto. Um AlertDialog é uma janela própria e ficaria utilizável por cima
        // da tela de bloqueio (ex.: "Definir PIN" deixado aberto permitia trocar o PIN sem saber o atual).
        LaunchedEffect(showLock) { if (showLock) dialogs.close() }
        CompositionLocalProvider(LocalDialogs provides dialogs, LocalPrivacy provides s.privacy) {
            AppBackground {
                // Bloqueado: o conteúdo do app nem é composto (nada acessível por trás).
                if (showLock) LockScreen(activity, dev) else holder.SaveableStateProvider("main") { MainScaffold(s, dev, activity, nav) }
            }
            if (!showLock) DialogHost(dialogs)
            // acesso pela rede: "Permitir <navegador>?" aparece em qualquer tela, só com o app desbloqueado
            if (!showLock) com.finanplus.lan.LanPairPrompt(dialogs)
        }
    }
}

@Composable
private fun MainScaffold(s: AppState, dev: DeviceSettings, activity: MainActivity, nav: Nav) {
    // Voltar: fecha a folha aberta (o próprio ModalBottomSheet trata) e depois volta para o Início.
    BackHandler(enabled = nav.tab != Tab.HOME && nav.sheet == null) { nav.tab = Tab.HOME }

    // Android 13+: pede a permissão de notificação na primeira abertura (uma vez só).
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> prefs.update { it.copy(notifications = granted) } }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && dev.notifications && !Reminders.canNotify(ctx) && !prefs.askedNotifications) {
            prefs.askedNotifications = true
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // "hoje" dinâmico: muda na meia-noite, ao voltar ao app e se a data do aparelho mudar
    val today = com.finanplus.ui.components.rememberToday()
    LaunchedEffect(today) {
        Repo.runRecurring(today) // virou o mês com o app aberto
        // o filtro estava no mês anterior inteiro (o padrão "este mês" de ontem): acompanha a virada
        val prev = today.minusMonths(1)
        val f = nav.filters
        if (f.from == prev.withDayOfMonth(1) && f.to == prev.withDayOfMonth(prev.lengthOfMonth())) f.thisMonth()
        // calendário parado em "hoje" (ontem): acompanha a virada do dia e do mês
        val yesterday = today.minusDays(1)
        if (nav.calDay == yesterday && nav.calMonth == java.time.YearMonth.from(yesterday)) {
            nav.calDay = today; nav.calMonth = java.time.YearMonth.from(today)
        }
    }
    var reaskProblem by remember { mutableIntStateOf(0) }
    // Abas lado a lado: deslizar para o lado passa para a vizinha (Início › Lançamentos › Relatórios › Ajustes).
    // Os botões da barra inferior continuam funcionando; os dois caminhos mudam o mesmo nav.tab.
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = nav.tab.ordinal) { Tab.entries.size }
    // tocou num botão da barra (ou "Voltar" para o Início): leva o pager até a aba
    LaunchedEffect(nav.tab) { if (pager.targetPage != nav.tab.ordinal) pager.animateScrollToPage(nav.tab.ordinal) }
    // deslizou e parou numa aba: ela vira a aba atual
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page ->
            if (!pager.isScrollInProgress && nav.tab.ordinal != page) nav.tab = Tab.entries[page]
        }
    }
    CompositionLocalProvider(LocalNav provides nav, com.finanplus.ui.components.LocalToday provides today) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.foundation.pager.HorizontalPager(
                pager, Modifier.fillMaxSize(), key = { Tab.entries[it].name },
                // folha (formulário) aberta: sem troca de aba por gesto por trás dela
                userScrollEnabled = nav.sheet == null,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    when (Tab.entries[page]) {
                        Tab.HOME -> HomeScreen(s)
                        Tab.MOVES -> MovesScreen(s)
                        Tab.REPORTS -> ReportsScreen(s)
                        Tab.PREFS -> SettingsScreen(s, dev, activity)
                    }
                }
            }
            // Faixa atrás da barra de status: o conteúdo rolado não fica sob os ícones do sistema.
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Fin.c.bg))
            BottomNav(nav, Modifier.align(Alignment.BottomCenter))
            SaveErrorBanner(Modifier.align(Alignment.TopCenter)) { reaskProblem++ }
        }
        nav.sheet?.let { sh -> key(sh) { SheetHost(s, sh) { nav.sheet = null } } }
        ProblemNotice(reaskProblem)
    }
}

@Composable
private fun BottomNav(nav: Nav, modifier: Modifier) {
    val p = Fin.c
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp).widthIn(max = 536.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
            .background(p.surface.compositeOver(p.bg)).border(1.dp, p.border, RoundedCornerShape(28.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Tab.HOME, nav, Modifier.weight(1f))
        NavItem(Tab.MOVES, nav, Modifier.weight(1f))
        Box(
            Modifier.padding(horizontal = 4.dp).size(54.dp).shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(p.accent)
                .clickable(role = Role.Button) { nav.open(Sheet.TxEdit(Kind.EXPENSE)) }.semantics { contentDescription = "Novo lançamento" },
            contentAlignment = Alignment.Center,
        ) { com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.ADD, p.onAccent, size = 28.dp) }
        NavItem(Tab.REPORTS, nav, Modifier.weight(1f))
        NavItem(Tab.PREFS, nav, Modifier.weight(1f))
    }
}

@Composable
private fun NavItem(t: Tab, nav: Nav, modifier: Modifier) {
    val p = Fin.c
    val on = nav.tab == t
    Column(
        modifier.height(58.dp).clip(RoundedCornerShape(20.dp)).background(if (on) p.accent.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Tab) { nav.tab = t }.semantics { selected = on },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        // aba selecionada usa o ícone preenchido (estilo Material)
        com.finanplus.ui.components.AppIcon(t.icon, if (on) p.accent else p.muted, size = 22.dp, filled = on)
        Text(t.label, fontSize = 10.sp, color = if (on) p.accent else p.muted, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Linha de lançamento: toque abre a edição; o círculo marca como pago/recebido. */
@Composable
fun TxRow(t: Tx, s: AppState, onToggle: () -> Unit, onOpen: () -> Unit) {
    val p = Fin.c
    val today = com.finanplus.ui.components.LocalToday.current
    val status = when {
        t.isCard -> "Cartão"
        t.paid -> if (t.kind == Kind.INCOME) "Recebido" else "Pago"
        t.date.isBefore(today) -> "Em atraso"
        else -> if (t.kind == Kind.INCOME) "A receber" else "A pagar"
    }
    val where = if (t.isCard) "Cartão " + (s.card(t.cardId)?.name ?: "") else s.account(t.accountId)?.name ?: "Conta"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.accent2), contentAlignment = Alignment.Center) {
                // ícone da categoria; sem ícone próprio, mostra a inicial (ex.: "E" de Empréstimo)
                com.finanplus.ui.components.CategoryGlyph(t.category, p.text)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.desc, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // duas linhas fixas: data e situação nunca são cortadas, mesmo com nomes longos
                Text("${t.category} · $where", style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${t.date.br()} · $status", style = MaterialTheme.typography.bodySmall, color = if (status == "Em atraso") p.red else p.muted, maxLines = 1)
            }
            MoneyText(t.value, prefix = if (t.kind == Kind.EXPENSE) "− " else "+ ", color = if (t.kind == Kind.EXPENSE) p.red else p.green,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold))
        }
        // compra no cartão e pagamento de fatura não alternam pago/pendente (ver Ops.canTogglePaid)
        if (!Ops.canTogglePaid(t)) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.CARD, p.muted, size = 20.dp) }
        else {
            val label = (if (t.kind == Kind.INCOME) "Recebida" else "Paga") + ": ${t.desc}"
            Box(
                Modifier.padding(end = 6.dp).size(48.dp).clip(CircleShape)
                    .toggleable(value = t.paid, role = Role.Checkbox, onValueChange = { onToggle() })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (t.paid) p.green else androidx.compose.ui.graphics.Color.Transparent)
                        .border(2.dp, if (t.paid) p.green else p.muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (t.paid) com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.CHECK, p.onAccent, size = 18.dp) }
            }
        }
    }
}


/** Aviso fixo no topo quando as alterações não estão sendo salvas (falha de gravação ou arquivo pendente de decisão). */
@Composable
private fun SaveErrorBanner(modifier: Modifier, onResolve: () -> Unit) {
    val err by Repo.saveError.collectAsStateWithLifecycle()
    val problem by Repo.problem.collectAsStateWithLifecycle()
    val msg = err ?: return
    val p = Fin.c
    Row(
        modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp, start = 12.dp, end = 12.dp)
            .widthIn(max = 600.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.red.copy(alpha = 0.16f).compositeOver(p.bg))
            .border(1.dp, p.red.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.WARNING, p.red, size = 20.dp)
        Text(msg, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodySmall, color = p.text)
        if (problem != null) com.finanplus.ui.components.Pill("Resolver", onClick = onResolve)
    }
}

/**
 * Problemas ao abrir os dados. Nada é gravado por cima do arquivo até o usuário escolher:
 * "Tentar de novo" (erro do Keystore), "Começar do zero" (com cópia cifrada guardada) ou salvar a cópia.
 * "Decidir depois" fecha o aviso; o aviso fixo no topo continua e reabre esta pergunta.
 */
@Composable
private fun ProblemNotice(reask: Int) {
    val problem by Repo.problem.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val scope = rememberCoroutineScope()
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        com.finanplus.security.AppLock.endExternal()
        val p = Repo.problem.value
        if (uri != null && p is LoadProblem.Invalid) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(p.raw.toByteArray()) } != null }.getOrDefault(false)
            if (ok) Repo.startFresh() else dialogs.notice("Não foi possível salvar", "A cópia não foi gravada. Escolha outro local e tente de novo.")
        }
    }
    LaunchedEffect(problem, reask) {
        when (val p = problem) {
            null -> {}
            is LoadProblem.KeystoreError -> dialogs.confirm(
                "Chave de criptografia indisponível",
                "O Android não liberou a chave que protege seus dados agora (isso pode acontecer logo após ligar ou atualizar o aparelho). " +
                    "Seus dados continuam guardados e nada foi alterado. Tente de novo; se continuar, reinicie o aparelho.",
                ok = "Tentar de novo", cancel = "Outras opções",
                onCancel = {
                    dialogs.confirm(
                        "Outras opções",
                        "Começar do zero deixa o app vazio. O arquivo atual fica guardado, cifrado, e pode voltar a abrir depois. " +
                            "Se você tem um Backup JSON, restaure-o em Ajustes › Dados.",
                        ok = "Começar do zero", cancel = "Decidir depois", danger = true,
                    ) { Repo.startFresh() }
                },
            ) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { Repo.retryLoad() }
            }
            is LoadProblem.Unreadable -> dialogs.confirm(
                "Dados não puderam ser abertos",
                "Os dados salvos neste aparelho não puderam ser decifrados (isso acontece, por exemplo, depois de restaurar o sistema). " +
                    (if (p.copied) "Uma cópia cifrada do arquivo foi guardada. " else "Não foi possível guardar uma cópia do arquivo (verifique o espaço livre); começar do zero o descarta. ") +
                    "Nada é salvo até você escolher. Se você tem um Backup JSON, comece do zero e restaure-o em Ajustes › Dados.",
                ok = "Começar do zero", cancel = "Decidir depois", danger = !p.copied,
            ) { Repo.startFresh() }
            is LoadProblem.Invalid -> dialogs.confirm(
                "Dados danificados",
                "Os dados salvos estavam danificados e não puderam ser abertos. Deseja salvar o conteúdo original num arquivo, para tentar recuperá-lo?",
                ok = "Salvar cópia", cancel = "Outras opções",
                onCancel = {
                    dialogs.confirm(
                        "Outras opções",
                        "Começar do zero deixa o app vazio. Se você tem um Backup JSON, restaure-o em Ajustes › Dados.",
                        ok = "Começar do zero", cancel = "Decidir depois", danger = true,
                        // com a cópia cifrada guardada, fecha o aviso; se a gravação estiver bloqueada, o aviso fixo no topo reabre a pergunta
                        onCancel = { Repo.dismissProblem() },
                    ) { Repo.startFresh() }
                },
            ) {
                com.finanplus.security.AppLock.allowExternalOnce()
                saveCopy.launch("finan-plus-dados-danificados-${LocalDate.now()}.json")
            }
            is LoadProblem.Dropped -> { dialogs.notice("Dados revisados", "${p.count} registro(s) inválido(s) foram ignorados ao abrir os dados."); Repo.dismissProblem() }
        }
    }
}
