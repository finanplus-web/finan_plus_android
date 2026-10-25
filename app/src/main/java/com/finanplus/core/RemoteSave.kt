// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

/**
 * Gravação vinda do navegador (acesso pela rede): protege as reservas para meta.
 *
 * O navegador manda o estado inteiro de volta, montado pelo Finan+ web embutido no app. Enquanto o
 * Finan+ web não conhecer o campo `goalId`, ele devolve as reservas para meta sem esse campo, e o
 * celular as gravaria como despesas comuns da categoria "Metas" (entrariam no Resumo, nos limites e
 * no assistente). Aqui o celular restaura o `goalId` a partir do que ele já tem e recusa o que o
 * navegador não sabe fazer direito com uma reserva: alterar ou excluir o lançamento, ou excluir a
 * meta que ainda tem dinheiro guardado pelo Finan+. Essas ações continuam disponíveis no celular.
 */
object RemoteSave {
    fun protectGoalReserves(current: AppState, incoming: AppState): Outcome {
        val reserves = current.txs.filter { it.isGoal }
        if (reserves.isEmpty()) return Outcome.Ok(incoming)

        val byId = incoming.txs.associateBy { it.id }
        val keep = HashMap<String, Tx>()
        for (t in reserves) {
            val got = byId[t.id] ?: return Outcome.Err(TITLE,
                "\"${t.desc}\" é uma reserva para meta e só pode ser excluída no celular. Nada foi gravado.")
            val same = got.kind == t.kind && got.value == t.value && got.date == t.date &&
                got.accountId == t.accountId && got.cardId.isEmpty() && got.cardPayment.isEmpty() &&
                (got.goalId.isEmpty() || got.goalId == t.goalId)
            if (!same) return Outcome.Err(TITLE,
                "\"${t.desc}\" é uma reserva para meta e só pode ser alterada no celular, pela própria meta. Nada foi gravado.")
            keep[t.id] = t
        }

        val goalIds = incoming.goals.mapTo(HashSet()) { it.id }
        reserves.map { it.goalId }.distinct().forEach { gid ->
            if (gid !in goalIds && current.goals.any { it.id == gid }) {
                val name = current.goals.first { it.id == gid }.name
                return Outcome.Err(TITLE,
                    "A meta \"$name\" tem dinheiro guardado pelo Finan+ e só pode ser excluída no celular, para o valor voltar à conta. Nada foi gravado.")
            }
        }

        return Outcome.Ok(incoming.copy(txs = incoming.txs.map { keep[it.id] ?: it }))
    }

    private const val TITLE = "Reserva para meta"
}
