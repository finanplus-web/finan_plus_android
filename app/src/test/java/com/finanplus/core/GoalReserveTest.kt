// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import com.finanplus.core.assist.Insights
import com.finanplus.core.assist.MoneyFmt
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reserva para meta (1.7.0): guardar tira da conta, mas não é despesa. */
class GoalReserveTest {
    private val today = LocalDate.parse("2026-10-17")
    private fun AppState.ok(o: Outcome): AppState = when (o) { is Outcome.Ok -> o.state; is Outcome.Err -> error("esperava Ok: ${o.message}") }

    private fun withGoal(): Pair<AppState, String> {
        val base = AppState(accounts = listOf(Account("main", "Conta principal", 100000), Account("poup", "Poupança", 0)))
        val s = base.ok(Ops.saveGoal(base, null, "Viagem", "6000", "", null, ""))
        return s to s.goals[0].id
    }

    @Test fun guardarTiraDaContaSemContarComoDespesa() {
        val (s0, id) = withGoal()
        val s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "300", null, "", fromAccount = true, accountId = "main", today = today))
        assertEquals(30000L, s.goals[0].saved)
        val t = s.txs.single()
        assertTrue(t.isGoal); assertFalse(t.isFlow); assertTrue(t.paid)
        assertEquals(Kind.EXPENSE, t.kind); assertEquals(AppState.GOAL_CAT, t.category); assertEquals("Meta › Viagem", t.desc)
        // sai do saldo da conta...
        assertEquals(70000L, Finance.accountBalance(s, s.accounts[0]))
        // ...mas não é despesa: fora do resumo, das categorias e dos limites
        assertEquals(0L, Finance.flow(s.txs).expense)
        assertTrue(Finance.categoryTotals(s, null, null).isEmpty())
        assertTrue(Finance.budgetUsage(s, today.ym()).isEmpty())
        assertFalse(Ops.canTogglePaid(t))
        assertEquals(Finance.GoalMoves(30000L, 0L), Finance.goalMoves(s.txs))
    }

    @Test fun retirarDevolveParaAContaEParaNoZero() {
        val (s0, id) = withGoal()
        var s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "100", null, "", true, "poup", today))
        // retirar mais do que tem: a meta para no zero e só volta o que havia
        s = s.ok(Ops.saveGoal(s, id, "Viagem", "6000", "-250", null, "", true, "poup", today))
        assertEquals(0L, s.goals[0].saved)
        val back = s.txs.last()
        assertEquals(Kind.INCOME, back.kind); assertEquals(10000L, back.value); assertEquals("Retirada da meta › Viagem", back.desc)
        assertEquals(0L, Finance.accountBalance(s, s.account("poup")!!))
        assertEquals(0L, Finance.flow(s.txs).income)
    }

    @Test fun semTirarDaContaFicaComoAntes() {
        val (s0, id) = withGoal()
        val s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "300", null, "", fromAccount = false, accountId = "main", today = today))
        assertEquals(30000L, s.goals[0].saved)
        assertTrue(s.txs.isEmpty())
        assertEquals(100000L, Finance.accountBalance(s, s.accounts[0]))
    }

    @Test fun excluirLancamentoDesfazNaMeta() {
        val (s0, id) = withGoal()
        var s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "300", null, "", true, "main", today))
        s = Ops.deleteTx(s, s.txs.single().id, false)
        assertEquals(0L, s.goals[0].saved)
        assertEquals(100000L, Finance.accountBalance(s, s.accounts[0]))
    }

    @Test fun editarLancamentoDeMetaERecusado() {
        val (s0, id) = withGoal()
        val s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "300", null, "", true, "main", today))
        val o = Ops.saveTx(s, s.txs.single().id, TxDraft(Kind.EXPENSE, "x", "1", "Outros", today, true, "main"), today)
        assertTrue(o is Outcome.Err)
    }

    @Test fun excluirMetaDevolveOQueSaiuDasContas() {
        val (s0, id) = withGoal()
        var s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "300", null, "", true, "main", today))
        // 50 guardados sem tirar da conta não voltam (nunca saíram)
        s = s.ok(Ops.saveGoal(s, id, "Viagem", "6000", "50", null, "", false, "main", today))
        assertEquals(30000L, Ops.goalFromAccounts(s, id))
        s = Ops.deleteGoal(s, id, today)
        assertTrue(s.goals.isEmpty())
        assertEquals("Meta excluída › Viagem", s.txs.last().desc)
        assertEquals(100000L, Finance.accountBalance(s, s.accounts[0]))
        assertEquals(0L, Finance.flow(s.txs).income)
    }

    @Test fun backupLevaOGoalId() {
        val (s0, id) = withGoal()
        val s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "300", null, "", true, "main", today))
        val again = Backup.parse(Backup.toJson(s)).state
        assertEquals(id, again.txs.single().goalId)
        assertFalse(again.txs.single().isFlow)
        assertEquals(Backup.toJson(s), Backup.toJson(again))
    }

    @Test fun assistenteNaoVeReservaComoGasto() {
        val (s0, id) = withGoal()
        val s = s0.ok(Ops.saveGoal(s0, id, "Viagem", "6000", "5000", null, "", true, "main", today))
        val money: MoneyFmt = { Money.format(it) }
        val r = Insights.report(s, today, money)
        assertTrue(r.lines.none { it.contains("50,00") || it.contains("5.000") }, r.lines.joinToString())
    }
}
