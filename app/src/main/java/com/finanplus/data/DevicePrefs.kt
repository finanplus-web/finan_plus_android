// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Configurações que pertencem a ESTE aparelho e nunca vão para o backup:
 * PIN, biometria, notificações, widget, bloqueio de capturas de tela e opções do assistente.
 * O PIN é guardado só como hash PBKDF2 com sal.
 */
data class DeviceSettings(
    val pinHash: String = "",
    val biometric: Boolean = false,
    val notifications: Boolean = true,
    val widgetValues: Boolean = true,
    val secureScreen: Boolean = true,
    // ---- assistente (cada função pode ser desligada separadamente) ----
    /** sugerir categoria pela descrição ao criar lançamentos */
    val assistCategory: Boolean = true,
    /** resumo do mês e dicas no Início */
    val assistTips: Boolean = true,
    /** perguntas rápidas */
    val assistAsk: Boolean = true,
    /** dicas que o usuário dispensou (ids estáveis, ver core/assist/Insights.kt) */
    val dismissedTips: Set<String> = emptySet(),
) {
    val hasPin get() = pinHash.isNotEmpty()
    /** O app pede desbloqueio se houver PIN, digital, ou os dois. */
    val lockEnabled get() = hasPin || biometric
}

class DevicePrefs private constructor(context: Context) {
    private val sp = context.getSharedPreferences("finanplus_device", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<DeviceSettings> = _flow
    val value get() = _flow.value

    private fun load() = DeviceSettings(
        pinHash = sp.getString("pin", "") ?: "",
        biometric = sp.getBoolean("biometric", false),
        notifications = sp.getBoolean("notifications", true),
        widgetValues = sp.getBoolean("widgetValues", true),
        secureScreen = sp.getBoolean("secureScreen", true),
        assistCategory = sp.getBoolean("assistCategory", true),
        assistTips = sp.getBoolean("assistTips", true),
        assistAsk = sp.getBoolean("assistAsk", true),
        dismissedTips = sp.getStringSet("dismissedTips", emptySet())?.toSet() ?: emptySet(),
    )

    fun update(f: (DeviceSettings) -> DeviceSettings) {
        val n = f(_flow.value)
        sp.edit(commit = true) {
            putString("pin", n.pinHash); putBoolean("biometric", n.biometric); putBoolean("notifications", n.notifications)
            putBoolean("widgetValues", n.widgetValues); putBoolean("secureScreen", n.secureScreen)
            remove("materialIcons") // preferência da fase de teste dos ícones, não é mais usada
            putBoolean("assistCategory", n.assistCategory); putBoolean("assistTips", n.assistTips); putBoolean("assistAsk", n.assistAsk)
            // guarda só as últimas 200 dicas dispensadas (os ids incluem o mês, então as antigas deixam de importar)
            putStringSet("dismissedTips", HashSet(n.dismissedTips.toList().takeLast(200)))
        }
        _flow.value = n
    }

    /** Já pedimos a permissão de notificação ao usuário (pede só uma vez). */
    var askedNotifications: Boolean
        get() = sp.getBoolean("askedNotifications", false)
        set(v) = sp.edit { putBoolean("askedNotifications", v) }

    /** Marca o que já foi notificado hoje, para não repetir a mesma notificação. */
    var lastNotified: String
        get() = sp.getString("lastNotified", "") ?: ""
        set(v) = sp.edit { putString("lastNotified", v) }

    fun wipe() { sp.edit(commit = true) { clear() }; _flow.value = load() }

    companion object {
        @Volatile private var inst: DevicePrefs? = null
        fun get(context: Context): DevicePrefs = inst ?: synchronized(this) { inst ?: DevicePrefs(context.applicationContext).also { inst = it } }
    }
}
