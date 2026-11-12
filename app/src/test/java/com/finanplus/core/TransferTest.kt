// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Transferência entre contas (TRANSFERENCIAS.md): muda o saldo de cada conta, mas não é receita nem despesa. */
class TransferTest {
    private val today = LocalDate.parse("2026-11-10")
    private fun AppState.ok(o: Outcome): AppState = when (o) { is Outcome.Ok -> o.state; is Outcome.Err -> error("esperava Ok: ${o.message}") }

    private val base = AppState(accounts = listOf(Account("main", "Conta principal", 100000), Account("poup", "Poupança", 0)))

    /** R$ 250 da Conta principal para a Poupança, mais uma despesa comum de R$ 45 no mesmo dia. */
    private fun withTransfer(): AppState {
        val s = base.ok(Ops.saveTransfer(base, null, "main", "poup", "250", today, "", today))
        return s.copy(txs = s.txs + Tx("c1", Kind.EXPENSE, 4500, today, "Feira", "Alimentação", true, "main"))
    }

    @Test fun criaOsDoisLadosSemContarComoReceitaOuDespesa() {
        val s = withTransfer()
        val legs = s.txs.filter { it.isTransfer }
        assertEquals(2, legs.size)
        assertEquals(1, legs.map { it.transferId }.distinct().size)
        val l = assertNotNull(Transfers.legs(s, legs[0].transferId))
        assertEquals("main", l.fromId); assertEquals("poup", l.toId); assertEquals(25000L, l.value)
        assertTrue(legs.all { it.paid && !it.isFlow && it.category == AppState.TRANSFER_CAT && it.desc == "Transferência" })
        // cada conta muda...
        assertEquals(100000L - 25000 - 4500, Finance.accountBalance(s, s.account("main")!!))
        assertEquals(25000L, Finance.accountBalance(s, s.account("poup")!!))
        // ...o total não
        assertEquals(100000L - 4500, Finance.currentBalance(s))
        // e não entra em receitas, despesas, categorias, limites nem no total do dia
        assertEquals(Flow(0L, 4500L), Finance.flow(s.txs))
        assertEquals(listOf("Alimentação"), Finance.categoryTotals(s, null, null).map { it.first })
        assertEquals(setOf("Alimentação"), Finance.budgetUsage(s, today.ym()).keys)
        assertEquals(DayTotal(-4500L, 0L, true), Period.dayTotal(s.txs))
        assertTrue(legs.none { Ops.canTogglePaid(it) })
    }

    @Test fun totaisDoMesECalendarioIgnoramATransferencia() {
        val s = withTransfer()
        val m = Totals.month(s, today.ym(), today)
        assertEquals(0L, m.realizedIn)
        assertEquals(4500L, m.realizedOut)
        assertEquals(Finance.currentBalance(s), m.opening)
        val day = MonthCalendar.build(s, today.ym(), today).getValue(today)
        assertEquals(0L, day.income); assertEquals(4500L, day.expense)
        // só a transferência no dia: o dia aparece na lista, mas sem total
        val only = base.ok(Ops.saveTransfer(base, null, "main", "poup", "10", today, "", today))
        assertFalse(MonthCalendar.build(only, today.ym(), today).getValue(today).hasFlow)
        assertFalse(Period.dayTotal(only.txs).hasFlow)
    }

    @Test fun validaContasValorEData() {
        assertIs<Outcome.Err>(Ops.saveTransfer(base, null, "main", "main", "10", today, "", today))
        assertIs<Outcome.Err>(Ops.saveTransfer(base, null, "main", "poup", "0", today, "", today))
        assertIs<Outcome.Err>(Ops.saveTransfer(base, null, "main", "xyz", "10", today, "", today))
        assertIs<Outcome.Err>(Ops.saveTransfer(base, null, "main", "poup", "10", today.plusDays(1), "", today))
        assertIs<Outcome.Err>(Ops.saveTransfer(base, null, "main", "poup", "10", null, "", today))
        val one = AppState(accounts = listOf(Account("main", "Conta principal", 0)))
        assertIs<Outcome.Err>(Ops.saveTransfer(one, null, "main", "main", "10", today, "", today))
        // data passada pode
        assertIs<Outcome.Ok>(Ops.saveTransfer(base, null, "main", "poup", "10", today.minusDays(3), "Guardar", today))
    }

