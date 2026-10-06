// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import com.finanplus.core.AppState
import com.finanplus.core.Backup
import com.finanplus.core.BackupException
import com.finanplus.core.Finance
import com.finanplus.core.Outcome
import com.finanplus.widget.BalanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Problema encontrado ao abrir os dados (mostrado ao usuário até ele decidir). */
sealed class LoadProblem {
    /** arquivo não decifrou; [copied] = uma cópia (cifrada) foi guardada */
    data class Unreadable(val copied: Boolean) : LoadProblem()
    /** erro do Keystore talvez passageiro: o arquivo está intacto e nada é gravado até o usuário decidir */
    data class KeystoreError(val detail: String) : LoadProblem()
    /** decifrou, mas o conteúdo era inválido; [raw] pode ser salvo pelo usuário */
    data class Invalid(val raw: String) : LoadProblem()
    data class Dropped(val count: Int) : LoadProblem()
}

/**
 * Fonte única dos dados do app. Toda alteração passa por [update]/[replace]/[commit],
 * é gravada criptografada em segundo plano (sempre a versão mais recente) e atualiza o widget.
 */
object Repo {
    private const val BLOCKED_MSG = "As alterações não estão sendo salvas até você decidir o que fazer com o arquivo de dados."
    private lateinit var app: Context
    private lateinit var store: SecureStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    @Volatile private var lastWritten: AppState? = null
    @Volatile private var saveBlocked = false

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    private val _problem = MutableStateFlow<LoadProblem?>(null)
    val problem: StateFlow<LoadProblem?> = _problem

    /**
     * Falha ao gravar (disco cheio, Keystore…): mensagem para um aviso fixo na tela, ou null.
     * Também fica preenchido enquanto a gravação está bloqueada por um problema de abertura.
     */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError
    @Volatile private var retryScheduled = false

    @Volatile var ready = false; private set

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        app = context.applicationContext
        store = SecureStore(app)
        load()
        ready = true
        runRecurring()
    }

    private fun load() {
        when (val r = store.read()) {
            is SecureStore.ReadResult.Empty -> { saveBlocked = false; _state.value = AppState() }
            is SecureStore.ReadResult.Ok -> try {
                val n = Backup.parse(r.json)
                saveBlocked = false
                _state.value = n.state
                lastWritten = n.state
                if (n.dropped.total > 0) _problem.value = LoadProblem.Dropped(n.dropped.total)
            } catch (e: BackupException) {
                // conteúdo inválido: guarda cópia (cifrada) antes de qualquer gravação; sem cópia, não grava
                saveBlocked = store.keepEncryptedCopy() == null
                _problem.value = LoadProblem.Invalid(r.json)
                _state.value = AppState()
            }
            is SecureStore.ReadResult.Unreadable -> {
                // nunca grava por cima até o usuário escolher "começar do zero" (com ou sem cópia guardada)
                saveBlocked = true
                _problem.value = LoadProblem.Unreadable(copied = r.copy != null)
                _state.value = AppState()
            }
            is SecureStore.ReadResult.KeystoreError -> {
                saveBlocked = true
                _problem.value = LoadProblem.KeystoreError(r.message)
                _state.value = AppState()
            }
        }
        _saveError.value = if (saveBlocked) BLOCKED_MSG else null
    }

    /** "Tentar de novo" após [LoadProblem.KeystoreError]: relê o arquivo (que não foi tocado). */
    @Synchronized
    fun retryLoad(): Boolean {
        // continua bloqueado durante a releitura (o Keystore pode levar ~1 s): uma alteração feita nesse
        // intervalo gravaria o estado vazio por cima do arquivo intacto. load() libera só se a leitura der certo.
        saveBlocked = true
        _problem.value = null
        lastWritten = null
        load()
        runRecurring()
        return _problem.value == null
    }

    /** Gera lançamentos de recorrências pendentes até hoje. */
    fun runRecurring(today: LocalDate = LocalDate.now()): Int {
        var added = 0
        _state.update { s -> val (n, c) = Finance.generateRecurring(s, today); added = c; n }
        if (added > 0) persist()
        return added
    }

    fun update(f: (AppState) -> AppState) { _state.update(f); persist() }

    fun replace(s: AppState) { _state.value = s; persist() }

    /** Aplica o resultado de uma operação; retorna o erro (para exibir) ou null. */
    fun commit(o: Outcome): Outcome.Err? = when (o) {
        is Outcome.Ok -> { replace(o.state); null }
        is Outcome.Err -> o
    }

    /** Fecha um aviso informativo (registros ignorados, conteúdo inválido com cópia guardada). Não libera gravação bloqueada. */
    fun dismissProblem() { if (saveBlocked) return; _problem.value = null; persist() }

    /**
     * Escolha explícita do usuário: começar do zero. O arquivo atual já tem cópia cifrada guardada
     * (ou não pôde ser copiado, e o usuário confirmou que aceita perdê-lo). Libera a gravação.
     */
    fun startFresh() {
        _problem.value = null
        saveBlocked = false
        _saveError.value = null
        persist()
    }

    private fun persist() {
        if (saveBlocked) { _saveError.value = BLOCKED_MSG; return }
        scope.launch {
            val ok = writeLock.withLock {
                val s = _state.value // sempre a versão mais recente
                if (s == lastWritten) return@withLock true
                try { store.write(Backup.toJson(s)); lastWritten = s; true } catch (e: Exception) { false }
            }
            if (ok) {
                _saveError.value = null
                BalanceWidget.refresh(app)
            } else {
                // antes a falha era engolida em silêncio; agora aparece um aviso fixo e o app tenta de novo
                _saveError.value = "Não foi possível salvar as últimas alterações. Verifique o espaço livre no aparelho e faça um Backup JSON."
                if (!retryScheduled) {
                    retryScheduled = true
                    scope.launch { delay(15_000); retryScheduled = false; persist() }
                }
            }
        }
    }

    /** Força a gravação imediata (usado antes de sair/apagar). */
    suspend fun flush() = writeLock.withLock {
        val s = _state.value
        if (!saveBlocked && s != lastWritten) { store.write(Backup.toJson(s)); lastWritten = s; _saveError.value = null }
    }

    /** Apaga TODOS os dados deste aparelho: arquivo, cópias, chave e configurações (inclusive o PIN). */
    fun wipe() {
        scope.launch {
            writeLock.withLock {
                store.wipe()
                DevicePrefs.get(app).wipe()
                lastWritten = null
                saveBlocked = false
                _problem.value = null
                _saveError.value = null
                com.finanplus.notify.Reminders.cancel(app) // a última notificação (títulos, valores) some junto
                _state.value = AppState()
                store.write(Backup.toJson(_state.value)); lastWritten = _state.value
            }
            BalanceWidget.refresh(app)
        }
    }
}
