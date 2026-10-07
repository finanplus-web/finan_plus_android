// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.security.AppLock
import com.finanplus.ui.components.Dialogs
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.Pill
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Permitir <navegador>?" — aparece em qualquer tela quando um navegador digita o código certo.
 * Fica no FinanRoot, só com o app desbloqueado (com PIN/digital ligado, abrir pela notificação pede o desbloqueio antes).
 * Fechar a janela (tocar fora ou voltar) conta como "Recusar".
 */
@Composable
fun LanPairPrompt(dialogs: Dialogs) {
    val ui by Lan.ui.collectAsState()
    val req = ui.pending.firstOrNull()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(req?.id) {
        if (req == null) {
            // o pedido expirou ou foi respondido pela notificação: fecha a janela que mostrava esse pedido
            if (shown != null) { dialogs.close(); shown = null }
            return@LaunchedEffect
        }
        shown = req.id
        dialogs.confirm(
            "Permitir acesso pela rede?",
            "${req.label} (${req.address}) digitou o código certo e quer ver e alterar seus dados do Finan+.\n\nSó permita se foi você, agora, neste computador.",
            ok = "Permitir",
            cancel = "Recusar",
            onCancel = { Lan.deny(req.id); shown = null },
        ) { Lan.approve(req.id); shown = null }
    }
}

/** Seção "Acesso pela rede" em Ajustes. */
@Composable
fun LanSettings(lan: LanUi) {
    val p = Fin.c
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val scope = rememberCoroutineScope()
    var caVersion by remember { mutableIntStateOf(0) }
    // impressão digital da CA (se já existir); ler o Keystore fora da thread principal
    val caFp by produceState(initialValue = lan.caSha256, lan.caSha256, caVersion) {
        value = lan.caSha256.ifEmpty { withContext(Dispatchers.IO) { LanCa.peek(ctx)?.sha256 ?: "" } }
    }

    val saveCa = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-x509-ca-cert")) { uri ->
        AppLock.endExternal()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val der = LanCa.get(ctx).certDer
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(der) } != null
                }.getOrDefault(false)
            }
            caVersion++
            if (ok) dialogs.notice("Certificado salvo", "Leve o arquivo para o computador (cabo, pendrive) e instale como autoridade no navegador. Antes de confiar, confira a impressão digital mostrada aqui.")
            else dialogs.notice("Não foi possível salvar", "Tente de novo ou escolha outro local.")
        }
    }

    when {
        lan.starting -> Text("Iniciando… (na primeira vez o celular cria o certificado, pode levar alguns segundos)", color = p.muted)
        lan.running -> {
            // Um endereço só: o de instalação. Na primeira vez ele ensina a instalar o certificado;
            // depois, abre o Finan+ direto no endereço seguro (https).
            Text("No computador ou tablet, na mesma rede Wi-Fi, abra no navegador:", color = p.muted)
            Text(lan.setupUrl, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.accent2).padding(17.dp)) {
                Eyebrow("Código de pareamento")
                Text(lan.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Depois do código, o celular pergunta se você permite o aparelho.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            Text(
                "Na primeira vez, a página mostra como instalar o certificado deste celular. Nas próximas, ela abre o Finan+ direto, com conexão segura.",
                style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 8.dp),
            )
            if (lan.devices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Eyebrow("Aparelhos conectados")
                lan.devices.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(d.label, fontWeight = FontWeight.SemiBold)
                            Text(d.address, style = MaterialTheme.typography.bodySmall, color = p.muted)
                        }
                        Pill("Desconectar", danger = true) { Lan.revoke(d.id) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Pill("Parar servidor", Modifier.fillMaxWidth(), danger = true) { Lan.stop(ctx) }
        }
        else -> {
            lan.message?.let { Text(it, color = p.red, modifier = Modifier.padding(bottom = 8.dp)) }
            Text(
                "Liga um servidor seguro (HTTPS) neste celular, só na sua rede Wi-Fi. Os dados continuam aqui; o outro aparelho vê e lança enquanto o servidor estiver ligado. Desliga sozinho após ${Lan.config.idleMillis / 60_000} min sem uso.",
                color = p.muted, modifier = Modifier.padding(bottom = 10.dp),
            )
            Pill("Iniciar servidor", Modifier.fillMaxWidth()) {
                dialogs.confirm(
                    "Ligar acesso pela rede?",
                    "Use em redes de confiança (sua casa). A conexão é criptografada (HTTPS) e cada aparelho precisa do código e da sua permissão aqui no celular.",
                    ok = "Ligar",
                ) { Lan.start(ctx) }
            }
        }
    }

    // ---------- certificado (autoridade) deste celular ----------
    Spacer(Modifier.height(16.dp))
    Eyebrow("Certificado deste celular")
    Text(
        "Instale uma vez no navegador do computador para abrir sem aviso de segurança. Ele só vale para endereços da rede local: não serve para nenhum site da internet.",
        style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(vertical = 4.dp),
    )
    if (caFp.isNotEmpty()) {
        Text("Impressão digital (SHA-256) — confira no navegador:", style = MaterialTheme.typography.bodySmall, color = p.muted)
        Text(
            fingerprintLines(caFp), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.accent2.copy(alpha = 0.6f)).padding(10.dp),
        )
    } else {
        Text("Ainda não criado: será criado ao iniciar o servidor ou ao salvar o arquivo.", style = MaterialTheme.typography.bodySmall, color = p.muted)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Salvar certificado", Modifier.weight(1f)) { AppLock.allowExternalOnce(); saveCa.launch("finanplus-ca.crt") }
        Pill("Gerar novo", Modifier.weight(1f), danger = true) {
            dialogs.confirm(
                "Gerar novo certificado?",
                "O certificado atual deixa de valer: os computadores onde ele foi instalado voltam a mostrar aviso até você instalar o novo (e remover o antigo da lista de autoridades). Use se achar que alguém copiou o certificado ou se trocou de computador.",
                ok = "Gerar novo", danger = true,
            ) {
                Lan.stop(ctx)
                scope.launch {
                    withContext(Dispatchers.IO) { LanCa.reset(ctx) }
                    Lan.set { it.copy(caSha256 = "") }
                    caVersion++
                }
            }
        }
    }
}

/** "AB:CD:…" em 4 linhas de 8 bytes, mais fácil de comparar. */
internal fun fingerprintLines(fp: String): String =
    fp.split(':').chunked(8).joinToString("\n") { it.joinToString(":") }