    @Test fun editarMudaOsDoisLadosJuntos() {
        val s = withTransfer()
        val tid = s.txs.first { it.isTransfer }.transferId
        val e = s.ok(Ops.saveTransfer(s, tid, "poup", "main", "100", today.minusDays(1), "Volta", today))
        val l = assertNotNull(Transfers.legs(e, tid))
        assertEquals("poup", l.fromId); assertEquals("main", l.toId)
        assertEquals(10000L, l.value); assertEquals(today.minusDays(1), l.date)
        assertEquals("Volta", l.out.desc); assertEquals("Volta", l.into.desc)
        assertEquals(s.txs.size, e.txs.size)
        assertEquals(-10000L, Finance.accountBalance(e, e.account("poup")!!))
        // pelo formulário comum de lançamento não dá
        assertIs<Outcome.Err>(Ops.saveTx(e, l.out.id, TxDraft(Kind.EXPENSE, "x", "5", "Outros", today, true, "main"), today))
    }

    @Test fun excluirUmLadoExcluiOsDois() {
        val s = withTransfer()
        val leg = s.txs.first { it.isTransfer && it.kind == Kind.INCOME }
        val d = Ops.deleteTx(s, leg.id, false)
        assertTrue(d.txs.none { it.isTransfer })
        assertEquals(listOf("c1"), d.txs.map { it.id })
        assertEquals(100000L - 4500, Finance.accountBalance(d, d.account("main")!!))
    }

    @Test fun contaComTransferenciaNaoPodeSerExcluida() {
        val s = withTransfer()
        assertIs<Outcome.Err>(Ops.deleteAccount(s, "poup"))
    }

    @Test fun backupGuardaERestauraATransferencia() {
        val s = withTransfer()
        val back = Backup.parse(Backup.toJson(s)).state
        val tid = s.txs.first { it.isTransfer }.transferId
        val l = assertNotNull(Transfers.legs(back, tid))
        assertEquals(25000L, l.value)
        assertEquals(Finance.flow(s.txs), Finance.flow(back.txs))
        assertEquals(Finance.currentBalance(s), Finance.currentBalance(back))
    }

    @Test fun backupComUmLadoSoViraLancamentoComum() {
        val s = withTransfer()
        val half = s.copy(txs = s.txs.filterNot { it.isTransfer && it.kind == Kind.INCOME })
        val back = Backup.parse(Backup.toJson(half)).state
        assertTrue(back.txs.none { it.isTransfer })
        // o lado que sobrou continua visível como despesa comum
        assertEquals(25000L + 4500, Finance.flow(back.txs).expense)
        // lados que não combinam (valores diferentes) também
        val tid = s.txs.first { it.isTransfer }.transferId
        val wrong = s.copy(txs = s.txs.map { if (it.transferId == tid && it.kind == Kind.INCOME) it.copy(value = 1) else it })
        assertNull(Transfers.legs(AppState(txs = Transfers.sanitize(wrong.txs)), tid))
    }

    @Test fun navegadorNaoPerdeNemAlteraATransferencia() {
        val s = withTransfer()
        // o Finan+ web devolve tudo sem o transferId
        val browser = s.copy(txs = s.txs.map { it.copy(transferId = "") })
        val merged = s.ok(RemoteSave.protectTransfers(s, browser))
        assertEquals(2, merged.txs.count { it.isTransfer })
        assertEquals(Flow(0L, 4500L), Finance.flow(merged.txs))
        // alterar ou excluir um lado pelo navegador é recusado
        val changed = browser.copy(txs = browser.txs.map { if (it.category == AppState.TRANSFER_CAT && it.kind == Kind.EXPENSE) it.copy(value = 1) else it })
        assertIs<Outcome.Err>(RemoteSave.protectTransfers(s, changed))
        val removed = browser.copy(txs = browser.txs.filterNot { it.category == AppState.TRANSFER_CAT && it.kind == Kind.INCOME })
        assertIs<Outcome.Err>(RemoteSave.protectTransfers(s, removed))
        // editar um lançamento comum pelo navegador continua valendo
        val edited = browser.copy(txs = browser.txs.map { if (it.id == "c1") it.copy(desc = "Feira do sábado") else it })
        assertEquals("Feira do sábado", s.ok(RemoteSave.protectTransfers(s, edited)).txs.single { it.id == "c1" }.desc)
    }

    @Test fun csvMarcaATransferencia() {
        val csv = Csv.build(withTransfer())
        assertTrue("transferência enviada" in csv)
        assertTrue("transferência recebida" in csv)
    }
}
