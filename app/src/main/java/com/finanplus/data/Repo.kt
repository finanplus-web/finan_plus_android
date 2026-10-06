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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Problema encontrado ao abrir os dados (mostrado uma vez ao usuário). */
sealed class LoadProblem {
    /** arquivo não decifrou; cópia guardada */
    data object Unreadable : LoadProblem()
    /** decifrou, mas o conteúdo era inválido; [raw] pode ser salvo pelo usuário */
    data class Invalid(val raw: String) : LoadProblem()
    data class Dropped(val count: Int) : LoadProblem()
}

/**
 * Fonte única dos dados do app. Toda alteração passa por [update]/[replace]/[commit],
 * é gravada criptografada em segundo plano (sempre a versão mais recente) e atualiza o widget.
 */
object Repo {
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

    @Volatile var ready = false; private set

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        app = context.applicationContext
        store = SecureStore(app)
        when (val r = store.read()) {
            is SecureStore.ReadResult.Empty -> _state.value = AppState()
            is SecureStore.ReadResult.Ok -> try {
                val n = Backup.parse(r.json)
                _state.value = n.state
                lastWritten = n.state
                if (n.dropped.total > 0) _problem.value = LoadProblem.Dropped(n.dropped.total)
            } catch (e: BackupException) {
                // conteúdo inválido: guarda cópia (cifrada) antes de qualquer gravação
                store.keepEncryptedCopy()
                _problem.value = LoadProblem.Invalid(r.json)
                _state.value = AppState()
            }
            is SecureStore.ReadResult.Unreadable -> {
                saveBlocked = r.copy == null // sem cópia, não sobrescreve até o usuário decidir
                _problem.value = LoadProblem.Unreadable
                _state.value = AppState()
            }
        }
        ready = true
        runRecurring()
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

    fun dismissProblem() { _problem.value = null; saveBlocked = false; persist() }

    private fun persist() {
        if (saveBlocked) return
        scope.launch {
            writeLock.withLock {
                val s = _state.value // sempre a versão mais recente
                if (s == lastWritten) return@withLock
                try { store.write(Backup.toJson(s)); lastWritten = s } catch (e: Exception) { /* tenta de novo na próxima alteração */ }
            }
            BalanceWidget.refresh(app)
        }
    }

    /** Força a gravação imediata (usado antes de sair/apagar). */
    suspend fun flush() = writeLock.withLock {
        val s = _state.value
        if (!saveBlocked && s != lastWritten) { store.write(Backup.toJson(s)); lastWritten = s }
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
                _state.value = AppState()
                store.write(Backup.toJson(_state.value)); lastWritten = _state.value
            }
            BalanceWidget.refresh(app)
        }
    }
}
