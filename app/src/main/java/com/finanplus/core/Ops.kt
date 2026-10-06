// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate

/** Resultado de uma operação: novo estado ou mensagem para o usuário. */
sealed class Outcome {
    data class Ok(val state: AppState) : Outcome()
    data class Err(val title: String, val message: String) : Outcome()
}

private fun err(msg: String, title: String = "Revise os dados") = Outcome.Err(title, msg)
private fun ok(s: AppState) = Outcome.Ok(s)
private fun sameName(a: String, b: String) = a.lowercase() == b.lowercase()

enum class RepsMode { TOTAL, EACH }

data class TxDraft(
    val kind: Kind,
    val desc: String,
    val value: String,
    val category: String,
    val date: LocalDate?,
    val paid: Boolean,
    val accountId: String,
    /** vazio = conta/dinheiro */
    val cardId: String = "",
    val reps: Int = 1,
    val repsMode: RepsMode = RepsMode.TOTAL,
    val recurring: Boolean = false,
)

/** Todas as alterações de dados passam por aqui, com as mesmas validações do PWA. */
object Ops {
    // ---------------- lançamentos ----------------
    fun saveTx(s: AppState, editId: String?, d: TxDraft): Outcome {
        val desc = d.desc.trim().take(TX_DESC_MAX)
        val value = Money.parse(d.value)
        val card = d.kind == Kind.EXPENSE && d.cardId.isNotEmpty()
        if (desc.isEmpty()) return err("Informe uma descrição.", "Campo obrigatório")
        if (value == null || value <= 0) return err("Informe um valor maior que zero. Ex.: 59,90", "Valor inválido")
        val date = d.date ?: return err("Informe uma data válida.", "Data inválida")
        if (card && s.card(d.cardId) == null) return err("Cadastre um cartão em Ajustes antes de lançar no cartão.", "Sem cartão")
        val accountId = if (s.account(d.accountId) != null) d.accountId else s.accounts[0].id
        val category = d.category.ifBlank { s.cats.of(d.kind)[0] }
        val cardId = if (card) d.cardId else ""
        val paid = card || d.paid

        if (editId != null) {
            val t = s.txs.firstOrNull { it.id == editId } ?: return err("Lançamento não encontrado.")
            val upd = if (t.cardPayment.isNotEmpty())
                t.copy(desc = desc, value = value, category = category, date = date, accountId = accountId, paid = true)
            else t.copy(kind = d.kind, desc = desc, value = value, category = category, date = date, paid = paid, accountId = accountId, cardId = cardId)
            return ok(s.copy(txs = s.txs.map { if (it.id == editId) upd else it }))
        }

        val n = d.reps.coerceIn(1, 60)
        if (n > 1 && d.recurring) return err("Escolha parcelas ou repetição mensal, não os dois.", "Revise o lançamento")
        val base = Tx(Ids.new(), d.kind, value, date, desc, category, paid, accountId, cardId)
        val added = if (n > 1) {
            val values = if (d.repsMode == RepsMode.EACH) List(n) { value } else Finance.splitInstallments(value, n)
            if (values.any { it <= 0 }) return err("O valor é pequeno demais para tantas parcelas.", "Valor inválido")
            val group = Ids.new()
            values.mapIndexed { i, v ->
                base.copy(id = Ids.new(), value = v, date = date.plusMonthsClamped(i.toLong()), paid = card || (i == 0 && d.paid),
                    desc = parcelDesc(desc, i + 1, n), groupId = group, parcelN = i + 1, parcelTotal = n)
            }
        } else listOf(base)
        var rec = s.recurring
        // a recorrência guarda até 120 caracteres (o limite lido de volta do arquivo); sem cortar aqui, o fim se perdia na próxima abertura
        if (d.recurring) rec = rec + Recurring(Ids.new(), d.kind, desc.take(REC_DESC_MAX).trimEnd(), value, category, accountId, cardId, date.dayOfMonth, true, date, date.ym())
        return ok(s.copy(txs = s.txs + added, recurring = rec))
    }

    const val TX_DESC_MAX = 200
    const val REC_DESC_MAX = 120

    /** "Notebook (3/10)": corta a descrição antes do sufixo para o total caber em [TX_DESC_MAX] (senão o sufixo sumia ao reler). */
    fun parcelDesc(desc: String, n: Int, total: Int): String {
        val suffix = " ($n/$total)"
        return desc.take(TX_DESC_MAX - suffix.length).trimEnd() + suffix
    }

    /** Parcelas seguintes do mesmo grupo (para perguntar se exclui junto). */
    fun laterParcels(s: AppState, id: String): List<Tx> {
        val t = s.txs.firstOrNull { it.id == id } ?: return emptyList()
        if (t.groupId.isEmpty()) return emptyList()
        return s.txs.filter { it.groupId == t.groupId && it.id != t.id && !it.date.isBefore(t.date) }
    }

    fun deleteTx(s: AppState, id: String, withLater: Boolean): AppState {
        val ids = HashSet<String>().apply { add(id); if (withLater) laterParcels(s, id).forEach { add(it.id) } }
        return s.copy(txs = s.txs.filterNot { it.id in ids })
    }

