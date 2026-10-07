// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import com.finanplus.core.Account
import com.finanplus.core.AppState
import com.finanplus.core.Card
import com.finanplus.core.Finance
import com.finanplus.core.Goal
import com.finanplus.core.Kind
import com.finanplus.core.Recurring
import com.finanplus.core.Tx
import com.finanplus.core.dayClamped
import com.finanplus.core.ym
import java.time.LocalDate
import java.time.YearMonth

/**
 * Dados de demonstração (fictícios) para as capturas de tela da documentação.
 * Os mesmos do Finan+ web (tools/demo-data.js), sempre relativos à data de hoje.
 */
object DemoData {
    fun state(today: LocalDate = LocalDate.now()): AppState {
        var n = 0
        val txs = ArrayList<Tx>()
        fun add(kind: Kind, value: Long, date: LocalDate, desc: String, category: String, paid: Boolean = true,
                card: String = "", payment: String = "", group: String = "", parcelN: Int = 0, parcelTotal: Int = 0) {
            txs += Tx("demo${n++}", kind, value, date, desc, category, paid, "main", card, payment, "", group, parcelN, parcelTotal)
        }
        val cur = today.ym()
        fun idx(m: YearMonth) = m.year * 12 + m.monthValue - 1 // mesmo índice de mês do web (paridade das variações)
        for (back in 5 downTo 0) {
            val m = cur.minusMonths(back.toLong())
            val past = m.isBefore(cur)
            val mi = idx(m)
            fun d(day: Int) = m.dayClamped(day)
            fun until(day: Int) = past || !d(day).isAfter(today)
            add(Kind.INCOME, 520000, d(5), "Salário", "Salário", until(5))
            if (mi % 2 == 0) add(Kind.INCOME, 65000L + (mi % 5) * 10000, d(18), "Freela de design", "Extra", until(18))
            add(Kind.EXPENSE, 160000, d(10), "Aluguel", "Moradia", until(10))
            add(Kind.EXPENSE, 18500L + (mi % 3) * 1200, d(15), "Conta de luz", "Moradia", until(15))
            add(Kind.EXPENSE, 9990, d(20), "Internet fibra", "Moradia", until(20))
            add(Kind.EXPENSE, if (m == cur) 5590 else 4490, d(8), "Netflix", "Lazer", card = "nu")
            add(Kind.EXPENSE, 2190, d(2), "Spotify", "Lazer", card = "nu")
            for ((day, v, desc, cat) in listOf(
                Quad(3, 32000, "Supermercado Pão de Açúcar", "Alimentação"), Quad(9, 8900, "iFood pizza", "Alimentação"),
                Quad(12, 2400, "Uber casa", "Transporte"), Quad(14, 27500, "Mercado Extra", "Alimentação"),
                Quad(16, 1800, "Uber trabalho", "Transporte"), Quad(21, 12000, "Posto Shell", "Transporte"),
                Quad(23, 15000, "Cinema e pipoca", "Lazer"), Quad(25, 6990, "Drogasil", "Saúde"), Quad(27, 21000, "Feira do mês", "Alimentação"),
            )) {
                if (!until(day)) continue
                add(Kind.EXPENSE, v + ((mi * 7L + day) % 9) * 150, d(day), desc, cat, card = if (day % 3 == 0) "nu" else "")
            }
            if (past) add(Kind.EXPENSE, 120000, d(12), "Pagamento fatura Roxinho", AppState.CARD_PAYMENT_CAT, payment = "nu")
        }
        listOf("Café", "Pão de queijo", "Café", "Água", "Café", "Bala", "Café", "Pão de queijo", "Café", "Suco", "Café").forEachIndexed { i, desc ->
            val date = cur.dayClamped(1 + i * 2)
            if (!date.isAfter(today) && date.monthValue == cur.monthValue) add(Kind.EXPENSE, 650L + (i % 3) * 300, date, desc, "Alimentação")
        }
        for (i in 1..6) add(Kind.EXPENSE, 60000, cur.plusMonths(i - 2L).dayClamped(22), "Notebook ($i/6)", "Educação",
            card = "nu", group = "nb", parcelN = i, parcelTotal = 6)
        val s = AppState(
            txs = txs,
            accounts = listOf(Account("main", "Conta principal", 185000), Account("pp", "Poupança", 420000)),
            cards = listOf(Card("nu", "Roxinho", 450000, 5, 12)),
            goals = listOf(
                Goal("g1", "Viagem de férias", 800000, 310000, cur.plusMonths(8).dayClamped(30), 60000),
                Goal("g2", "Reserva de emergência", 1500000, 1120000, null, 50000),
            ),
            recurring = listOf(Recurring("r1", Kind.EXPENSE, "Academia", 11990, "Saúde", "main", "", 6, true, cur.minusMonths(3).dayClamped(6), cur.minusMonths(1))),
            limits = mapOf("Alimentação" to 120000L, "Lazer" to 40000L, "Transporte" to 45000L),
        )
        return Finance.generateRecurring(s, today).first
    }

    private data class Quad(val day: Int, val v: Int, val desc: String, val cat: String)
}
