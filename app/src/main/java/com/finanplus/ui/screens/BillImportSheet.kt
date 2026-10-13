// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Ops
import com.finanplus.core.Outcome
import com.finanplus.core.TxDraft
import com.finanplus.core.bill.BillInfo
import com.finanplus.core.bill.BillMatch
import com.finanplus.core.bill.BillReader
import com.finanplus.data.BillPasswords
import com.finanplus.data.BillPdf
import com.finanplus.data.Repo
import com.finanplus.security.AppLock
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.DateField
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.MoneyField
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.br
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** PDF recebido por "Compartilhar → Finan+" esperando o app ser desbloqueado (ver MainActivity e Root). */
object SharedBill { val pending = MutableStateFlow<Sheet.BillImport?>(null) }

private enum class BillStep { CHOOSE, PASSWORD, RESULT }

/**
 * Ler fatura ou boleto (FATURAS.md): escolher o PDF ou colar o código, digitar a senha se o PDF tiver,
 * conferir o que foi encontrado e salvar. Nada é lançado sem a pessoa confirmar.
 */
@Composable
fun BillImportSheet(s: AppState, sheet: Sheet.BillImport, close: () -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val scope = rememberCoroutineScope()
    val vault = remember { BillPasswords.get(ctx) }
    val p = Fin.c
    val today = LocalDate.now()

    var cardId by rememberSaveable { mutableStateOf(sheet.cardId?.takeIf { s.card(it) != null } ?: "") }
    var uri by rememberSaveable { mutableStateOf(sheet.sharedUri) }
    var step by rememberSaveable { mutableStateOf(BillStep.CHOOSE) }
    var busy by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    // a senha digitada fica só na memória da tela (não vai para o estado salvo do Android)
    var pw by remember { mutableStateOf("") }
    var rememberPw by rememberSaveable { mutableStateOf(false) }
    var pwError by remember { mutableStateOf<String?>(null) }
    var fromVaultFailed by rememberSaveable { mutableStateOf(false) }

    // resultado (editável)
    var desc by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var valueNote by rememberSaveable { mutableStateOf("") }
    var dueNote by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf("") }
    var payeeNote by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf(s.accounts[0].id) }
    var paid by rememberSaveable { mutableStateOf(false) }

    val guard = LocalSheetGuard.current
    SideEffect { guard.dirty = step == BillStep.RESULT }

    val card = s.card(cardId)

    fun show(info: BillInfo) {
        value = info.amount?.let { Money.input(it.value) } ?: ""
        valueNote = info.amount?.let { "Do ${it.source.label}: “${it.excerpt}”" } ?: ""
        due = info.due?.value
        dueNote = info.due?.let { "Do ${it.source.label}: “${it.excerpt}”" } ?: ""
        desc = when {
            card != null -> "Fatura ${card.name}"
            info.segment != null -> info.segment
            info.payee != null && info.amount?.source == com.finanplus.core.bill.Source.PIX -> info.payee
            else -> "Boleto"
        }.take(Ops.TX_DESC_MAX)
        payeeNote = if (card == null && info.payee != null && info.amount?.source != com.finanplus.core.bill.Source.PIX) "Boleto emitido pelo banco ${info.payee} (nem sempre é quem cobra)." else ""
        notice = when {
            info.isEmpty && info.dynamicPix -> "Pix dinâmico: o valor fica no banco de quem cobra e não vem no código. Digite o valor."
            info.isEmpty -> "Não encontrei o valor nem o vencimento. Preencha abaixo."
            info.amount == null -> "Não encontrei o valor. Digite abaixo."
            info.due == null -> "Não encontrei o vencimento. Confira a data abaixo."
            else -> ""
        }
        if (due == null) due = today
        category = s.cats.expense.firstOrNull { it.equals("Moradia", true) && info.segment != null } ?: s.cats.expense[0]
        paid = false
        step = BillStep.RESULT
    }

    fun openPdf(password: String?, fromVault: Boolean) {
        val u = uri ?: return
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { BillPdf.read(ctx, Uri.parse(u), password) }
            busy = false
            when (r) {
                is BillPdf.Result.Text -> {
                    if (cardId.isNotEmpty() && !fromVault && !password.isNullOrEmpty()) {
                        if (rememberPw) vault.set(cardId, password) else vault.remove(cardId)
                    }
                    pw = ""; pwError = null
                    val info = withContext(Dispatchers.Default) { BillReader.fromText(r.text, today) }
                    show(info)
                }
                is BillPdf.Result.NeedsPassword -> {
                    val saved = if (cardId.isNotEmpty() && !fromVaultFailed) vault.get(cardId) else null
                    if (saved != null && password == null) openPdf(saved, true)
                    else { rememberPw = cardId.isNotEmpty() && vault.has(cardId); pwError = null; step = BillStep.PASSWORD }
                }
                is BillPdf.Result.WrongPassword -> {
                    if (fromVault) { fromVaultFailed = true; rememberPw = true; pwError = "A senha lembrada não abriu esta fatura. Digite a senha atual." }
                    else pwError = "Senha incorreta. Confira e tente de novo."
                    step = BillStep.PASSWORD
                }
                is BillPdf.Result.NoText -> {
                    dialogs.notice("PDF sem texto", "Esta fatura parece ser uma imagem (escaneada). Digite o valor e o vencimento, ou cole o código do boleto.")
                    show(BillInfo())
                }
                is BillPdf.Result.Error -> dialogs.notice("Não foi possível ler", r.message)
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        AppLock.endExternal()
        if (u != null) { uri = u.toString(); fromVaultFailed = false; openPdf(null, false) }
    }

    // PDF compartilhado de outro app: abre direto
    LaunchedEffect(Unit) { if (sheet.sharedUri != null && step == BillStep.CHOOSE) openPdf(null, false) }

    Text("Ler fatura ou boleto", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

    if (busy) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = p.accent)
            Spacer(Modifier.size(10.dp))
            Text("Lendo a fatura…", color = p.muted)
        }
        return
    }

    when (step) {
        BillStep.CHOOSE -> {
            Text("O valor e o vencimento saem do PDF ou do código. Você confere antes de salvar.", style = MaterialTheme.typography.bodyMedium, color = p.muted)
            Spacer(Modifier.height(12.dp))
            if (s.cards.isNotEmpty()) SelectField(
                "É a fatura de um cartão?", listOf("" to "Não (boleto ou conta)") + s.cards.map { it.id to it.name }, cardId, { cardId = it; fromVaultFailed = false },
            )
            Spacer(Modifier.height(6.dp))
            PrimaryButton("Escolher PDF") { AppLock.allowExternalOnce(); pick.launch(arrayOf("application/pdf")) }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                code, { code = it.take(600) }, Modifier.fillMaxWidth(),
                label = { Text("Ou cole o código do boleto ou o Pix copia e cola") }, minLines = 2, maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(6.dp))
            Pill("Ler código", Modifier.fillMaxWidth(), icon = Ico.RECEIPT) {
                val info = BillReader.fromCode(code, today)
                if (info == null) dialogs.notice("Código não reconhecido", "Confira se copiou a linha digitável inteira (47 ou 48 números) ou o Pix copia e cola completo. Um número errado não passa na conferência.")
                else show(info)
            }
            Spacer(Modifier.height(14.dp))
            PrivacyNote()
        }

        BillStep.PASSWORD -> {
            Text("Esta fatura tem senha. Digite para abrir.", style = MaterialTheme.typography.bodyMedium)
            Text("Muitos bancos usam os primeiros dígitos do seu CPF.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            Spacer(Modifier.height(8.dp))
            Field("Senha do PDF", pw, { pw = it }, keyboard = KeyboardType.Password, password = true, maxLength = 64)
            pwError?.let { Text(it, color = p.red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (card != null) CheckLine("Lembrar a senha para ${card.name}", rememberPw) { rememberPw = it }
            if (card != null && rememberPw) Text(
                "Fica cifrada só neste aparelho e não vai para o backup. Para esquecer, desmarque aqui ou use Esquecer senha no cartão.",
                style = MaterialTheme.typography.bodySmall, color = p.muted,
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Abrir") { if (pw.isEmpty()) pwError = "Digite a senha." else openPdf(pw, false) }
            Spacer(Modifier.height(6.dp))
            Pill("Voltar", Modifier.fillMaxWidth()) { pw = ""; pwError = null; step = BillStep.CHOOSE }
        }

        BillStep.RESULT -> {
            if (notice.isNotEmpty()) Note(notice, warn = true)
            Field("Descrição", desc, { desc = it }, maxLength = Ops.TX_DESC_MAX)
            MoneyField("Valor (R$)", value, { value = it })
            if (valueNote.isNotEmpty()) SourceNote(valueNote)
            DateField("Vencimento", due, { due = it })
            if (dueNote.isNotEmpty()) SourceNote(dueNote)
            if (payeeNote.isNotEmpty()) SourceNote(payeeNote)
            Spacer(Modifier.height(8.dp))
            if (card != null) {
                val ym = BillMatch.invoiceYm(s, card, due, today)
                val app = BillMatch.appTotal(s, card, ym)
                val bank = Money.parse(value)
                val diff = if (bank != null) bank - app else null
                val mes = ym.label()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.accent.copy(alpha = 0.10f)).padding(12.dp)) {
                    Text("Fatura de $mes · ${card.name}", fontWeight = FontWeight.Bold)
                    Text("No Finan+: ${Money.format(app)}", color = p.muted)
                    if (bank != null) Text("No PDF: ${Money.format(bank)}", color = p.muted)
                    when {
                        diff == null -> Text("Informe o valor para comparar.", color = p.muted)
                        diff == 0L -> Text("Confere com as compras lançadas.", fontWeight = FontWeight.Bold, color = p.green)
                        diff > 0 -> Text("Faltam ${Money.format(diff)} em compras no Finan+.", fontWeight = FontWeight.Bold)
                        else -> Text("O Finan+ tem ${Money.format(-diff)} a mais do que o banco. Confira as compras do cartão.", fontWeight = FontWeight.Bold, color = p.red)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (diff != null && diff > 0) {
                    PrimaryButton(if (app == 0L) "Lançar a fatura no cartão" else "Lançar a diferença no cartão") {
                        val draft = TxDraft(
                            Kind.EXPENSE, if (app == 0L) desc.ifBlank { "Fatura ${card.name}" } else "Diferença da fatura ${card.name} ($mes)",
                            Money.input(diff), s.cats.expense.firstOrNull { it.equals("Outros", true) } ?: s.cats.expense[0],
                            BillMatch.dateInInvoice(card, ym), true, s.accounts[0].id, card.id,
                        )
                        save(Ops.saveTx(Repo.state.value, null, draft), dialogs, close)
                    }
                    Text("Entra como uma compra na fatura de $mes, para o Finan+ bater com o banco.", style = MaterialTheme.typography.bodySmall, color = p.muted)
                } else PrimaryButton("Fechar") { guard.dirty = false; close() }
            } else {
                val cats = s.cats.expense.let { if (category.isNotEmpty() && category !in it) it + category else it }
                CategoryHint(s, Kind.EXPENSE, desc, category, enabled = true) { category = it }
                SelectField("Categoria", cats.map { it to it }, category.ifEmpty { cats[0] }, { category = it })
                SelectField("Conta", s.accounts.map { it.id to it.name }, accountId, { accountId = it })
                CheckLine("Já paga", paid) { paid = it }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(if (paid) "Salvar despesa" else "Salvar como conta a pagar") {
                    val draft = TxDraft(Kind.EXPENSE, desc, value, category.ifEmpty { cats[0] }, due, paid, accountId)
                    save(Ops.saveTx(Repo.state.value, null, draft), dialogs, close)
                }
                if (!paid && due != null) Text("Fica pendente até ${due!!.br()} e entra nos avisos de vencimento.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Spacer(Modifier.height(6.dp))
            Pill("Ler outra", Modifier.fillMaxWidth()) { step = BillStep.CHOOSE; code = ""; uri = null }
        }
    }
}

private fun save(o: Outcome, dialogs: com.finanplus.ui.components.Dialogs, close: () -> Unit) {
    val err = Repo.commit(o)
    if (err == null) close() else dialogs.notice(err.title, err.message)
}

@Composable
private fun PrivacyNote() {
    val p = Fin.c
    Row(verticalAlignment = Alignment.Top) {
        AppIcon(Ico.SHIELD, p.accent, size = 18.dp)
        Spacer(Modifier.size(8.dp))
        Text(
            "Tudo acontece neste aparelho, sem internet. O PDF é lido só na memória e não fica guardado.",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
    }
}

@Composable
private fun Note(text: String, warn: Boolean) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background((if (warn) p.red else p.accent).copy(alpha = 0.10f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(if (warn) Ico.WARNING else Ico.CHECK, if (warn) p.red else p.accent, size = 18.dp)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** "Do código do boleto: “34191.79001…”": de onde o valor saiu, para a pessoa conferir. */
@Composable
private fun SourceNote(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = Fin.c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

@Composable
private fun CheckLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { Checkbox(checked, null); Spacer(Modifier.padding(4.dp)); Text(text, fontWeight = FontWeight.SemiBold) }
}
