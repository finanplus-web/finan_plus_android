// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

/**
 * Transferência entre contas (TRANSFERENCIAS.md).
 *
 * Uma transferência são dois lançamentos com o mesmo [Tx.transferId]: uma saída ([Kind.EXPENSE]) na conta de
 * origem e uma entrada ([Kind.INCOME]) na conta de destino, com o mesmo valor e a mesma data, sempre realizados.
 * Cada conta muda de saldo, mas o saldo total não: por isso a transferência fica fora de receitas, despesas,
 * limites, relatórios e assistente ([Tx.isFlow] = false). Sem Android: testado em JVM.
 */
object Transfers {
    /** Os dois lados de uma transferência: [out] sai da conta de origem, [into] entra na conta de destino. */
    data class Legs(val out: Tx, val into: Tx) {
        val id get() = out.transferId
        val value get() = out.value
        val date get() = out.date
        val fromId get() = out.accountId
        val toId get() = into.accountId
    }

    /** Os dois lados da transferência [transferId] (null se não existe ou está incompleta). */
    fun legs(s: AppState, transferId: String): Legs? {
        if (transferId.isEmpty()) return null
        val g = s.txs.filter { it.transferId == transferId }
        if (g.size != 2) return null
        val out = g.firstOrNull { it.kind == Kind.EXPENSE } ?: return null
        val into = g.firstOrNull { it.kind == Kind.INCOME } ?: return null
        return Legs(out, into)
    }

    /** Os dois lados da transferência de que [t] faz parte. */
    fun legsOf(s: AppState, t: Tx): Legs? = if (t.isTransfer) legs(s, t.transferId) else null

    /** Os dois lados combinam: um sai e outro entra, mesmo valor e data, contas diferentes, fora do cartão. */
    private fun valid(a: Tx, b: Tx): Boolean =
        a.kind != b.kind && a.value == b.value && a.date == b.date && a.accountId != b.accountId &&
            a.paid && b.paid && !a.isCard && !b.isCard && a.cardPayment.isEmpty() && b.cardPayment.isEmpty() &&
            a.goalId.isEmpty() && b.goalId.isEmpty()

    /**
     * Arquivo de backup ou gravação pela rede: um lado sozinho ou um par que não combina deixa de ser transferência
     * (vira receita ou despesa comum, para o valor continuar visível e editável em vez de mexer no saldo escondido).
     */
    fun sanitize(txs: List<Tx>): List<Tx> {
        if (txs.none { it.isTransfer }) return txs
        val ok = txs.filter { it.isTransfer }.groupBy { it.transferId }
            .filter { (_, g) -> g.size == 2 && valid(g[0], g[1]) }.keys
        return txs.map { if (it.isTransfer && it.transferId !in ok) it.copy(transferId = "") else it }
    }

    /** "Conta principal → Poupança" (nomes atuais das contas; conta que não existe mais vira "Conta"). */
    fun route(s: AppState, l: Legs): String = "${s.account(l.fromId)?.name ?: "Conta"} → ${s.account(l.toId)?.name ?: "Conta"}"

    /** Descrição gravada nos dois lados: o que a pessoa escreveu ou, sem nada, "Transferência". */
    fun desc(text: String): String = text.trim().take(Ops.TX_DESC_MAX).ifBlank { AppState.TRANSFER_CAT }
}
