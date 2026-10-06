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
    MATERIAL_YOU("materialBlue", "Material You"),
    OLED("oledGray", "OLED Cinza"),
    TOKYO("tokyo", "Tokyo Night"),
    NORD("nord", "Nord");
    companion object {
        fun of(s: Any?): ThemeId = if (s == "dark") OLED else entries.firstOrNull { it.json == s } ?: AUTO
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
) {
    val isFlow get() = cardPayment.isEmpty()
    val isCard get() = cardId.isNotEmpty()
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

    companion object {
        const val MAIN_ACCOUNT = "main"
        val DEFAULT_EXPENSE = listOf("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Educação", "Outros")
        val DEFAULT_INCOME = listOf("Salário", "Extra", "Investimentos", "Outros")
        val AUTOLOCK_OPTIONS = listOf(0, 1, 5, 15, 30)
        const val CARD_PAYMENT_CAT = "Pagamento de fatura"
    }
}

object Ids {
    private val rnd = java.security.SecureRandom()
    private var seq = 0L
    @Synchronized
    fun new(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) +
        java.lang.Long.toString(seq++, 36) + java.lang.Long.toString((rnd.nextInt(1 shl 20)).toLong(), 36)
}
