// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Ops
import com.finanplus.core.Outcome
import com.finanplus.core.RepsMode
import com.finanplus.core.TxDraft
import com.finanplus.data.Repo
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.DateField
import com.finanplus.ui.components.Dialogs
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.MoneyField
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.SwitchRow
import com.finanplus.ui.components.br
import com.finanplus.ui.theme.Fin
import java.time.LocalDate

/** Aplica a operação: sucesso fecha a folha, erro mostra a mensagem e mantém o formulário. */
private fun apply(o: Outcome, dialogs: Dialogs, close: () -> Unit) {
    val err = Repo.commit(o)
    if (err == null) close() else dialogs.notice(err.title, err.message)
}

/** O formulário aberto avisa se tem alterações não salvas (para perguntar antes de descartar). */
class SheetGuard { var dirty = false }
val LocalSheetGuard = staticCompositionLocalOf { SheetGuard() }

@Composable
fun SheetHost(s: AppState, sheet: Sheet, onClose: () -> Unit) {
    val guard = remember { SheetGuard() }
    val dialogs = LocalDialogs.current
    // Deslizar para baixo, tocar fora ou voltar com alterações não salvas: pergunta antes de descartar.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { v ->
        if (v == SheetValue.Hidden && guard.dirty) {
            dialogs.confirm("Descartar alterações?", "O que você digitou neste formulário será perdido.", ok = "Descartar", cancel = "Continuar editando", danger = true) {
                guard.dirty = false; onClose()
            }
            false
        } else true
    })
    androidx.compose.runtime.CompositionLocalProvider(LocalSheetGuard provides guard) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            when (sheet) {
                is Sheet.TxEdit -> TxEditor(s, sheet, onClose)
                is Sheet.GoalEdit -> GoalEditor(s, sheet.id, onClose)
                is Sheet.AccountEdit -> AccountEditor(s, sheet.id, onClose)
                is Sheet.CardEdit -> CardEditor(s, sheet.id, onClose)
                is Sheet.RecurringEdit -> RecurringEditor(s, sheet.id, onClose)
                is Sheet.LimitEdit -> LimitEditor(s, sheet.category, onClose)
                is Sheet.PayInvoice -> PayInvoiceEditor(s, sheet.cardId, onClose)
                is Sheet.Assistant -> AssistantSheet(s, onClose)
                is Sheet.ReportPdf -> ReportExportSheet(s, sheet.from, sheet.to, onClose)
            }
        }
    }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fin.c.muted)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    PrimaryButton(label, danger = true, onClick = onClick)
}

@Composable
private fun Segmented(options: List<Pair<Kind, String>>, selected: Kind, onSelect: (Kind) -> Unit) {
    val p = Fin.c
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(17.dp)).background(p.accent2.copy(alpha = 0.7f)).padding(4.dp)) {
        options.forEach { (k, l) ->
            val on = k == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(k) }.semantics { this.selected = on }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Checkbox(checked, null); Spacer(Modifier.padding(4.dp)); Text(text, fontWeight = FontWeight.SemiBold) }
}

