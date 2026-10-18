// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.finanplus.ui.components.Glass
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
                Sheet.AddChoice -> AddChoiceEditor()
                is Sheet.TxEdit -> TxEditor(s, sheet, onClose)
                is Sheet.GoalEdit -> GoalEditor(s, sheet, onClose)
                is Sheet.AccountEdit -> AccountEditor(s, sheet.id, onClose)
                is Sheet.CardEdit -> CardEditor(s, sheet.id, onClose)
                is Sheet.RecurringEdit -> RecurringEditor(s, sheet.id, onClose)
                is Sheet.LimitEdit -> LimitEditor(s, sheet.category, onClose)
                is Sheet.PayInvoice -> PayInvoiceEditor(s, sheet.cardId, onClose)
                is Sheet.Assistant -> AssistantSheet(s, onClose)
                is Sheet.ReportPdf -> ReportExportSheet(s, sheet.from, sheet.to, onClose)
                is Sheet.MovesFilters -> MovesFiltersSheet(s, onClose)
                is Sheet.Simulator -> SimulatorSheet(s, sheet, onClose)
                is Sheet.Radar -> RadarSheet(s, onClose)
                is Sheet.BillImport -> BillImportSheet(s, sheet, onClose)
            }
        }
    }
    }
}

@Composable
private fun AddChoiceEditor() {
    val nav = com.finanplus.ui.LocalNav.current
    Header("Adicionar movimentação", "Escolha o que deseja registrar.")
    AddChoiceRow("Despesa", "Dinheiro que saiu", com.finanplus.ui.components.Ico.EXPENSE, Fin.c.dangerContainer, Fin.c.red) {
        nav.open(Sheet.TxEdit(Kind.EXPENSE))
    }
    AddChoiceRow("Receita", "Dinheiro que entrou", com.finanplus.ui.components.Ico.INCOME, Fin.c.successContainer, Fin.c.green) {
        nav.open(Sheet.TxEdit(Kind.INCOME))
    }
    AddChoiceRow("Nova recorrência", "Configure uma movimentação que se repete", com.finanplus.ui.components.Ico.REPEAT, Fin.c.accent2, Fin.c.accent) {
        nav.open(Sheet.RecurringEdit())
    }
}

