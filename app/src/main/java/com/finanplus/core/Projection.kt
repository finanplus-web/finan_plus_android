// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate

/**
 * Recorrências previstas: as próximas ocorrências de cada recorrência ativa nos meses que ainda não chegaram.
 *
 * Elas não são gravadas. O lançamento real continua sendo criado quando o mês chega ([Finance.generateRecurring]);
 * até lá, calendário, Lista e saldos previstos mostram a ocorrência como "Previsto". Mudar, pausar ou excluir a
 * recorrência muda (ou some com) todas as previstas na hora. Mesmas regras do Finan+ web e do Linux.
 * Detalhes em RECORRENCIAS.md.
 */
object Projection {
    /** prefixo do id de um lançamento previsto: "prev:<id da recorrência>:<AAAA-MM>" (nunca vai para o estado) */
    const val PREFIX = "prev:"

    /**
     * Ocorrências previstas com data entre [from] e [to] (inclusive), só em meses depois do mês de [today]
     * e depois do último mês já gerado; nunca antes do início da recorrência. Ordenadas por data.
     */
    fun between(s: AppState, from: LocalDate, to: LocalDate, today: LocalDate): List<Tx> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<Tx>()
        val cur = today.ym()
        for (r in s.recurring) {
            if (!r.active) continue
            var m = maxOf(cur.plusMonths(1), from.ym())
            r.last?.let { if (it.plusMonths(1).isAfter(m)) m = it.plusMonths(1) }
            r.start?.let { if (it.ym().isAfter(m)) m = it.ym() }
            while (!m.isAfter(to.ym())) {
                val date = m.dayClamped(r.day)
                if (!date.isBefore(from) && !date.isAfter(to) && (r.start == null || !date.isBefore(r.start)))
                    out.add(Tx("$PREFIX${r.id}:$m", r.kind, r.value, date, r.desc, r.category, r.cardId.isNotEmpty(), r.accountId, r.cardId, recurringId = r.id))
                m = m.plusMonths(1)
            }
        }
        return out.sortedWith(compareBy<Tx> { it.date }.thenBy { it.id })
    }
}

/** lançamento previsto de uma recorrência (não existe no estado) */
val Tx.isProjected: Boolean get() = id.startsWith(Projection.PREFIX)
