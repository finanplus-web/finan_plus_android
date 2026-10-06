// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** "Hoje" compartilhado pelas telas. Muda sozinho quando o dia muda (ver [rememberToday]). */
val LocalToday = staticCompositionLocalOf { LocalDate.now() }

/**
 * Data de hoje que acompanha o relógio do aparelho. Atualiza:
 * 1. na virada da meia-noite, com o app aberto;
 * 2. quando o app volta do segundo plano;
 * 3. quando o usuário muda a data, a hora ou o fuso nas configurações do Android.
 */
@Composable
fun rememberToday(): LocalDate {
    val ctx = LocalContext.current
    var today by remember { mutableStateOf(LocalDate.now()) }

    // 1) espera até a próxima meia-noite (recalcula a cada volta: nunca fica preso num dia)
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val ms = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
            delay(ms.coerceIn(1_000, 3_600_000) + 500) // no máximo 1 h por espera (o relógio do aparelho pode mudar)
            today = LocalDate.now()
        }
    }
    // 2) voltou ao app
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
    // 3) data, hora ou fuso alterados no sistema
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { today = LocalDate.now() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    return today
}