    /** Pago/pendente. Compra no cartão e pagamento de fatura não alternam: desmarcar um pagamento de fatura
     *  reabria a fatura e deixava o pagamento pendente, descontando o mesmo valor duas vezes. */
    fun canTogglePaid(t: Tx) = !t.isCard && t.isFlow

    fun togglePaid(s: AppState, id: String): AppState =
        s.copy(txs = s.txs.map { if (it.id == id && canTogglePaid(it)) it.copy(paid = !it.paid) else it })

    // ---------------- metas ----------------
    fun saveGoal(s: AppState, id: String?, name: String, target: String, move: String, deadline: LocalDate?, monthly: String): Outcome {
        val n = name.trim().take(60)
        val t = Money.parse(target)
        val m = if (monthly.isBlank()) 0L else Money.parse(monthly)
        if (n.isEmpty()) return err("Informe o nome da meta.")
        if (t == null || t <= 0) return err("Informe um valor de meta maior que zero. Ex.: 1500,50")
        if (m == null || m < 0) return err("Contribuição mensal inválida.")
        val mv = if (move.isBlank()) 0L else Money.parse(move) ?: return err("Valor a guardar inválido.")
        if (id == null) return ok(s.copy(goals = s.goals + Goal(Ids.new(), n, t, 0, deadline, m)))
        return ok(s.copy(goals = s.goals.map { if (it.id == id) it.copy(name = n, target = t, deadline = deadline, monthly = m, saved = maxOf(0L, it.saved + mv)) else it }))
    }

    fun deleteGoal(s: AppState, id: String) = s.copy(goals = s.goals.filterNot { it.id == id })

    // ---------------- contas ----------------
    fun saveAccount(s: AppState, id: String?, name: String, initial: String): Outcome {
        val n = name.trim().take(40)
        val ini = if (initial.isBlank()) 0L else Money.parse(initial)
        if (n.isEmpty()) return err("Informe o nome da conta.")
        if (ini == null) return err("Saldo inicial inválido. Ex.: 1.250,00 ou -300")
        if (id == null) return ok(s.copy(accounts = s.accounts + Account(Ids.new(), n, ini)))
        return ok(s.copy(accounts = s.accounts.map { if (it.id == id) it.copy(name = n, initial = ini) else it }))
    }

    fun deleteAccount(s: AppState, id: String): Outcome {
        if (s.accounts.size <= 1) return err("Mantenha pelo menos uma conta.", "Conta necessária")
        if (s.txs.any { it.accountId == id && !it.isCard }) return err("Mova ou exclua os lançamentos desta conta antes.", "Conta em uso")
        if (s.recurring.any { it.accountId == id && it.cardId.isEmpty() }) return err("Há recorrências usando esta conta. Edite ou exclua essas recorrências antes.", "Conta em uso")
        val rest = s.accounts.filterNot { it.id == id }
        val first = rest[0].id
        return ok(s.copy(
            accounts = rest,
            txs = s.txs.map { if (it.accountId == id) it.copy(accountId = first) else it },
            recurring = s.recurring.map { if (it.accountId == id) it.copy(accountId = first) else it },
        ))
    }

    // ---------------- cartões ----------------
    fun saveCard(s: AppState, id: String?, name: String, limit: String, close: String, due: String): Outcome {
        val n = name.trim().take(40)
        val lim = if (limit.isBlank()) 0L else Money.parse(limit)
        val c = close.trim().toIntOrNull(); val d = due.trim().toIntOrNull()
        if (n.isEmpty()) return err("Informe o nome do cartão.")
        if (lim == null || lim < 0) return err("Limite inválido.")
        if (c == null || d == null || c !in 1..31 || d !in 1..31) return err("Os dias de fechamento e vencimento devem estar entre 1 e 31.")
        if (id == null) return ok(s.copy(cards = s.cards + Card(Ids.new(), n, lim, c, d)))
        return ok(s.copy(cards = s.cards.map { if (it.id == id) it.copy(name = n, limit = lim, close = c, due = d) else it }))
    }

    fun deleteCard(s: AppState, id: String): Outcome {
        if (s.txs.any { it.cardId == id || it.cardPayment == id }) return err("Este cartão tem compras ou pagamentos registrados. Exclua esses lançamentos antes de excluir o cartão.", "Cartão em uso")
        if (s.recurring.any { it.cardId == id }) return err("Há recorrências usando este cartão. Edite ou exclua essas recorrências antes.", "Cartão em uso")
        return ok(s.copy(cards = s.cards.filterNot { it.id == id }))
    }

    fun payInvoice(s: AppState, cardId: String, value: String, accountId: String, date: LocalDate?): Outcome {
        val c = s.card(cardId) ?: return err("Cartão não encontrado.")
        val v = Money.parse(value)
        if (v == null || v <= 0) return err("Informe um valor maior que zero.")
        if (date == null) return err("Informe uma data válida.")
        val acc = if (s.account(accountId) != null) accountId else s.accounts[0].id
        return ok(s.copy(txs = s.txs + Tx(Ids.new(), Kind.EXPENSE, v, date, "Pagamento fatura ${c.name}", AppState.CARD_PAYMENT_CAT, true, acc, cardPayment = c.id)))
    }