@Composable
private fun AddChoiceRow(title: String, description: String, icon: com.finanplus.ui.components.Ico,
                         container: androidx.compose.ui.graphics.Color, tint: androidx.compose.ui.graphics.Color,
                         onClick: () -> Unit) {
    Glass(Modifier.padding(top = 8.dp), radius = 20.dp, padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.finanplus.ui.components.IconTile(icon, tint, container, size = 44.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
            }
            com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.NEXT, Fin.c.muted, size = 20.dp)
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
    if (tx != null && tx.isGoal) { GoalMoveView(s, tx, close); return }
    val isPayment = tx?.cardPayment?.isNotEmpty() == true
    val today = LocalDate.now()
    var kind by rememberSaveable { mutableStateOf(tx?.kind ?: sheet.kind) }
    val pre = if (tx == null) sheet.prefill else null
    var desc by rememberSaveable { mutableStateOf(tx?.desc ?: pre?.desc ?: "") }
    var value by rememberSaveable { mutableStateOf(tx?.let { Money.input(it.value) } ?: pre?.let { Money.input(it.value) } ?: "") }
    var category by rememberSaveable { mutableStateOf(tx?.category ?: s.cats.of(kind)[0]) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(tx?.date ?: sheet.date ?: today) }
    // Novos lançamentos só são considerados realizados por padrão quando a data é hoje.
    // Vindo do "Posso comprar?", começa a pagar: é um plano, não um gasto já feito.
    var paid by rememberSaveable { mutableStateOf(tx?.paid ?: if (pre != null) false else Ops.defaultPaid(date, today)) }
    // Se o usuário escolher explicitamente o status, mudanças de data não sobrescrevem essa escolha.
    var paymentStatusTouched by rememberSaveable { mutableStateOf(pre != null) }
    val preCard = pre?.cardId?.takeIf { s.card(it) != null }
    var useCard by rememberSaveable { mutableStateOf(tx?.isCard == true || preCard != null) }
    var accountId by rememberSaveable { mutableStateOf(tx?.accountId ?: pre?.accountId?.takeIf { s.account(it) != null } ?: s.accounts[0].id) }
    var cardId by rememberSaveable { mutableStateOf(tx?.cardId?.ifEmpty { null } ?: preCard ?: s.cards.firstOrNull()?.id ?: "") }
    var reps by rememberSaveable { mutableStateOf((pre?.parcels?.coerceIn(1, 60) ?: 1).toString()) }
    var repsMode by rememberSaveable { mutableStateOf(RepsMode.TOTAL) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable(sheet.id) { mutableStateOf(tx != null || pre != null) }
    val initialKind = tx?.kind ?: sheet.kind
    val initialDate = tx?.date ?: sheet.date ?: today
    val initialPaid = tx?.paid ?: if (pre != null) false else Ops.defaultPaid(initialDate, today)
    val initialAccountId = tx?.accountId ?: pre?.accountId?.takeIf { s.account(it) != null } ?: s.accounts[0].id
    val initialCardId = tx?.cardId?.ifEmpty { null } ?: preCard ?: s.cards.firstOrNull()?.id ?: ""
    val initialUseCard = tx?.isCard == true || preCard != null
    val initialCategory = tx?.category ?: s.cats.of(kind)[0]
    val guard = LocalSheetGuard.current
    SideEffect {
        guard.dirty = kind != initialKind || desc != (tx?.desc ?: "") ||
            value != (tx?.let { Money.input(it.value) } ?: "") || category != initialCategory || date != initialDate ||
            paid != initialPaid || useCard != initialUseCard || accountId != initialAccountId || cardId != initialCardId ||
            (tx == null && (reps != "1" || repsMode != RepsMode.TOTAL || recurring))
    }

    val canCard = kind == Kind.EXPENSE && s.cards.isNotEmpty() && !isPayment
    val card = useCard && canCard
    val cats = s.cats.of(kind).let { if (category !in it) it + category else it }
    val title = when {
        isPayment -> "Pagamento de fatura"
        tx == null && kind == Kind.INCOME -> "Nova receita"
        tx == null -> "Nova despesa"
        kind == Kind.INCOME -> "Editar receita"
        else -> "Editar despesa"
    }
    val subtitle = when (kind) {
        Kind.INCOME -> "Registre uma entrada de dinheiro"
        Kind.EXPENSE -> "Registre uma saída de dinheiro"
    }

    Header(title, if (isPayment) "" else subtitle)
    if (tx == null && kind == Kind.EXPENSE) {
        val nav = com.finanplus.ui.LocalNav.current
        // atalho: preencher a partir de uma fatura em PDF, linha digitável do boleto ou Pix copia e cola (FATURAS.md)
        com.finanplus.ui.components.Pill("Ler fatura ou boleto", Modifier.fillMaxWidth(), icon = com.finanplus.ui.components.Ico.RECEIPT) {
            guard.dirty = false; nav.open(Sheet.BillImport())
        }
    }
    if (isPayment) {
        Glass(Modifier.padding(top = 4.dp), radius = 20.dp, padding = 13.dp) {
            Text("Este pagamento abate o valor da fatura e da conta, sem registrar uma nova despesa.", style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
        }
    } else {
        Segmented(listOf(Kind.EXPENSE to "Despesa", Kind.INCOME to "Receita"), kind) { k ->
            if (k != kind) { kind = k; if (category !in s.cats.of(k)) category = s.cats.of(k)[0] }
        }
    }

    Glass(Modifier.padding(top = 8.dp), radius = 22.dp, padding = 14.dp) {
        MoneyField(if (kind == Kind.INCOME) "Valor da receita (R$)" else "Valor da despesa (R$)", value, { value = it })
    }

    Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        // Assistente: sugere categoria com base na descrição, sem preencher dados financeiros fictícios.
        if (!isPayment) CategoryHint(s, kind, desc, category, enabled = tx == null || desc != tx.desc) { category = it }
        SelectField("Categoria", cats.map { it to it }, category, { category = it })
        DateField("Data", date, { selectedDate ->
            date = selectedDate
            if (tx == null && !paymentStatusTouched) paid = Ops.defaultPaid(selectedDate, today)
        })
        Field("Descrição (opcional)", desc, { desc = it }, placeholder = if (kind == Kind.INCOME) "Ex.: Salário" else "Ex.: Mercado")
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(if (advancedExpanded) "Menos opções" else "Mais opções")
            com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.DROPDOWN, Fin.c.accent, size = 18.dp)
        }
    }

    AnimatedVisibility(visible = advancedExpanded) {
        Column {
            Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
                Text("Detalhes adicionais", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (canCard) SelectField("Forma de pagamento", listOf(false to "Conta / dinheiro", true to "Cartão de crédito"), card, { useCard = it })
                if (card) SelectField("Cartão", s.cards.map { it.id to it.name }, cardId, { cardId = it })
                else SelectField(if (kind == Kind.INCOME) "Conta de destino" else "Conta", s.accounts.map { it.id to it.name }, accountId, { accountId = it })
                if (tx?.scheduledDate != null) Text(
                    "Data prevista original: ${tx.scheduledDate.br()}. Ela será restaurada se o lançamento voltar a ficar pendente.",
                    style = MaterialTheme.typography.bodySmall, color = Fin.c.muted,
                )
            }

            if (!card && !isPayment) {
                val settled = if (kind == Kind.INCOME) "Já recebida" else "Já paga"
                val pending = if (kind == Kind.INCOME) "A receber" else "A pagar"
                Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
                    Text(if (kind == Kind.INCOME) "Situação da receita" else "Situação da despesa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    PaymentStatusChoice(settled, paid) { paid = true; paymentStatusTouched = true }
                    PaymentStatusChoice(pending, !paid) { paid = false; paymentStatusTouched = true }
                    Text(
                        if (paid) "O valor será considerado no saldo realizado da conta." else "O valor ficará pendente até ser recebido ou pago.",
                        style = MaterialTheme.typography.bodySmall, color = Fin.c.muted, modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }

            if (tx == null) {
                Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
                    Text("Parcelamento e repetição", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Field("Número de parcelas", reps, { reps = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, maxLength = 2)
                    if ((reps.toIntOrNull() ?: 1) > 1) SelectField(
                        "O valor informado é", listOf(RepsMode.TOTAL to "O total da compra (divide entre as parcelas)", RepsMode.EACH to "O valor de cada parcela"), repsMode, { repsMode = it },
                    )
                    CheckRow(if (kind == Kind.INCOME) "Repetir esta receita mensalmente" else "Repetir esta despesa mensalmente", recurring) { recurring = it }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    PrimaryButton(when { isPayment -> "Registrar pagamento"; kind == Kind.INCOME -> "Salvar receita"; else -> "Salvar despesa" }) {
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

@Composable
private fun PaymentStatusChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .semantics { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Lançamento de reserva para meta: só consulta e exclusão (o valor muda pela meta). */
@Composable
private fun GoalMoveView(s: AppState, tx: com.finanplus.core.Tx, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val nav = com.finanplus.ui.LocalNav.current
    val goal = s.goals.firstOrNull { it.id == tx.goalId }
    val saved = tx.kind == Kind.EXPENSE
    Header(if (saved) "Guardado na meta" else "Retirado da meta", goal?.name ?: "Meta excluída")
    Glass(Modifier.padding(top = 4.dp), radius = 22.dp, padding = 14.dp) {
        Text(Money.format(tx.value), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Fin.c.accent)
        Text("${tx.date.br()} · ${s.account(tx.accountId)?.name ?: "Conta"}", style = MaterialTheme.typography.bodyMedium, color = Fin.c.muted)
        Text(
            if (saved) "Este valor saiu do saldo da conta para a meta. Não conta como despesa, nem nos limites ou no assistente."
            else "Este valor voltou da meta para o saldo da conta. Não conta como receita.",
            style = MaterialTheme.typography.bodySmall, color = Fin.c.muted, modifier = Modifier.padding(top = 6.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    if (goal != null) PrimaryButton("Abrir a meta") { nav.open(Sheet.GoalEdit(goal.id)) }
    DeleteButton("Excluir lançamento") {
        val what = if (goal == null) "O saldo da conta volta ao que era antes deste lançamento."
        else if (saved) "O valor guardado na meta diminui ${Money.format(tx.value)} e volta para o saldo da conta."
        else "O valor guardado na meta aumenta ${Money.format(tx.value)} e sai de novo do saldo da conta."
        dialogs.confirm("Excluir lançamento", what, ok = "Excluir", danger = true) { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, false)); close() }
    }
}

// ------------------------------------------------------------------ meta
@Composable
private fun GoalEditor(s: AppState, sheet: Sheet.GoalEdit, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val g = sheet.id?.let { x -> s.goals.firstOrNull { it.id == x } }
    // meta nova pode vir preenchida (simulador "E se…?" › Transformar em meta)
    var name by rememberSaveable { mutableStateOf(g?.name ?: sheet.name) }
    var target by rememberSaveable { mutableStateOf(g?.let { Money.input(it.target) } ?: sheet.target.takeIf { it > 0 }?.let { Money.input(it) } ?: "") }
    // "Radar › Usar dinheiro da meta" já traz o valor a retirar (negativo)
    var move by rememberSaveable { mutableStateOf(if (g != null && sheet.move != 0L) Money.input(sheet.move) else "") }
    var deadline by rememberSaveable { mutableStateOf(g?.deadline) }
    var monthly by rememberSaveable { mutableStateOf(g?.monthly?.takeIf { it > 0 }?.let { Money.input(it) } ?: sheet.monthly.takeIf { it > 0 }?.let { Money.input(it) } ?: "") }
    // reserva para meta (1.7.0): o valor guardado sai da conta, sem contar como despesa
    var fromAccount by rememberSaveable { mutableStateOf(true) }
    val lastAccount = g?.let { goal -> s.txs.filter { it.goalId == goal.id && it.kind == Kind.EXPENSE }.maxByOrNull { it.date }?.accountId }
    var goalAccount by rememberSaveable { mutableStateOf(lastAccount?.takeIf { s.account(it) != null } ?: s.accounts[0].id) }
    Header(if (g == null) "Nova meta" else "Editar meta", if (g == null) "Dê um nome e um valor ao seu objetivo." else "Guardado até agora: ${Money.format(g.saved)}")
    Field("Nome", name, { name = it }, maxLength = 60)
    MoneyField("Valor da meta (R$)", target, { target = it })
    if (g != null) {
        MoneyField("Guardar ou retirar agora (R$)", move, { move = it }, placeholder = "Ex.: 100 ou -50")
        val mv = Money.parse(move)
        if (mv != null && mv != 0L) Glass(Modifier.padding(top = 6.dp), radius = 20.dp, padding = 13.dp) {
            val withdraw = mv < 0
            SwitchRow(
                if (withdraw) "Devolver para a conta" else "Tirar da conta",
                if (fromAccount) (if (withdraw) "O valor volta para o saldo da conta." else "O valor sai do saldo da conta, mas não conta como despesa.")
                else "Só o valor guardado na meta muda; o saldo da conta continua igual.",
                fromAccount,
            ) { fromAccount = it }
            if (fromAccount) SelectField("Conta", s.accounts.map { it.id to it.name }, goalAccount, { goalAccount = it })
        }
    }
    DateField("Prazo (opcional)", deadline, { deadline = it }, allowClear = true)
    MoneyField("Contribuição mensal planejada (opcional)", monthly, { monthly = it })
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar") { apply(Ops.saveGoal(Repo.state.value, g?.id, name, target, move, deadline, monthly, fromAccount, goalAccount), dialogs, close) }
    if (g != null) DeleteButton("Excluir meta") {
        val back = Ops.goalFromAccounts(Repo.state.value, g.id)
        val msg = if (back > 0) "Excluir a meta “${g.name}”? ${Money.format(back)} que saíram das contas para ela voltam para a conta." else "Excluir a meta “${g.name}”?"
        dialogs.confirm("Excluir meta", msg, ok = "Excluir", danger = true) { Repo.replace(Ops.deleteGoal(Repo.state.value, g.id)); close() }
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
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
    if (c != null) {
        val vault = remember { com.finanplus.data.BillPasswords.get(ctx) }
        var hasPw by remember { mutableStateOf(vault.has(c.id)) }
        val nav = com.finanplus.ui.LocalNav.current
        Spacer(Modifier.height(4.dp))
        com.finanplus.ui.components.Pill("Ler a fatura deste cartão (PDF)", Modifier.fillMaxWidth(), icon = com.finanplus.ui.components.Ico.RECEIPT) { nav.open(Sheet.BillImport(cardId = c.id)) }
        // senha do PDF da fatura lembrada só neste aparelho (FATURAS.md): a pessoa pode esquecer a qualquer momento
        if (hasPw) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Senha do PDF da fatura lembrada neste aparelho", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
            com.finanplus.ui.components.Pill("Esquecer senha", danger = true) { vault.remove(c.id); hasPw = false }
        }
    }
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Salvar") { apply(Ops.saveCard(Repo.state.value, c?.id, name, limit, close_, due), dialogs, close) }
    if (c != null) DeleteButton("Excluir cartão") {
        when (val o = Ops.deleteCard(Repo.state.value, c.id)) {
            is Outcome.Err -> dialogs.notice(o.title, o.message)
            is Outcome.Ok -> dialogs.confirm("Excluir cartão", "Excluir o cartão “${c.name}”?", ok = "Excluir", danger = true) {
                Repo.replace(o.state)
                com.finanplus.data.BillPasswords.get(ctx).remove(c.id)
                close()
            }
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
