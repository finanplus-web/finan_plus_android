// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth

/** Todos os valores em dinheiro são Long em centavos: nada de ponto flutuante. */
typealias Cents = Long

enum class Kind(val json: String) {
    INCOME("income"), EXPENSE("expense");
    companion object { fun of(s: Any?): Kind? = entries.firstOrNull { it.json == s } }
}

enum class ThemeId(val json: String, val label: String) {
    AUTO("auto", "Sistema"),
    LIGHT("light", "Claro"),
    GRUVBOX("gruvbox", "Gruvbox"),
    OLED("oledGray", "OLED Cinza"),
    GRUVBOX_OLED("gruvboxOled", "Gruvbox OLED"),
    TOKYO("tokyo", "Tokyo Night"),
    NORD("nord", "Nord");
    companion object {
        // "dark" (versões antigas) vira OLED Cinza; o Material You ("materialBlue") saiu e vira Claro,
        // o tema mais parecido (sem isso cairia em Sistema e poderia ficar escuro de repente)
        fun of(s: Any?): ThemeId = when (s) {
            "dark" -> OLED
            "materialBlue" -> LIGHT
            else -> entries.firstOrNull { it.json == s } ?: AUTO
        }
    }
}

data class Tx(
    val id: String,
    val kind: Kind,
    val value: Cents,
    val date: LocalDate,
    val desc: String,
    val category: String,
    val paid: Boolean,
    val accountId: String,
    /** compra feita no cartão (entra na fatura) */
    val cardId: String = "",
    /** pagamento da fatura deste cartão (debita a conta, não é despesa nova) */
    val cardPayment: String = "",
    val recurringId: String = "",
    val groupId: String = "",
    val parcelN: Int = 0,
    val parcelTotal: Int = 0,
    /** Data prevista preservada quando um lançamento pendente é realizado em outro dia. */
    val scheduledDate: LocalDate? = null,
    /** Reserva para meta: dinheiro que saiu da conta para esta meta (despesa) ou voltou dela (receita).
     *  Mexe no saldo da conta, mas não é gasto nem ganho: fica fora de despesas, limites e assistente. */
    val goalId: String = "",
) {
    /** Receita ou despesa de verdade (fica de fora: pagamento de fatura e reserva para meta) */
    val isFlow get() = cardPayment.isEmpty() && goalId.isEmpty()
    val isCard get() = cardId.isNotEmpty()
    val isGoal get() = goalId.isNotEmpty()
}

data class Account(val id: String, val name: String, val initial: Cents)

data class Card(val id: String, val name: String, val limit: Cents, val close: Int, val due: Int)

data class Goal(
    val id: String,
    val name: String,
    val target: Cents,
    val saved: Cents,
    val deadline: LocalDate?,
    val monthly: Cents,
)

data class Recurring(
    val id: String,
    val kind: Kind,
    val desc: String,
    val value: Cents,
    val category: String,
    val accountId: String,
    val cardId: String,
    /** 1..31, limitado ao último dia de cada mês */
    val day: Int,
    val active: Boolean,
    val start: LocalDate?,
    val last: YearMonth?,
)

data class Categories(val expense: List<String>, val income: List<String>) {
    fun of(k: Kind) = if (k == Kind.EXPENSE) expense else income
    fun with(k: Kind, list: List<String>) = if (k == Kind.EXPENSE) copy(expense = list) else copy(income = list)
}

/** Estado completo do app. É o que vai para o backup JSON (compatível com o Finan+ PWA). */
data class AppState(
    val txs: List<Tx> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val accounts: List<Account> = listOf(Account(MAIN_ACCOUNT, "Conta principal", 0)),
    val cards: List<Card> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val cats: Categories = Categories(DEFAULT_EXPENSE, DEFAULT_INCOME),
    /** categoria de despesa → limite mensal */
    val limits: Map<String, Cents> = emptyMap(),
    val privacy: Boolean = false,
    /** minutos em segundo plano até bloquear; 0 = desativado */
    val autoLock: Int = 0,
    val theme: ThemeId = ThemeId.AUTO,
) {
    fun account(id: String) = accounts.firstOrNull { it.id == id }
    fun card(id: String) = cards.firstOrNull { it.id == id }

    /** Garante que valores e somas permaneçam exatos em Long e no Number do PWA. */
    fun hasSafeMoneyTotals(): Boolean {
        val cap = java.math.BigInteger("9007199254740991") // Number.MAX_SAFE_INTEGER, em centavos
        val max = java.math.BigInteger.valueOf(Money.MAX_ABS)
        var total = java.math.BigInteger.ZERO
        fun add(value: Cents): Boolean {
            val n = java.math.BigInteger.valueOf(value).abs()
            if (n > max) return false
            total = total.add(n)
            return total <= cap
        }
        return txs.all { add(it.value) } && accounts.all { add(it.initial) } && cards.all { add(it.limit) } &&
            goals.all { add(it.target) && add(it.saved) && add(it.monthly) } &&
            recurring.all { add(it.value) } && limits.values.all(::add)
    }

    /** Mensagem de erro se [next] cria itens além de [Limits] (null = dentro do limite). Só conta quando a
     *  coleção cresce: quem já tinha mais itens antes do limite existir continua editando normalmente. */
    fun growthError(next: AppState): String? {
        fun grew(a: Int, b: Int, max: Int) = b > max && b > a
        return when {
            grew(txs.size, next.txs.size, Limits.TRANSACTIONS) -> "Limite de ${Limits.TRANSACTIONS} lançamentos atingido."
            grew(accounts.size, next.accounts.size, Limits.ACCOUNTS) -> "Limite de ${Limits.ACCOUNTS} contas atingido."
            grew(cards.size, next.cards.size, Limits.CARDS) -> "Limite de ${Limits.CARDS} cartões atingido."
            grew(recurring.size, next.recurring.size, Limits.RECURRING) -> "Limite de ${Limits.RECURRING} recorrências atingido."
            grew(goals.size, next.goals.size, Limits.GOALS) -> "Limite de ${Limits.GOALS} metas atingido."
            Kind.entries.any { grew(cats.of(it).size, next.cats.of(it).size, Limits.CATEGORIES_PER_KIND) } ->
                "Limite de ${Limits.CATEGORIES_PER_KIND} categorias atingido."
            else -> null
        }
    }

    /** Quantidades máximas aceitas num backup externo; o app também não deixa criar além delas,
     *  para que todo backup gerado por ele possa ser restaurado. */
    object Limits {
        const val TRANSACTIONS = 50_000
        const val ACCOUNTS = 100
        const val CARDS = 100
        const val RECURRING = 1_000
        const val GOALS = 1_000
        const val CATEGORIES_PER_KIND = 500
    }

    companion object {
        const val MAIN_ACCOUNT = "main"
        val DEFAULT_EXPENSE = listOf("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Educação", "Outros")
        val DEFAULT_INCOME = listOf("Salário", "Extra", "Investimentos", "Outros")
        val AUTOLOCK_OPTIONS = listOf(0, 1, 5, 15, 30)
        const val CARD_PAYMENT_CAT = "Pagamento de fatura"
        /** categoria dos lançamentos de reserva para meta (não aparece na lista de categorias) */
        const val GOAL_CAT = "Metas"
    }
}

object Ids {
    private val rnd = java.security.SecureRandom()
    private var seq = 0L
    @Synchronized
    fun new(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) +
        java.lang.Long.toString(seq++, 36) + java.lang.Long.toString((rnd.nextInt(1 shl 20)).toLong(), 36)
}
