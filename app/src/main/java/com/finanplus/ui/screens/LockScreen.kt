// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.MainActivity
import com.finanplus.data.DeviceSettings
import com.finanplus.security.AppLock
import com.finanplus.security.Pin
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.launch

@Composable
fun LockScreen(activity: MainActivity, dev: DeviceSettings) {
    val p = Fin.c
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val bio = dev.biometric && (activity.canUseBiometric() || !dev.hasPin)
    val ask = { activity.promptBiometric(withPinFallback = dev.hasPin, onFail = { msg = it }) }
    LaunchedEffect(Unit) {
        // Só digital, mas o aparelho não tem mais digital nem bloqueio de tela: não há como verificar,
        // então a trava por digital é desligada (quem tem o aparelho já tem acesso a tudo nele).
        if (!dev.hasPin && !activity.canUseDeviceAuth()) {
            com.finanplus.data.DevicePrefs.get(activity).update { it.copy(biometric = false) }
            AppLock.unlock(); return@LaunchedEffect
        }
        if (bio) ask()
    }

    fun submit() {
        if (busy || pin.length < 4) return
        val wait = AppLock.waitSeconds()
        if (wait > 0) { msg = "Muitas tentativas. Aguarde $wait s."; pin = ""; return }
        busy = true
        scope.launch {
            if (Pin.verify(pin, dev.pinHash)) { msg = ""; AppLock.unlock() }
            else { AppLock.registerFail(); msg = if (AppLock.waitSeconds() > 0) "PIN incorreto. Aguarde para tentar de novo." else "PIN incorreto." }
            pin = ""; busy = false
        }
    }

    if (!dev.hasPin) {
        // Só digital: um botão grande; a alternativa é o bloqueio de tela do Android.
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("Finan+", style = MaterialTheme.typography.headlineMedium)
            Text("App bloqueado", color = p.muted)
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(p.accent).clickable(role = Role.Button) { ask() }
                    .semantics { contentDescription = "Desbloquear com digital" },
                contentAlignment = Alignment.Center,
            ) { com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.FINGERPRINT, p.onAccent, size = 40.dp) }
            Spacer(Modifier.height(16.dp))
            TextButton({ ask() }) { Text("Desbloquear com digital", fontWeight = FontWeight.Bold) }
            Text(msg, color = p.red, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        return
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Finan+", style = MaterialTheme.typography.headlineMedium)
        Text(if (bio) "Digite seu PIN ou use a digital" else "Digite seu PIN", color = p.muted)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { contentDescription = "${pin.length} dígitos digitados" }) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) p.accent else p.track))
            }
        }
        Text(msg, color = p.red, modifier = Modifier.padding(top = 12.dp).height(22.dp).semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(8.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (bio) "bio" else "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                row.forEach { k ->
                    val label = when (k) { "bio" -> "Usar digital ou rosto"; "⌫" -> "Apagar"; else -> k }
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .then(if (k.isEmpty()) Modifier else Modifier.background(p.surface).border(1.dp, p.border, CircleShape).clickable(role = Role.Button) {
                                when (k) {
                                    "bio" -> ask()
                                    "⌫" -> pin = pin.dropLast(1)
                                    else -> if (pin.length < 8) { pin += k; if (pin.length == dev.pinLengthHint()) submit() }
                                }
                            }.semantics { contentDescription = label }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "bio") com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.FINGERPRINT, p.text, size = 26.dp)
                        else Text(k, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton({ submit() }, enabled = pin.length >= 4 && !busy) { Text("Entrar", fontWeight = FontWeight.Bold) }
    }
}

/** Tamanho do PIN não é guardado (seria uma dica); o envio é automático só a partir de 8 dígitos. */
private fun DeviceSettings.pinLengthHint() = 8
