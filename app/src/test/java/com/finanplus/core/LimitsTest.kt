// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Limites de quantidade (AppState.Limits): valem para backups externos e para a criação de itens novos,
 * mas nunca impedem o app de abrir os dados que ele mesmo já salvou.
 */
class LimitsTest {
    private val today = LocalDate.parse("2026-10-17")

    private fun backupWithAccounts(n: Int): String {
        val accounts = (1..n).joinToString(",") { """{"id":"a$it","name":"Conta $it","initial":0}""" }
        return """{"txs":[],"accounts":[$accounts]}"""
    }

    @Test fun backupExternoAcimaDoLimiteERecusado() {
        assertFailsWith<BackupException> { Backup.parse(backupWithAccounts(AppState.Limits.ACCOUNTS + 1)) }
    }

    @Test fun dadosDoProprioAppAcimaDoLimiteContinuamAbrindo() {
        val n = Backup.parse(backupWithAccounts(AppState.Limits.ACCOUNTS + 5), enforceLimits = false)
        assertEquals(AppState.Limits.ACCOUNTS + 5, n.state.accounts.size)
    }

    @Test fun naoCriaContaAlemDoLimite() {
        val accounts = (1..AppState.Limits.ACCOUNTS).map { Account("a$it", "Conta $it", 0) }
        val s = AppState(accounts = accounts)
        assertTrue(Ops.saveAccount(s, null, "Mais uma", "") is Outcome.Err)
    }

    @Test fun quemJaPassouDoLimiteAindaEditaEExclui() {
        val accounts = (1..AppState.Limits.ACCOUNTS + 3).map { Account("a$it", "Conta $it", 0) }
        val s = AppState(accounts = accounts)
        val edited = Ops.saveAccount(s, "a2", "Renomeada", "10") as? Outcome.Ok
        kotlin.test.assertNotNull(edited)
        assertEquals("Renomeada", edited.state.account("a2")?.name)
        assertTrue(Ops.deleteAccount(s, "a3") is Outcome.Ok)
        // e lança movimentações normalmente (lançamentos não estão no limite)
        assertTrue(Ops.saveTx(s, null, TxDraft(Kind.EXPENSE, "Mercado", "10", "Alimentação", today, true, "a1"), today) is Outcome.Ok)
    }

    @Test fun backupGeradoPeloAppNoLimiteEhRestauravel() {
        val accounts = (1..AppState.Limits.ACCOUNTS).map { Account("a$it", "Conta $it", 0) }
        val text = Backup.toJson(AppState(accounts = accounts))
        assertEquals(AppState.Limits.ACCOUNTS, Backup.parse(text).state.accounts.size)
    }
}