// ------------------------------------------------------------------ lançamento
@Composable
private fun TxEditor(s: AppState, sheet: Sheet.TxEdit, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val tx = sheet.id?.let { id -> s.txs.firstOrNull { it.id == id } }
    val isPayment = tx?.cardPayment?.isNotEmpty() == true
    var kind by rememberSaveable { mutableStateOf(tx?.kind ?: sheet.kind) }
    var desc by rememberSaveable { mutableStateOf(tx?.desc ?: "") }
    var value by rememberSaveable { mutableStateOf(tx?.let { Money.input(it.value) } ?: "") }
    var category by rememberSaveable { mutableStateOf(tx?.category ?: s.cats.of(kind)[0]) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(tx?.date ?: sheet.date ?: LocalDate.now()) }
    // novo lançamento numa data futura (ex.: escolhida no calendário) começa como pendente
    var paid by rememberSaveable { mutableStateOf(tx?.paid ?: (sheet.date?.isAfter(LocalDate.now()) != true)) }
    var useCard by rememberSaveable { mutableStateOf(tx?.isCard == true) }
    var accountId by rememberSaveable { mutableStateOf(tx?.accountId ?: s.accounts[0].id) }
    var cardId by rememberSaveable { mutableStateOf(tx?.cardId?.ifEmpty { null } ?: s.cards.firstOrNull()?.id ?: "") }
    var reps by rememberSaveable { mutableStateOf("1") }
    var repsMode by rememberSaveable { mutableStateOf(RepsMode.TOTAL) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    val guard = LocalSheetGuard.current
    SideEffect {
        guard.dirty = desc != (tx?.desc ?: "") || value != (tx?.let { Money.input(it.value) } ?: "") ||
            (tx != null && (category != tx.category || date != tx.date)) || (tx == null && (reps != "1" || recurring))
    }

    val canCard = kind == Kind.EXPENSE && s.cards.isNotEmpty() && !isPayment
    val card = useCard && canCard
    val cats = s.cats.of(kind).let { if (category !in it) it + category else it }

    Header(if (tx == null) "Novo lançamento" else "Editar lançamento", if (isPayment) "" else "Registre uma receita ou despesa")
    if (isPayment) Text("Pagamento de fatura: debita a conta e abate da fatura do cartão. Não conta como despesa nova.", style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
    else Segmented(listOf(Kind.EXPENSE to "Despesa", Kind.INCOME to "Receita"), kind) { k ->
        if (k != kind) { kind = k; if (category !in s.cats.of(k)) category = s.cats.of(k)[0] }
    }
    Field("Descrição", desc, { desc = it }, placeholder = "Ex.: Mercado")
    // assistente: sugere a categoria pela descrição (lançamento novo, ou quando a descrição foi alterada)
    if (!isPayment) CategoryHint(s, kind, desc, category, enabled = tx == null || desc != tx.desc) { category = it }
    MoneyField("Valor (R$)", value, { value = it })
    SelectField("Categoria", cats.map { it to it }, category, { category = it })
    if (canCard) SelectField("Forma de pagamento", listOf(false to "Conta / dinheiro", true to "Cartão de crédito"), card, { useCard = it })
    if (card) SelectField("Cartão", s.cards.map { it.id to it.name }, cardId, { cardId = it })
    else SelectField("Conta", s.accounts.map { it.id to it.name }, accountId, { accountId = it })
    DateField("Data", date, { date = it })
    if (!card && !isPayment) CheckRow(if (kind == Kind.INCOME) "Receita já recebida" else "Despesa já paga", paid) { paid = it }
    if (tx == null) {
        Field("Parcelas", reps, { reps = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, maxLength = 2)
        if ((reps.toIntOrNull() ?: 1) > 1) SelectField(
            "O valor informado é", listOf(RepsMode.TOTAL to "O total da compra (divide entre as parcelas)", RepsMode.EACH to "O valor de cada parcela"), repsMode, { repsMode = it },
        )
        CheckRow("Repetir mensalmente", recurring) { recurring = it }
    }
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar lançamento") {
        val draft = TxDraft(kind, desc, value, category, date, paid, accountId, if (card) cardId else "", reps.toIntOrNull() ?: 1, repsMode, recurring)
        apply(Ops.saveTx(Repo.state.value, tx?.id, draft), dialogs, close)
    }
    if (tx != null) DeleteButton("Excluir lançamento") {
        dialogs.confirm("Excluir lançamento", "Excluir este lançamento?", ok = "Excluir", danger = true) {
            val later = Ops.laterParcels(Repo.state.value, tx.id)
            if (later.isEmpty()) { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, false)); close() }
            else dialogs.confirm(
                "Parcelas", "Excluir também as ${later.size} parcela(s) seguinte(s)?", ok = "Excluir também", cancel = "Só esta",
                onCancel = { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, false)); close() },
            ) { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, true)); close() }
        }
    }
}

