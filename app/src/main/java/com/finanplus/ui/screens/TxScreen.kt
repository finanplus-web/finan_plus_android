// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.R
import com.finanplus.ui.components.SwitchRow
import com.finanplus.ui.components.Stepper
import com.finanplus.ui.components.SegmentedChoice
import com.finanplus.core.Words
import androidx.compose.ui.graphics.graphicsLayer
import com.finanplus.core.AmountInput
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Ops
import com.finanplus.core.RepsMode
import com.finanplus.core.TxDraft
import com.finanplus.core.TxForm
import com.finanplus.data.Repo
import com.finanplus.ui.LocalTxSnackbar
import com.finanplus.ui.LocalUiScope
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalToday
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.br
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A tela de Receita e Despesa (novo e editar) é a tela cheia abaixo. Pagamento de fatura, reserva para meta e transferência
 * continuam no formulário em folha de antes (não são "lançar uma receita ou despesa").
 */
fun usesTxScreen(s: AppState, sheet: Sheet.TxEdit): Boolean {
    val tx = sheet.id?.let { id -> s.txs.firstOrNull { it.id == id } }
    return tx == null || (!tx.isGoal && !tx.isTransfer && tx.cardPayment.isEmpty())
}

/** Cor do texto sobre um fundo colorido: branco, ou o escuro do tema quando o branco não chega a 4,5:1. */
@Composable
internal fun onFill(fill: Color): Color {
    fun contrast(a: Color, b: Color): Float { val l1 = a.luminance() + 0.05f; val l2 = b.luminance() + 0.05f; return if (l1 > l2) l1 / l2 else l2 / l1 }
    val white = Color.White
    if (contrast(white, fill) >= 4.5f) return white
    val dark = Fin.c.bg
    return if (contrast(dark, fill) >= contrast(Color.Black, fill)) dark else Color.Black
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxScreen(s: AppState, sheet: Sheet.TxEdit, close: () -> Unit) {
    val p = Fin.c
    val dialogs = LocalDialogs.current
    val today = LocalToday.current
    val tx = sheet.id?.let { id -> s.txs.firstOrNull { it.id == id } }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val snackbar = LocalTxSnackbar.current
    val uiScope = LocalUiScope.current

    // ---- estado (sobrevive a girar a tela)
    val initialKind = tx?.kind ?: sheet.kind
    val initialDate = tx?.date ?: sheet.date ?: today
    val initialAmount = tx?.let { AmountInput.fromCents(it.value) } ?: ""
    // novo: pré-seleção = última categoria usada do tipo (ou a pedida por quem abriu, ex.: Dívidas)
    val initialCategory = tx?.category ?: sheet.category
        ?: TxForm.recentCategories(s.txs, initialKind, TxForm.choices(s.cats.of(initialKind)), 1).firstOrNull() ?: ""
    val initialPaid = tx?.paid ?: Ops.defaultPaid(initialDate, today)
    val initialAccount = tx?.accountId ?: s.accounts[0].id
    val initialCard = tx?.cardId?.ifEmpty { null } ?: s.cards.firstOrNull()?.id ?: ""
    val initialUseCard = tx?.isCard == true
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(initialDate) }
    var desc by rememberSaveable { mutableStateOf(tx?.desc ?: "") }
    var paid by rememberSaveable { mutableStateOf(initialPaid) }
    // Se a pessoa escolher a situação, mudar a data não a sobrescreve.
    var paidTouched by rememberSaveable { mutableStateOf(false) }
    var useCard by rememberSaveable { mutableStateOf(initialUseCard) }
    var accountId by rememberSaveable { mutableStateOf(initialAccount) }
    var cardId by rememberSaveable { mutableStateOf(initialCard) }
    var reps by rememberSaveable { mutableStateOf(1) }
    var repsMode by rememberSaveable { mutableStateOf(RepsMode.TOTAL) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable(sheet.id) { mutableStateOf(false) }
    var drawer by rememberSaveable { mutableStateOf(false) }
    var payPicker by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    val nav = com.finanplus.ui.LocalNav.current

    val dirty = kind != initialKind || amount != initialAmount || category != initialCategory || date != initialDate ||
        desc != (tx?.desc ?: "") || paid != initialPaid || useCard != initialUseCard || accountId != initialAccount || cardId != initialCard ||
        (tx == null && (reps != 1 || repsMode != RepsMode.TOTAL || recurring))
    fun leaveThen(after: () -> Unit) {
        if (dirty) dialogs.confirm("Descartar alterações?", "O que você digitou neste formulário será perdido.", ok = "Descartar", cancel = "Continuar editando", danger = true) { close(); after() }
        else { close(); after() }
    }
    val leave: () -> Unit = { leaveThen { } }
    BackHandler(onBack = leave)

    val isExpense = kind == Kind.EXPENSE
    val canCard = isExpense && s.cards.isNotEmpty()
    val card = useCard && canCard
    val toneTarget = if (isExpense) p.red else p.green
    val tone by animateColorAsState(toneTarget, tween(220), label = "tom do tipo")
    val canSave = AmountInput.isPositive(amount)
    val choices = TxForm.choices(s.cats.of(kind)).let { l ->
        val cur = TxForm.displayCategory(category)
        if (category.isNotBlank() && cur !in l) l + cur else l
    }

    val savedExpense = stringResource(R.string.tx_saved_expense)
    val savedIncome = stringResource(R.string.tx_saved_income)
    val undoLabel = stringResource(R.string.cats_undo)
    fun save() {
        // sem categoria escolhida, salva em "Outros" (ou na primeira do tipo, se não houver "Outros")
        val cats = s.cats.of(kind)
        val cat = category.ifBlank { cats.firstOrNull { it == TxForm.OTHERS } ?: cats[0] }
        val draft = TxDraft(kind, desc, amount, cat, date, paid, accountId, if (card) cardId else "", reps, repsMode, recurring)
        val before = Repo.state.value
        val err = Repo.commit(Ops.saveTx(before, tx?.id, draft))
        if (err == null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val msg = if (isExpense) savedExpense else savedIncome
            close()
            // aviso com "Desfazer": volta ao estado de antes de salvar
            uiScope?.launch {
                if (snackbar.showSnackbar(msg, actionLabel = undoLabel, withDismissAction = true) == androidx.compose.material3.SnackbarResult.ActionPerformed) Repo.replace(before)
            }
        } else dialogs.notice(err.title, err.message)
    }

    androidx.compose.material3.Surface(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } },
        color = p.bg,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // ---- barra superior: fechar, título e "Ler boleto" (despesa nova)
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val closeLabel = stringResource(R.string.tx_close)
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = leave).semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(Ico.CLOSE, p.text, size = 24.dp) }
                Text(
                    stringResource(if (tx != null) R.string.tx_title_edit else if (isExpense) R.string.tx_title_new_expense else R.string.tx_title_new_income),
                    Modifier.weight(1f).padding(horizontal = 6.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (tx == null && isExpense) Row(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(p.accent2)
                        .clickable(role = Role.Button) { leaveThen { nav.open(Sheet.BillImport()) } }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(Ico.RECEIPT, p.accent, size = 18.dp)
                    Text(stringResource(R.string.tx_read_bill), Modifier.padding(start = 6.dp), color = p.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // ---- Despesa / Receita (segmentado)
                KindSwitch(kind) { k ->
                    if (k != kind) {
                        kind = k
                        category = TxForm.categoryAfterKindChange(category, TxForm.choices(s.cats.of(k)))
                            .ifBlank { TxForm.recentCategories(s.txs, k, TxForm.choices(s.cats.of(k)), 1).firstOrNull() ?: "" }
                        if (k == Kind.INCOME) useCard = false
                    }
                }

                // ---- valor: o herói da tela
                Text(stringResource(R.string.tx_amount_title), Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = p.muted)
                AmountEntry(amount, { amount = it }, tone, autoFocus = tx == null)
                Box(Modifier.fillMaxWidth().heightIn(min = 22.dp), contentAlignment = Alignment.Center) {
                    if (AmountInput.isZeroTyped(amount)) Text(
                        stringResource(R.string.tx_amount_error), style = MaterialTheme.typography.bodySmall, color = p.red,
                        modifier = Modifier.semantics { contentDescription = "Erro: valor igual a zero" },
                    )
                }

                // ---- categoria: chips das mais usadas + "Todas ›"
                FormLabel(stringResource(R.string.tx_category))
                val chips = remember(s.txs, kind, choices, category) {
                    val recent = TxForm.recentCategories(s.txs, kind, choices, 4)
                    val base = (recent + choices).distinct().take(4)
                    val cur = TxForm.displayCategory(category)
                    if (category.isNotBlank() && cur !in base) listOf(cur) + base.take(3) else base
                }
                FlowRow(Modifier.fillMaxWidth().testTag(TxTags.CATEGORY_ROW), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { c ->
                        val on = category.isNotBlank() && TxForm.displayCategory(category) == c
                        FormChip(Words.ordinal(c), on, TxIcons.forCategory(c), Modifier.testTag(TxTags.chip(c))) { category = c }
                    }
                    FormChip(stringResource(R.string.tx_all_categories), false, null, Modifier.testTag(TxTags.ALL_CATEGORIES), trailing = Ico.NEXT) {
                        focus.clearFocus(); keyboard?.hide(); drawer = true
                    }
                }

                // ---- data: Hoje, Ontem, Escolher
                FormLabel(stringResource(R.string.tx_date))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val yesterday = today.minusDays(1)
                    fun pick(d: LocalDate) { date = d; if (tx == null && !paidTouched) paid = Ops.defaultPaid(d, today) }
                    FormChip(stringResource(R.string.tx_today), date == today, null) { pick(today) }
                    FormChip(stringResource(R.string.tx_yesterday), date == yesterday, null) { pick(yesterday) }
                    val other = date != null && date != today && date != yesterday
                    FormChip(if (other) date!!.br() else stringResource(R.string.tx_pick_date), other, Ico.CALENDAR) { focus.clearFocus(); keyboard?.hide(); datePicker = true }
                }

                // ---- descrição (opcional) e sugestões
                FormLabel(stringResource(R.string.tx_description_hint))
                val hint = stringResource(R.string.tx_description_placeholder)
                TextField(
                    desc, { desc = it.take(Ops.TX_DESC_MAX) }, Modifier.fillMaxWidth().semantics { contentDescription = hint },
                    placeholder = { Text(hint) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = p.surface, unfocusedContainerColor = p.surface,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent,
                        focusedPlaceholderColor = p.muted, unfocusedPlaceholderColor = p.muted,
                    ),
                )
                val suggestions = remember(s.txs, kind) { TxForm.descriptionSuggestions(s.txs, kind) }
                if (suggestions.isNotEmpty()) {
                    val sugLabel = stringResource(R.string.tx_description_suggestions)
                    LazyRow(
                        Modifier.fillMaxWidth().padding(top = 6.dp).semantics { contentDescription = sugLabel },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(suggestions) { d ->
                            SuggestionChip(onClick = { desc = d.take(Ops.TX_DESC_MAX) }, label = { Text(Words.ordinal(d), maxLines = 1) })
                        }
                    }
                }
                // Assistente: sugere categoria com base na descrição (como antes)
                CategoryHint(s, kind, desc, category, enabled = tx == null || desc != tx.desc) { category = it }

                // ---- mais opções, recolhido com resumo: forma de pagamento, situação, parcelas e repetição
                val payName = if (card) s.card(cardId)?.name ?: "" else s.account(accountId)?.name ?: ""
                val status = when { card -> stringResource(R.string.tx_more_card); paid -> stringResource(if (isExpense) R.string.tx_paid else R.string.tx_received); else -> stringResource(if (isExpense) R.string.tx_to_pay else R.string.tx_to_receive) }
                val parts = if (reps > 1) "${reps}x" else stringResource(R.string.tx_single)
                val rot by androidx.compose.animation.core.animateFloatAsState(if (moreOpen) 180f else 0f, tween(200), label = "mais opções")
                Glass(Modifier.fillMaxWidth().padding(top = 14.dp), radius = 18.dp, padding = 0.dp) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { moreOpen = !moreOpen }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tx_more), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(listOf(payName, status, parts).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AppIcon(Ico.EXPAND, p.muted, Modifier.graphicsLayer { rotationZ = rot }, size = 22.dp)
                    }
                    AnimatedVisibility(visible = moreOpen) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                            FieldRow(
                                icon = if (card) Ico.CARD else Ico.WALLET, tint = if (card) p.card else p.accent, tile = if (card) p.cardContainer else p.accent2,
                                label = stringResource(if (isExpense) R.string.tx_pay_with else R.string.tx_receive_in), value = payName, chevron = true,
                                modifier = Modifier.testTag(TxTags.PAY_ROW),
                            ) { focus.clearFocus(); keyboard?.hide(); payPicker = true }
                            if (!card) {
                                FormLabel(stringResource(if (isExpense) R.string.tx_status_expense else R.string.tx_status_income))
                                SegmentedChoice(
                                    listOf(true to stringResource(if (isExpense) R.string.tx_paid_cap else R.string.tx_received_cap), false to stringResource(if (isExpense) R.string.tx_to_pay_cap else R.string.tx_to_receive_cap)),
                                    paid,
                                ) { paid = it; paidTouched = true }
                            }
                            if (tx?.scheduledDate != null) Text(
                                "Data prevista original: ${tx.scheduledDate.br()}. Ela será restaurada se o lançamento voltar a ficar pendente.",
                                style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 8.dp),
                            )
                            if (tx == null) {
                                Stepper(stringResource(R.string.tx_parcels), reps, 1..48, { reps = it }, Modifier.padding(top = 8.dp)) { if (it == 1) "À vista" else "${it}x" }
                                if (reps > 1) SegmentedChoice(
                                    listOf(RepsMode.TOTAL to stringResource(R.string.tx_reps_total), RepsMode.EACH to stringResource(R.string.tx_reps_each)), repsMode,
                                ) { repsMode = it }
                                SwitchRow(stringResource(if (kind == Kind.INCOME) R.string.tx_repeat_income else R.string.tx_repeat_expense), null, recurring) { recurring = it }
                            }
                        }
                    }
                }
                if (tx != null) DeleteButton("Excluir lançamento") {
                    dialogs.confirm("Excluir lançamento", "Excluir este lançamento?", ok = "Excluir", danger = true) {
                        val later = Ops.laterParcels(Repo.state.value, tx.id)
                        if (later.isEmpty()) { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, false)); close() }
                        else dialogs.confirm(
                            "Parcelas", "Excluir também " + (if (later.size == 1) "a parcela seguinte?" else "as ${later.size} parcelas seguintes?"), ok = "Excluir também", cancel = "Só esta",
                            onCancel = { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, false)); close() },
                        ) { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, true)); close() }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- Salvar: botão principal (cor primária), fixo no rodapé; desabilitado até haver valor
            Button(
                onClick = { save() }, enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp).testTag(TxTags.SAVE),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = p.accent, contentColor = p.onAccent,
                    disabledContainerColor = p.accent.copy(alpha = 0.38f), disabledContentColor = p.onAccent.copy(alpha = 0.7f),
                ),
            ) { Text(stringResource(if (isExpense) R.string.tx_save_expense else R.string.tx_save_income), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium) }
        }
    }

    // ---- seletor de categoria (folha com busca e check)
    if (drawer) CategoryDrawer(
        kind = kind, choices = choices,
        current = TxForm.displayCategory(category).takeIf { category.isNotBlank() },
        onPick = { category = it; drawer = false }, onClose = { drawer = false },
        onManage = { drawer = false; leaveThen { nav.tab = com.finanplus.ui.Tab.ACCOUNTS; nav.moneyPage = PAGE_CATEGORIES } },
    )
    // ---- pagar com / receber em
    if (payPicker) PayWithSheet(s, isExpense, canCard, card, cardId, accountId, onClose = { payPicker = false }) { isCard, id ->
        if (isCard) { useCard = true; cardId = id } else { useCard = false; accountId = id }
        payPicker = false
    }
    // ---- seletor de data nativo do Material
    if (datePicker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        date = d
                        if (tx == null && !paidTouched) paid = Ops.defaultPaid(d, today)
                    }
                    datePicker = false
                }) { Text(stringResource(R.string.tx_date_ok)) }
            },
            dismissButton = { TextButton({ datePicker = false }) { Text(stringResource(R.string.tx_date_cancel)) } },
        ) { DatePicker(st) }
    }
}

