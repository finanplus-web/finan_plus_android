// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finanplus.R
import com.finanplus.core.AppState
import com.finanplus.core.assist.Text as AssistText

/**
 * Ícones do app: Material Symbols Rounded, traço leve (peso 300), em tamanhos contidos (18–22dp).
 * Os desenhos estão em res/drawable/ms_*.xml (Google, licença Apache 2.0, compatível com a GPL-3.0).
 */
enum class Ico(@DrawableRes val res: Int, @DrawableRes val filled: Int = 0) {
    HOME(R.drawable.ms_home, R.drawable.ms_home_fill),
    MOVES(R.drawable.ms_swap_horiz, R.drawable.ms_swap_horiz_fill),
    REPORTS(R.drawable.ms_pie_chart, R.drawable.ms_pie_chart_fill),
    SETTINGS(R.drawable.ms_settings, R.drawable.ms_settings_fill),
    ADD(R.drawable.ms_add),
    REMOVE(R.drawable.ms_remove),
    GOAL(R.drawable.ms_flag),
    CHECK(R.drawable.ms_check),
    CARD(R.drawable.ms_credit_card),
    ASSIST(R.drawable.ms_auto_awesome),
    FINGERPRINT(R.drawable.ms_fingerprint),
    UP(R.drawable.ms_arrow_upward),
    DOWN(R.drawable.ms_arrow_downward),
    BACKSPACE(R.drawable.ms_backspace),
    DROPDOWN(R.drawable.ms_arrow_drop_down),
    WARNING(R.drawable.ms_warning),
    SHIELD(R.drawable.ms_shield),
    CALENDAR(R.drawable.ms_calendar_month),
    LIST(R.drawable.ms_view_list),
    PREV(R.drawable.ms_chevron_left),
    NEXT(R.drawable.ms_chevron_right),

    // categorias
    FOOD(R.drawable.ms_shopping_cart),
    RESTAURANT(R.drawable.ms_restaurant),
    TRANSPORT(R.drawable.ms_directions_car),
    FUEL(R.drawable.ms_local_gas_station),
    HEALTH(R.drawable.ms_favorite),
    FITNESS(R.drawable.ms_fitness_center),
    LEISURE(R.drawable.ms_theaters),
    SUBSCRIPTIONS(R.drawable.ms_subscriptions),
    EDUCATION(R.drawable.ms_school),
    OTHER(R.drawable.ms_more_horiz),
    SALARY(R.drawable.ms_payments),
    EXTRA(R.drawable.ms_attach_money),
    INVEST(R.drawable.ms_savings),
    PETS(R.drawable.ms_pets),
    CLOTHES(R.drawable.ms_checkroom),
    SHOPPING(R.drawable.ms_shopping_bag),
    BEAUTY(R.drawable.ms_spa),
    TAXES(R.drawable.ms_receipt_long),
    GIFTS(R.drawable.ms_redeem),
    BANK(R.drawable.ms_account_balance),
    WORK(R.drawable.ms_work),
}

/** Ícone com a cor do tema. Com [description] nulo o ícone é decorativo (o texto ao lado já descreve). */
@Composable
fun AppIcon(
    icon: Ico,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    filled: Boolean = false,
    description: String? = null,
) {
    val sem = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.clearAndSetSemantics { contentDescription = description }
    Icon(
        painterResource(if (filled && icon.filled != 0) icon.filled else icon.res),
        contentDescription = null, tint = tint,
        modifier = modifier.size(size).then(sem),
    )
}

/**
 * Ícone da categoria. Reconhece os nomes padrão e variações comuns,
 * sem diferenciar acento e maiúscula. Categorias desconhecidas: null (o app mostra a inicial).
 */
fun categoryIcon(category: String): Ico? {
    val c = AssistText.fold(category)
    return when {
        category == AppState.CARD_PAYMENT_CAT -> Ico.CARD
        c in setOf("alimentacao", "mercado", "supermercado", "comida", "feira") -> Ico.FOOD
        c in setOf("restaurante", "delivery", "lanche", "lanches") -> Ico.RESTAURANT
        c in setOf("transporte", "carro", "uber", "mobilidade") -> Ico.TRANSPORT
        c in setOf("combustivel", "gasolina", "posto") -> Ico.FUEL
        c in setOf("moradia", "casa", "aluguel", "contas") -> Ico.HOME
        c in setOf("saude", "farmacia", "medico") -> Ico.HEALTH
        c in setOf("academia", "esporte", "esportes") -> Ico.FITNESS
        c in setOf("lazer", "diversao", "entretenimento", "viagem") -> Ico.LEISURE
        c in setOf("assinaturas", "assinatura", "streaming") -> Ico.SUBSCRIPTIONS
        c in setOf("educacao", "estudos", "escola", "faculdade", "cursos") -> Ico.EDUCATION
        c in setOf("outros", "outro", "diversos") -> Ico.OTHER
        c in setOf("salario", "pagamento", "pro labore") -> Ico.SALARY
        c in setOf("extra", "extras", "renda extra", "freela", "bonus") -> Ico.EXTRA
        c in setOf("investimentos", "investimento", "poupanca", "rendimentos", "reserva") -> Ico.INVEST
        c in setOf("pets", "pet", "animais") -> Ico.PETS
        c in setOf("vestuario", "roupas", "roupa") -> Ico.CLOTHES
        c in setOf("compras", "shopping") -> Ico.SHOPPING
        c in setOf("beleza", "cuidados pessoais", "estetica") -> Ico.BEAUTY
        c in setOf("impostos", "taxas", "tarifas", "impostos e taxas") -> Ico.TAXES
        c in setOf("presentes", "doacoes", "presente", "doacao") -> Ico.GIFTS
        c in setOf("emprestimo", "emprestimos", "financiamento", "banco", "dividas") -> Ico.BANK
        c in setOf("trabalho", "adiantamento quinzenal", "adiantamento", "comissao") -> Ico.WORK
        else -> null
    }
}

/** Quadradinho de categoria nas listas: ícone da categoria ou, se desconhecida, a inicial do nome. */
@Composable
fun CategoryGlyph(category: String, tint: Color) {
    val ico = categoryIcon(category)
    val initial = category.trim().firstOrNull()?.uppercase()
    if (ico != null || initial == null) AppIcon(ico ?: Ico.OTHER, tint, size = 20.dp)
    else Text(initial, color = tint, fontWeight = FontWeight.Bold)
}
