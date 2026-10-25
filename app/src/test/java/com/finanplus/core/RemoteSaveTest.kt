// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Acesso pela rede: o Finan+ web devolve as reservas para meta sem `goalId`; o celular as protege. */
class RemoteSaveTest {
    private val today = LocalDate.parse("2026-10-17")
    private fun AppState.ok(o: Outcome): AppState = when (o) { is Outcome.Ok -> o.state; is Outcome.Err -> error("esperava Ok: ${o.message}") }

    /** Estado do celular com uma reserva de R$ 300 para a meta Viagem e uma despesa comum. */
    private fun phone(): AppState {
        val base = AppState(accounts = listOf(Account("main", "Conta principal", 100000)))
        val g = base.ok(Ops.saveGoal(base, null, "Viagem", "6000", "", null, ""))
        val s = g.ok(Ops.saveGoal(g, g.goals[0].id, "Viagem", "6000", "300", null, "", fromAccount = true, accountId = "main", today = today))
        return s.copy(txs = s.txs + Tx("c1", Kind.EXPENSE, 4500, today, "Feira", "Alimentação", true, "main"))
    }

    /** O que o navegador manda de volta: tudo igual, mas sem o goalId (como o Finan+ web normaliza hoje). */
    private fun fromBrowser(s: AppState) = s.copy(txs = s.txs.map { it.copy(goalId = "") })

    @Test fun restauraOGoalIdQueONavegadorPerdeu() {
        val s = phone()
        val back = fromBrowser(s).let { it.copy(txs = it.txs.map { t -> if (t.id == "c1") t.copy(desc = "Feira do sábado") else t }) }
        val merged = s.ok(RemoteSave.protectGoalReserves(s, back))
        val reserve = merged.txs.single { it.category == AppState.GOAL_CAT }
        assertTrue(reserve.isGoal)
        assertEquals(s.goals[0].id, reserve.goalId)
        assertEquals(4500L, Finance.flow(merged.txs).expense) // a reserva continua fora das despesas
        assertEquals("Feira do sábado", merged.txs.single { it.id == "c1" }.desc) // a edição comum passa
    }

    @Test fun recusaExcluirOuAlterarAReservaPeloNavegador() {
        val s = phone()
        val reserveId = s.txs.single { it.isGoal }.id
        val deleted = fromBrowser(s).let { it.copy(txs = it.txs.filter { t -> t.id != reserveId }) }
        assertIs<Outcome.Err>(RemoteSave.protectGoalReserves(s, deleted))
        val changed = fromBrowser(s).let { it.copy(txs = it.txs.map { t -> if (t.id == reserveId) t.copy(value = 99) else t }) }
        assertIs<Outcome.Err>(RemoteSave.protectGoalReserves(s, changed))
    }

    @Test fun recusaExcluirAMetaComDinheiroGuardado() {
        val s = phone()
        val noGoal = fromBrowser(s).copy(goals = emptyList())
        assertIs<Outcome.Err>(RemoteSave.protectGoalReserves(s, noGoal))
    }

    @Test fun semReservasNadaMuda() {
        val s = AppState(txs = listOf(Tx("c1", Kind.EXPENSE, 4500, today, "Feira", "Alimentação", true, "main")))
        val back = s.copy(txs = emptyList())
        assertEquals(back, s.ok(RemoteSave.protectGoalReserves(s, back)))
    }
}
