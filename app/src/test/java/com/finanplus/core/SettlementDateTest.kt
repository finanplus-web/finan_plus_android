// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SettlementDateTest {
    private fun date(value: String) = LocalDate.parse(value)

    @Test
    fun marcarReceitaFuturaComoRecebidaUsaDataRealEPermiteDesfazer() {
        val today = date("2026-10-17")
        val due = date("2026-10-30")
        val income = Tx("salary", Kind.INCOME, 150_000, due, "Salário", "Salário", false, "main")
        val original = AppState(txs = listOf(income))

        val settled = Ops.togglePaid(original, income.id, today)
        val paid = settled.txs.single()
        assertTrue(paid.paid)
        assertEquals(today, paid.date)
        assertEquals(due, paid.scheduledDate)
        assertEquals(150_000, Finance.monthFlow(settled, today.ym()).income)
        assertEquals(150_000, Finance.currentBalance(settled))

        val reopened = Ops.togglePaid(settled, income.id, today.plusDays(1))
        val pending = reopened.txs.single()
        assertFalse(pending.paid)
        assertEquals(due, pending.date)
        assertNull(pending.scheduledDate)
        assertEquals(0, Finance.currentBalance(reopened))
    }

    @Test
    fun marcarDespesaFuturaComoPagaRegistraSaidaHoje() {
        val today = date("2026-10-17")
        val due = date("2026-10-30")
        val expense = Tx("rent", Kind.EXPENSE, 80_000, due, "Aluguel", "Moradia", false, "main")
        val settled = Ops.togglePaid(AppState(txs = listOf(expense)), expense.id, today)
        val paid = settled.txs.single()
        assertTrue(paid.paid)
        assertEquals(today, paid.date)
        assertEquals(due, paid.scheduledDate)
        assertEquals(80_000, Finance.monthFlow(settled, today.ym()).expense)
        assertEquals(-80_000, Finance.currentBalance(settled))
    }

    @Test
    fun salvarComoPagoComDataFuturaContaNoDiaDoPagamento() {
        val today = date("2026-10-17")
        val due = date("2026-10-30")
        val state = AppState()
        val draft = TxDraft(Kind.INCOME, "Salário", "1500,00", "Salário", due, true, "main")
        val saved = (Ops.saveTx(state, null, draft, today) as Outcome.Ok).state.txs.single()
        assertEquals(today, saved.date)
        assertEquals(due, saved.scheduledDate)
    }

    @Test
    fun dataAgendadaSobreviveAoBackupEBackupsAntigosContinuamValidos() {
        val today = date("2026-10-17")
        val due = date("2026-10-30")
        val paid = Ops.togglePaid(
            AppState(txs = listOf(Tx("salary", Kind.INCOME, 150_000, due, "Salário", "Salário", false, "main"))),
            "salary",
            today,
        ).txs.single()
        val restored = Backup.parse(Backup.toJson(AppState(txs = listOf(paid)))).state.txs.single()
        assertEquals(today, restored.date)
        assertEquals(due, restored.scheduledDate)

        val oldFormat = Backup.parse("""{"txs":[{"id":"old","kind":"income","value":1500,"date":"2026-10-30","paid":false}]}""").state.txs.single()
        assertEquals(date("2026-10-30"), oldFormat.date)
        assertNull(oldFormat.scheduledDate)
    }
}
