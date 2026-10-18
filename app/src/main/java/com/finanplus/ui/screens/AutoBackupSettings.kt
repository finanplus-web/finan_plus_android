// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finanplus.core.BackupCrypto
import com.finanplus.data.AutoBackup
import com.finanplus.security.AppLock
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Ajustes › Dados › Backup automático. Regras em BACKUP-AUTOMATICO.md; a cópia em data/AutoBackup.kt.
 */

private enum class Pick { ENABLE, FOLDER }

/** Senha + confirmação. Devolve a senha quando é válida, senão null (e mostra o motivo). */
@Composable
private fun PasswordPair(pw: String, onPw: (String) -> Unit, again: String, onAgain: (String) -> Unit): String? {
    val p = Fin.c
    Field("Senha do backup", pw, onPw, keyboard = KeyboardType.Password, password = true, maxLength = 128)
    Field("Repita a senha", again, onAgain, keyboard = KeyboardType.Password, password = true, maxLength = 128)
    val problem = when {
        pw.isEmpty() -> ""
        pw.length < BackupCrypto.MIN_PASSWORD -> "Use pelo menos ${BackupCrypto.MIN_PASSWORD} caracteres."
        again.isNotEmpty() && again != pw -> "As senhas não são iguais."
        else -> ""
    }
    if (problem.isNotEmpty()) Text(problem, style = MaterialTheme.typography.bodySmall, color = p.red)
    Text("Guarde essa senha fora do celular. Sem ela, nenhuma cópia pode ser aberta, e o Finan+ não consegue recuperá-la.",
        style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 2.dp))
    return pw.takeIf { it.length >= BackupCrypto.MIN_PASSWORD && it == again }
}

@Composable
fun AutoBackupSettings() {
    val p = Fin.c
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AutoBackup.load(ctx) }
    val st by AutoBackup.state.collectAsState()
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var changingPw by rememberSaveable { mutableStateOf(false) }
    var pick by rememberSaveable { mutableStateOf(Pick.ENABLE) }

    fun runNow(first: Boolean) = scope.launch {
        val err = AutoBackup.backupNow(ctx)
        if (err == null) dialogs.notice(if (first) "Backup automático ligado" else "Cópia gravada",
            (if (first) "A primeira cópia foi gravada. As próximas são feitas uma vez por dia, de madrugada. " else "") +
                "Para restaurar: Ajustes › Dados › Restaurar, escolha o arquivo e digite a senha.")
        else dialogs.notice("Não foi possível gravar", err)
    }

    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        AppLock.endExternal()
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            if (pick == Pick.ENABLE) {
                val ok = pw.takeIf { it.length >= BackupCrypto.MIN_PASSWORD && it == again } ?: return@rememberLauncherForActivityResult
                AutoBackup.enable(ctx, uri, ok)
                pw = ""; again = ""
                runNow(first = true)
            } else {
                AutoBackup.changeFolder(ctx, uri)
                runNow(first = false)
            }
        } catch (e: Exception) {
            dialogs.notice("Pasta não disponível", "O Android não permitiu guardar o acesso a essa pasta. Escolha outra (por exemplo, Documentos ou uma pasta do Syncthing).")
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("Backup automático", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })

    if (!st.enabled) {
        Text("Uma cópia criptografada por dia numa pasta sua (do celular, cartão SD, Syncthing ou Nextcloud). Só o Finan+ com a senha consegue abrir.",
            style = MaterialTheme.typography.bodySmall, color = p.muted)
        val ok = PasswordPair(pw, { pw = it }, again, { again = it })
        PrimaryButton(if (ok != null) "Escolher pasta e ligar" else "Defina a senha para continuar", Modifier.padding(top = 8.dp)) {
            if (ok != null) { pick = Pick.ENABLE; AppLock.allowExternalOnce(); folder.launch(null) }
        }
        return
    }

    val fmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Pasta: ${st.folder}", style = MaterialTheme.typography.bodyMedium)
        Text(
            when {
                st.running -> "Gravando a cópia…"
                st.lastAt > 0 -> "Última cópia: ${fmt.format(Instant.ofEpochMilli(st.lastAt))}" + (if (st.lastFile.isNotEmpty()) " (${st.lastFile})" else "")
                else -> "Nenhuma cópia ainda."
            },
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        if (st.lastError.isNotEmpty()) Text(st.lastError, style = MaterialTheme.typography.bodySmall, color = p.red, modifier = Modifier.padding(top = 2.dp))
    }
    SelectField("Guardar", AutoBackup.KEEP_OPTIONS.map { it to "As últimas $it cópias" }, st.keep, { AutoBackup.setKeep(ctx, it) }, Modifier.padding(top = 6.dp))
    Text("Cópias mais antigas com o nome finan-plus-auto-… são apagadas. Os outros arquivos da pasta não são tocados.",
        style = MaterialTheme.typography.labelSmall, color = p.muted)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (st.running) "Gravando…" else "Fazer agora", Modifier.weight(1f)) { if (!st.running) runNow(first = false) }
        Pill("Trocar pasta", Modifier.weight(1f)) { pick = Pick.FOLDER; AppLock.allowExternalOnce(); folder.launch(null) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (changingPw) "Cancelar" else "Trocar senha", Modifier.weight(1f)) { changingPw = !changingPw; pw = ""; again = "" }
        Pill("Desligar", Modifier.weight(1f), danger = true) {
            dialogs.confirm("Desligar o backup automático", "As cópias já gravadas continuam na pasta. O Finan+ esquece a pasta e a senha.", ok = "Desligar", danger = true) {
                AutoBackup.disable(ctx)
            }
        }
    }
    if (changingPw) {
        Text("A nova senha vale para as próximas cópias. As antigas continuam abrindo com a senha antiga.",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 8.dp))
        val ok = PasswordPair(pw, { pw = it }, again, { again = it })
        PrimaryButton("Salvar nova senha", Modifier.padding(top = 8.dp)) {
            if (ok != null) { AutoBackup.setPassword(ctx, ok); changingPw = false; pw = ""; again = ""; runNow(first = false) }
        }
    }
}
