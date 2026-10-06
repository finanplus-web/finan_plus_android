// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import com.finanplus.core.assist.Dictionary

/** Carrega (uma vez) o dicionário aberto do assistente: `assets/assistente/dicionario.txt`. */
object AssistData {
    const val DICTIONARY_PATH = "assistente/dicionario.txt"
    @Volatile private var dict: Dictionary? = null

    fun dictionary(context: Context): Dictionary = dict ?: synchronized(this) {
        dict ?: (try {
            context.applicationContext.assets.open(DICTIONARY_PATH).bufferedReader(Charsets.UTF_8).use { Dictionary.parse(it.readText()) }
        } catch (e: Exception) {
            Dictionary(emptyList()) // sem o arquivo, o assistente só usa o aprendizado
        }).also { dict = it }
    }
}