// ------------------------------------------------------------------ meta
@Composable
private fun GoalEditor(s: AppState, id: String?, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val g = id?.let { x -> s.goals.firstOrNull { it.id == x } }
    var name by rememberSaveable { mutableStateOf(g?.name ?: "") }
    var target by rememberSaveable { mutableStateOf(g?.let { Money.input(it.target) } ?: "") }
    var move by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf(g?.deadline) }
    var monthly by rememberSaveable { mutableStateOf(g?.monthly?.takeIf { it > 0 }?.let { Money.input(it) } ?: "") }
    Header(if (g == null) "Nova meta" else "Editar meta", if (g == null) "Dê um nome e um valor ao seu objetivo." else "Guardado até agora: ${Money.format(g.saved)}")
    Field("Nome", name, { name = it }, maxLength = 60)
    MoneyField("Valor da meta (R$)", target, { target = it })
    if (g != null) MoneyField("Guardar ou retirar agora (R$)", move, { move = it }, placeholder = "Ex.: 100 ou -50")
    DateField("Prazo (opcional)", deadline, { deadline = it }, allowClear = true)
    MoneyField("Contribuição mensal planejada (opcional)", monthly, { monthly = it })
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar") { apply(Ops.saveGoal(Repo.state.value, g?.id, name, target, move, deadline, monthly), dialogs, close) }
    if (g != null) DeleteButton("Excluir meta") {
        dialogs.confirm("Excluir meta", "Excluir a meta “${g.name}”?", ok = "Excluir", danger = true) { Repo.replace(Ops.deleteGoal(Repo.state.value, g.id)); close() }
    }
}

// ------------------------------------------------------------------ conta
@Composable
private fun AccountEditor(s: AppState, id: String?, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val a = id?.let { s.account(it) }
    var name by rememberSaveable { mutableStateOf(a?.name ?: "") }
    var initial by rememberSaveable { mutableStateOf(a?.let { Money.input(it.initial) } ?: "0,00") }
    Header(if (a == null) "Nova conta" else "Editar conta", "O saldo inicial entra no saldo atual.")
    Field("Nome", name, { name = it }, maxLength = 40)
    MoneyField("Saldo inicial (R$)", initial, { initial = it })
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar") { apply(Ops.saveAccount(Repo.state.value, a?.id, name, initial), dialogs, close) }
    if (a != null && s.accounts.size > 1) DeleteButton("Excluir conta") {
        when (val o = Ops.deleteAccount(Repo.state.value, a.id)) {
            is Outcome.Err -> dialogs.notice(o.title, o.message)
            is Outcome.Ok -> dialogs.confirm("Excluir conta", "Excluir a conta “${a.name}”?", ok = "Excluir", danger = true) { Repo.replace(o.state); close() }
        }
    }
}