    // ---------------- recorrências ----------------
    fun saveRecurring(
        s: AppState, id: String?, kind: Kind, desc: String, value: String, day: String, category: String,
        accountId: String, cardId: String, active: Boolean, start: LocalDate?, today: LocalDate,
    ): Outcome {
        val ds = desc.trim().take(REC_DESC_MAX); val v = Money.parse(value); val dd = day.trim().toIntOrNull()
        if (ds.isEmpty()) return err("Informe uma descrição.")
        if (v == null || v <= 0) return err("Informe um valor maior que zero.")
        if (dd == null || dd !in 1..31) return err("O dia deve estar entre 1 e 31.")
        val acc = if (s.account(accountId) != null) accountId else s.accounts[0].id
        val card = if (kind == Kind.EXPENSE && s.card(cardId) != null) cardId else ""
        val cat = category.ifBlank { s.cats.of(kind)[0] }
        val next = if (id == null) {
            if (start == null) return err("Informe a data de início.")
            s.copy(recurring = s.recurring + Recurring(Ids.new(), kind, ds, v, cat, acc, card, dd, true, start, null))
        } else s.copy(recurring = s.recurring.map {
            if (it.id != id) it
            else it.copy(kind = kind, desc = ds, value = v, day = dd, category = cat, accountId = acc, cardId = card, active = active,
                last = if (!it.active && active) resumedLast(it.last, today) else it.last)
        })
        return ok(Finance.generateRecurring(next, today).first)
    }

    /** Ao reativar uma recorrência pausada, os meses parados não geram lançamento: retoma a partir do mês atual.
     *  (Antes, reativar em outubro uma recorrência pausada em março criava 7 lançamentos pendentes de uma vez.) */
    fun resumedLast(last: java.time.YearMonth?, today: LocalDate): java.time.YearMonth {
        val prev = today.ym().minusMonths(1)
        return if (last != null && last.isAfter(prev)) last else prev
    }

    fun deleteRecurring(s: AppState, id: String) = s.copy(recurring = s.recurring.filterNot { it.id == id })

    // ---------------- limites ----------------
    fun saveLimit(s: AppState, old: String?, category: String, value: String): Outcome {
        val v = Money.parse(value)
        if (category.isBlank()) return err("Escolha uma categoria.")
        if (v == null || v <= 0) return err("Informe um valor maior que zero.")
        val m = LinkedHashMap(s.limits)
        if (old != null && old != category) m.remove(old)
        m[category] = v
        return ok(s.copy(limits = m))
    }

    fun deleteLimit(s: AppState, category: String) = s.copy(limits = s.limits - category)

    // ---------------- categorias ----------------
    fun addCategory(s: AppState, kind: Kind, name: String): Outcome {
        val n = name.trim().take(40)
        if (n.isEmpty()) return err("Digite o nome da categoria.", "Nome vazio")
        if (s.cats.of(kind).any { sameName(it, n) }) return err("Essa categoria já existe.", "Categoria duplicada")
        return ok(s.copy(cats = s.cats.with(kind, s.cats.of(kind) + n)))
    }

    fun renameCategory(s: AppState, kind: Kind, old: String, name: String): Outcome {
        val n = name.trim().take(40)
        if (n.isEmpty()) return err("Informe um nome.", "Nome vazio")
        if (n == old) return ok(s)
        if (s.cats.of(kind).any { it != old && sameName(it, n) }) return err("Essa categoria já existe.", "Categoria duplicada")
        val limits = if (kind == Kind.EXPENSE && old in s.limits) LinkedHashMap(s.limits).apply { put(n, remove(old)!!) } else s.limits
        return ok(s.copy(
            cats = s.cats.with(kind, s.cats.of(kind).map { if (it == old) n else it }),
            txs = s.txs.map { if (it.kind == kind && it.category == old) it.copy(category = n) else it },
            recurring = s.recurring.map { if (it.kind == kind && it.category == old) it.copy(category = n) else it },
            limits = limits,
        ))
    }

    /** Verifica se pode excluir; retorna a mensagem de confirmação (ou erro). */
    fun checkDeleteCategory(s: AppState, kind: Kind, name: String): Outcome {
        if (s.cats.of(kind).size <= 1) return err("Mantenha pelo menos uma categoria de ${if (kind == Kind.EXPENSE) "despesa" else "receita"}.", "Categoria necessária")
        if (s.recurring.any { it.kind == kind && it.category == name }) return err("Esta categoria está sendo usada por uma recorrência. Altere ou exclua a recorrência primeiro.", "Categoria em uso")
        return ok(s)
    }

    fun deleteCategory(s: AppState, kind: Kind, name: String) = s.copy(
        cats = s.cats.with(kind, s.cats.of(kind).filterNot { it == name }),
        limits = if (kind == Kind.EXPENSE) s.limits - name else s.limits,
    )

    fun categoryUseCount(s: AppState, kind: Kind, name: String) = s.txs.count { it.kind == kind && it.category == name }
}
