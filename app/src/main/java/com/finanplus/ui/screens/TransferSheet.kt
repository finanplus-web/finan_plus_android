// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.finanplus.R
import com.finanplus.core.AppState
import com.finanplus.core.Money
import com.finanplus.core.Ops
import com.finanplus.core.Transfers
import com.finanplus.data.Repo
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.DateField
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.MoneyField
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.theme.Fin
import java.time.LocalDate

/**
 * Transferência entre contas (TRANSFERENCIAS.md): nova ([Sheet.TransferEdit] sem id) ou editar (id da transferência,
 * aberta ao tocar em qualquer um dos dois lados na lista). Não conta como receita nem despesa.
 */
@Composable
internal fun TransferEditor(s: AppState, transferId: String?, close: () -> Unit) {
    val dialogs = LocalDialogs.current
    val legs = transferId?.let { Transfers.legs(s, it) }
    if (transferId != null && legs == null) {
        Header(stringResource(R.string.transfer_title), stringResource(R.string.transfer_not_found))
        PrimaryButton(stringResource(R.string.transfer_close), onClick = close)
        return
    }
    if (legs == null && s.accounts.size < 2) {
        val nav = com.finanplus.ui.LocalNav.current
        Header(stringResource(R.string.transfer_new), stringResource(R.string.transfer_need_accounts))
        PrimaryButton(stringResource(R.string.transfer_add_account)) { nav.open(Sheet.AccountEdit()) }
        return
    }
    val today = LocalDate.now()
    val firstFrom = legs?.fromId ?: s.accounts[0].id
    val firstTo = legs?.toId ?: s.accounts.first { it.id != firstFrom }.id
    val firstValue = legs?.let { Money.input(it.value) } ?: ""
    val firstDesc = legs?.out?.desc?.takeIf { it != AppState.TRANSFER_CAT } ?: ""
    val firstDate = legs?.date ?: today
    var fromId by rememberSaveable { mutableStateOf(firstFrom) }
    var toId by rememberSaveable { mutableStateOf(firstTo) }
    var value by rememberSaveable { mutableStateOf(firstValue) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(firstDate) }
    var desc by rememberSaveable { mutableStateOf(firstDesc) }
    val guard = LocalSheetGuard.current
    SideEffect { guard.dirty = fromId != firstFrom || toId != firstTo || value != firstValue || date != firstDate || desc != firstDesc }

    val accounts = s.accounts.map { it.id to it.name }
    Header(
        stringResource(if (legs == null) R.string.transfer_new else R.string.transfer_edit),
        stringResource(R.string.transfer_subtitle),
    )
    Glass(Modifier.padding(top = 4.dp), radius = 22.dp, padding = 14.dp) {
        MoneyField(stringResource(R.string.transfer_value), value, { value = it })
    }
    Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        // escolher a mesma conta nos dois campos troca as duas de lugar (nunca fica origem = destino)
        SelectField(stringResource(R.string.transfer_from), accounts, fromId, { id -> if (id == toId) toId = fromId; fromId = id })
        val swap = stringResource(R.string.transfer_swap)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { val a = fromId; fromId = toId; toId = a }
                    .semantics { contentDescription = swap },
                contentAlignment = Alignment.Center,
            ) { AppIcon(Ico.TRANSFER, Fin.c.accent, size = 22.dp) }
        }
        SelectField(stringResource(R.string.transfer_to), accounts, toId, { id -> if (id == fromId) fromId = toId; toId = id })
        DateField(stringResource(R.string.transfer_date), date, { date = it })
        Field(stringResource(R.string.transfer_desc), desc, { desc = it }, placeholder = stringResource(R.string.transfer_desc_hint), maxLength = Ops.TX_DESC_MAX)
    }
    Glass(Modifier.padding(top = 10.dp), radius = 20.dp, padding = 13.dp) {
        Text(stringResource(R.string.transfer_explain), style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(stringResource(if (legs == null) R.string.transfer_save else R.string.transfer_save_edit)) {
        apply(Ops.saveTransfer(Repo.state.value, legs?.id, fromId, toId, value, date, desc, LocalDate.now()), dialogs, close)
    }
    if (legs != null) {
        val title = stringResource(R.string.transfer_delete)
        val msg = stringResource(R.string.transfer_delete_confirm)
        val ok = stringResource(R.string.transfer_delete_ok)
        DeleteButton(title) {
            dialogs.confirm(title, msg, ok = ok, danger = true) { Repo.replace(Ops.deleteTx(Repo.state.value, legs.out.id, false)); close() }
        }
    }
}