// ------------------------------------------------------------------ cartão
@Composable
private fun CardEditor(s: AppState, id: String?, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val c = id?.let { s.card(it) }
    var name by rememberSaveable { mutableStateOf(c?.name ?: "") }
    var limit by rememberSaveable { mutableStateOf(c?.let { Money.input(it.limit) } ?: "") }
    var close_ by rememberSaveable { mutableStateOf((c?.close ?: 5).toString()) }
    var due by rememberSaveable { mutableStateOf((c?.due ?: 12).toString()) }
    Header(if (c == null) "Novo cartão" else "Editar cartão", "Compras feitas após o dia de fechamento entram na fatura seguinte.")
    Field("Nome", name, { name = it }, maxLength = 40)
    MoneyField("Limite (R$)", limit, { limit = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Fecha dia", close_, { close_ = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboard = KeyboardType.Number, maxLength = 2)
        Field("Vence dia", due, { due = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboard = KeyboardType.Number, maxLength = 2)
    }
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar") { apply(Ops.saveCard(Repo.state.value, c?.id, name, limit, close_, due), dialogs, close) }
    if (c != null) DeleteButton("Excluir cartão") {
        when (val o = Ops.deleteCard(Repo.state.value, c.id)) {
            is Outcome.Err -> dialogs.notice(o.title, o.message)
            is Outcome.Ok -> dialogs.confirm("Excluir cartão", "Excluir o cartão “${c.name}”?", ok = "Excluir", danger = true) { Repo.replace(o.state); close() }
        }
    }
}

// ------------------------------------------------------------------ fatura
@Composable
private fun PayInvoiceEditor(s: AppState, cardId: String, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val c = s.card(cardId)
    val cur = c?.let { Finance.cardStatus(s, it, LocalDate.now()).current }
    if (c == null || cur == null) { Header("Fatura", "Não há fatura em aberto neste cartão."); PrimaryButton("Fechar", onClick = close); return }
    var value by rememberSaveable { mutableStateOf(Money.input(cur.open)) }
    var acc by rememberSaveable { mutableStateOf(s.accounts[0].id) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(LocalDate.now()) }
    Header("Pagar fatura · ${c.name}", "Fatura de ${cur.ym.label()} · vence ${cur.due.br()} · em aberto ${Money.format(cur.open)}")
    MoneyField("Valor pago (R$)", value, { value = it })
    SelectField("Pago com a conta", s.accounts.map { it.id to it.name }, acc, { acc = it })
    DateField("Data do pagamento", date, { date = it })
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Registrar pagamento") { apply(Ops.payInvoice(Repo.state.value, c.id, value, acc, date), dialogs, close) }
}

// ------------------------------------------------------------------ recorrência
@Composable
private fun RecurringEditor(s: AppState, id: String?, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val r = id?.let { x -> s.recurring.firstOrNull { it.id == x } }
    var kind by rememberSaveable { mutableStateOf(r?.kind ?: Kind.EXPENSE) }
    var desc by rememberSaveable { mutableStateOf(r?.desc ?: "") }
    var value by rememberSaveable { mutableStateOf(r?.let { Money.input(it.value) } ?: "") }
    var day by rememberSaveable { mutableStateOf((r?.day ?: 1).toString()) }
    var category by rememberSaveable { mutableStateOf(r?.category ?: s.cats.of(kind)[0]) }
    var acc by rememberSaveable { mutableStateOf(r?.accountId ?: s.accounts[0].id) }
    var card by rememberSaveable { mutableStateOf(r?.cardId ?: "") }
    var active by rememberSaveable { mutableStateOf(r?.active ?: true) }
    var start by rememberSaveable { mutableStateOf<LocalDate?>(LocalDate.now()) }
    val cats = s.cats.of(kind).let { if (category !in it) it + category else it }
    Header(if (r == null) "Nova recorrência" else "Editar recorrência", "Cria um lançamento pendente por mês, a partir da data de início.")
    Field("Descrição", desc, { desc = it }, maxLength = 120)
    MoneyField("Valor (R$)", value, { value = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectField("Tipo", listOf(Kind.EXPENSE to "Despesa", Kind.INCOME to "Receita"), kind, { k -> kind = k; if (category !in s.cats.of(k)) category = s.cats.of(k)[0] }, Modifier.weight(1f))
        Field("Dia do mês", day, { day = it.filter(Char::isDigit) }, Modifier.weight(1f), keyboard = KeyboardType.Number, maxLength = 2)
    }
    SelectField("Categoria", cats.map { it to it }, category, { category = it })
    SelectField("Conta", s.accounts.map { it.id to it.name }, acc, { acc = it })
    if (kind == Kind.EXPENSE && s.cards.isNotEmpty()) SelectField("Cartão (opcional)", listOf("" to "Nenhum (debita da conta)") + s.cards.map { it.id to it.name }, card, { card = it })
    if (r != null) SwitchRow("Ativa", "Pausada não gera novos lançamentos", active) { active = it }
    else DateField("Começa em", start, { start = it })
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar") {
        apply(Ops.saveRecurring(Repo.state.value, r?.id, kind, desc, value, day, category, acc, card, active, start, LocalDate.now()), dialogs, close)
    }
    if (r != null) DeleteButton("Excluir recorrência") {
        dialogs.confirm("Excluir recorrência", "Excluir esta recorrência? Os lançamentos já criados serão mantidos.", ok = "Excluir", danger = true) {
            Repo.replace(Ops.deleteRecurring(Repo.state.value, r.id)); close()
        }
    }
}

// ------------------------------------------------------------------ limite
@Composable
private fun LimitEditor(s: AppState, current: String?, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    var cat by rememberSaveable { mutableStateOf(current ?: s.cats.expense[0]) }
    var value by rememberSaveable { mutableStateOf(current?.let { s.limits[it] }?.let { Money.input(it) } ?: "") }
    Header(if (current == null) "Novo limite" else "Editar limite", "Valor máximo mensal da categoria. Despesas pendentes do mês também contam.")
    SelectField("Categoria", s.cats.expense.map { it to it }, cat, { cat = it })
    MoneyField("Valor mensal (R$)", value, { value = it })
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar") { apply(Ops.saveLimit(Repo.state.value, current, cat, value), dialogs, close) }
    if (current != null) DeleteButton("Excluir limite") {
        dialogs.confirm("Excluir limite", "Excluir o limite de “$current”?", ok = "Excluir", danger = true) { Repo.replace(Ops.deleteLimit(Repo.state.value, current)); close() }
    }
}
