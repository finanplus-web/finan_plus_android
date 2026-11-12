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
import com.finanplus.ui.theme.Fin

/**
 * Ícones do app: Phosphor Icons (peso Regular), em tamanhos contidos (18–22dp).
 * [filled] é a versão Fill (aba ativa da barra inferior e destaques) e [duo] a Duotone (ícones dentro de quadrados, ver [IconTile]).
 * Os desenhos estão em res/drawable/ph_*.xml (Phosphor Icons, licença MIT, compatível com a GPL-3.0).
 */
enum class Ico(@DrawableRes val res: Int, @DrawableRes val filled: Int = 0, @DrawableRes val duo: Int = 0) {
    HOME(R.drawable.ph_house, filled = R.drawable.ph_house_fill, duo = R.drawable.ph_house_duotone),
    MOVES(R.drawable.ph_arrows_left_right, filled = R.drawable.ph_arrows_left_right_fill, duo = R.drawable.ph_arrows_left_right_duotone),
    REPORTS(R.drawable.ph_chart_pie_slice, filled = R.drawable.ph_chart_pie_slice_fill, duo = R.drawable.ph_chart_pie_slice_duotone),
    SETTINGS(R.drawable.ph_gear_six, filled = R.drawable.ph_gear_six_fill, duo = R.drawable.ph_gear_six_duotone),
    ADD(R.drawable.ph_plus, filled = 0, duo = R.drawable.ph_plus_duotone),
    REMOVE(R.drawable.ph_minus, filled = 0, duo = R.drawable.ph_minus_duotone),
    GOAL(R.drawable.ph_flag, filled = 0, duo = R.drawable.ph_flag_duotone),
    CHECK(R.drawable.ph_check, filled = 0, duo = R.drawable.ph_check_duotone),
    CARD(R.drawable.ph_credit_card, filled = 0, duo = R.drawable.ph_credit_card_duotone),
    INFO(R.drawable.ph_info, filled = 0, duo = R.drawable.ph_info_duotone),
    EDIT(R.drawable.ph_pencil_simple, filled = 0, duo = R.drawable.ph_pencil_simple_duotone),
    GAMES(R.drawable.ph_game_controller, filled = 0, duo = R.drawable.ph_game_controller_duotone),
    TREND(R.drawable.ph_trend_up, filled = 0, duo = R.drawable.ph_trend_up_duotone),
    EXPAND(R.drawable.ph_caret_down, filled = 0, duo = R.drawable.ph_caret_down_duotone),
    // tabela de ícones do redesign (REDESIGN-COERENCIA.md): um conceito, um ícone
    PALETTE(R.drawable.ph_palette, filled = 0, duo = R.drawable.ph_palette_duotone),
    BELL(R.drawable.ph_bell, filled = 0, duo = R.drawable.ph_bell_duotone),
    CALC(R.drawable.ph_calculator, filled = 0, duo = R.drawable.ph_calculator_duotone),
    WIFI(R.drawable.ph_wifi_high, filled = 0, duo = R.drawable.ph_wifi_high_duotone),
    DATABASE(R.drawable.ph_database, filled = 0, duo = R.drawable.ph_database_duotone),
    SCIENCE(R.drawable.ph_flask, filled = 0, duo = R.drawable.ph_flask_duotone),
    DEBT(R.drawable.ph_invoice, filled = 0, duo = R.drawable.ph_invoice_duotone),
    TAG(R.drawable.ph_tag, filled = 0, duo = R.drawable.ph_tag_duotone),
    SPEED(R.drawable.ph_gauge, filled = 0, duo = R.drawable.ph_gauge_duotone),
    DRAG(R.drawable.ph_dots_six_vertical, filled = 0, duo = R.drawable.ph_dots_six_vertical_duotone),
    ARCHIVE(R.drawable.ph_archive, filled = 0, duo = R.drawable.ph_archive_duotone),
    LOCK(R.drawable.ph_lock_simple, filled = 0, duo = R.drawable.ph_lock_simple_duotone),
    ASSIST(R.drawable.ph_sparkle, filled = R.drawable.ph_sparkle_fill, duo = R.drawable.ph_sparkle_duotone),
    FINGERPRINT(R.drawable.ph_fingerprint_simple, filled = 0, duo = R.drawable.ph_fingerprint_simple_duotone),
    UP(R.drawable.ph_arrow_up, filled = 0, duo = R.drawable.ph_arrow_up_duotone),
    DOWN(R.drawable.ph_arrow_down, filled = 0, duo = R.drawable.ph_arrow_down_duotone),
    BACKSPACE(R.drawable.ph_backspace, filled = 0, duo = R.drawable.ph_backspace_duotone),
    DROPDOWN(R.drawable.ph_caret_down, filled = 0, duo = R.drawable.ph_caret_down_duotone),
    WARNING(R.drawable.ph_warning, filled = 0, duo = R.drawable.ph_warning_duotone),
    SHIELD(R.drawable.ph_shield, filled = 0, duo = R.drawable.ph_shield_duotone),
    CALENDAR(R.drawable.ph_calendar_dots, filled = 0, duo = R.drawable.ph_calendar_dots_duotone),
    LIST(R.drawable.ph_list_bullets, filled = 0, duo = R.drawable.ph_list_bullets_duotone),
    PREV(R.drawable.ph_caret_left, filled = 0, duo = R.drawable.ph_caret_left_duotone),
    NEXT(R.drawable.ph_caret_right, filled = 0, duo = R.drawable.ph_caret_right_duotone),
    SEARCH(R.drawable.ph_magnifying_glass, filled = 0, duo = R.drawable.ph_magnifying_glass_duotone),
    CLOSE(R.drawable.ph_x, filled = 0, duo = R.drawable.ph_x_duotone),
    RECEIPT(R.drawable.ph_receipt, filled = 0, duo = R.drawable.ph_receipt_duotone),
    TUNE(R.drawable.ph_sliders_horizontal, filled = 0, duo = R.drawable.ph_sliders_horizontal_duotone),
    WALLET(R.drawable.ph_wallet, filled = R.drawable.ph_wallet_fill, duo = R.drawable.ph_wallet_duotone),
    FORECAST(R.drawable.ph_chart_bar, filled = 0, duo = R.drawable.ph_chart_bar_duotone),
    // Receita / Falta receber e Despesa / Falta pagar: setas no círculo (preenchem melhor o quadrado que a seta solta)
    INCOME(R.drawable.ph_arrow_circle_up, filled = 0, duo = R.drawable.ph_arrow_circle_up_duotone),
    EXPENSE(R.drawable.ph_arrow_circle_down, filled = 0, duo = R.drawable.ph_arrow_circle_down_duotone),
    LIMIT(R.drawable.ph_target, filled = 0, duo = R.drawable.ph_target_duotone),
    GROUP(R.drawable.ph_users_three, filled = 0, duo = R.drawable.ph_users_three_duotone),
    SHARE(R.drawable.ph_share_network, filled = 0, duo = R.drawable.ph_share_network_duotone),
    ATTACH(R.drawable.ph_paperclip, filled = 0, duo = R.drawable.ph_paperclip_duotone),

    // categorias
    FOOD(R.drawable.ph_shopping_cart, filled = 0, duo = R.drawable.ph_shopping_cart_duotone),
    RESTAURANT(R.drawable.ph_fork_knife, filled = 0, duo = R.drawable.ph_fork_knife_duotone),
    TRANSPORT(R.drawable.ph_car, filled = 0, duo = R.drawable.ph_car_duotone),
    FUEL(R.drawable.ph_gas_pump, filled = 0, duo = R.drawable.ph_gas_pump_duotone),
    HEALTH(R.drawable.ph_heart, filled = 0, duo = R.drawable.ph_heart_duotone),
    FITNESS(R.drawable.ph_barbell, filled = 0, duo = R.drawable.ph_barbell_duotone),
    LEISURE(R.drawable.ph_film_slate, filled = 0, duo = R.drawable.ph_film_slate_duotone),
    SUBSCRIPTIONS(R.drawable.ph_monitor_play, filled = 0, duo = R.drawable.ph_monitor_play_duotone),
    EDUCATION(R.drawable.ph_graduation_cap, filled = 0, duo = R.drawable.ph_graduation_cap_duotone),
    OTHER(R.drawable.ph_dots_three, filled = 0, duo = R.drawable.ph_dots_three_duotone),
    SALARY(R.drawable.ph_money, filled = 0, duo = R.drawable.ph_money_duotone),
    EXTRA(R.drawable.ph_coins, filled = 0, duo = R.drawable.ph_coins_duotone),
    INVEST(R.drawable.ph_piggy_bank, filled = 0, duo = R.drawable.ph_piggy_bank_duotone),
    PETS(R.drawable.ph_paw_print, filled = 0, duo = R.drawable.ph_paw_print_duotone),
    CLOTHES(R.drawable.ph_t_shirt, filled = 0, duo = R.drawable.ph_t_shirt_duotone),
    SHOPPING(R.drawable.ph_shopping_bag, filled = 0, duo = R.drawable.ph_shopping_bag_duotone),
    BEAUTY(R.drawable.ph_flower_lotus, filled = 0, duo = R.drawable.ph_flower_lotus_duotone),
    TAXES(R.drawable.ph_receipt, filled = 0, duo = R.drawable.ph_receipt_duotone),
    GIFTS(R.drawable.ph_gift, filled = 0, duo = R.drawable.ph_gift_duotone),
    BANK(R.drawable.ph_bank, filled = 0, duo = R.drawable.ph_bank_duotone),
    WORK(R.drawable.ph_briefcase, filled = 0, duo = R.drawable.ph_briefcase_duotone),
    REPEAT(R.drawable.ph_repeat, filled = 0, duo = R.drawable.ph_repeat_duotone),
    /** transferência entre contas */
    TRANSFER(R.drawable.ph_swap, filled = 0, duo = R.drawable.ph_swap_duotone),
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
    duotone: Boolean = false,
) {
    val sem = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.clearAndSetSemantics { contentDescription = description }
    Icon(
        painterResource(when { filled && icon.filled != 0 -> icon.filled; duotone && icon.duo != 0 -> icon.duo; else -> icon.res }),
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
        category == AppState.GOAL_CAT -> Ico.GOAL
        category == AppState.TRANSFER_CAT -> Ico.TRANSFER
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
        // nomes compostos ou criados pela pessoa ("Cartão Itaú", "Empréstimo Caixa", "Meta viagem")
        "cartao" in c || "fatura" in c -> Ico.CARD
        "emprestimo" in c || "financiamento" in c -> Ico.BANK
        "salario" in c -> Ico.SALARY
        c.startsWith("meta") -> Ico.GOAL
        else -> null
    }
}

/** Tom do ícone de um item nas listas (Início e Lançamentos): a cor diz o tipo, não a categoria. */
enum class Tone { NEGATIVE, POSITIVE, PRIMARY, NEUTRAL, GOAL, CARD }

@Composable
fun Tone.color(): Color = Fin.c.let { p -> when (this) { Tone.NEGATIVE -> p.red; Tone.POSITIVE -> p.green; Tone.PRIMARY -> p.accent; Tone.NEUTRAL -> p.muted; Tone.GOAL -> p.goal; Tone.CARD -> p.card } }

/**
 * Tom de um lançamento: despesa e conta a pagar = negativo; receita e valor a receber = positivo;
 * reserva para meta = âmbar das metas; cartão (compra ou fatura) = lilás; [neutral] = mês seguinte ou só informativo.
 */
fun txTone(kind: com.finanplus.core.Kind, goal: Boolean, neutral: Boolean = false, card: Boolean = false): Tone = when {
    neutral -> Tone.NEUTRAL
    goal -> Tone.GOAL
    card -> Tone.CARD
    kind == com.finanplus.core.Kind.INCOME -> Tone.POSITIVE
    else -> Tone.NEGATIVE
}

/** Quadrado arredondado com fundo suave e o ícone da categoria na cor forte do [tone]. */
@Composable
fun CategoryTile(category: String, tone: Tone, size: Dp = 42.dp, icon: Ico? = null) {
    val c = tone.color()
    IconTile(icon ?: categoryIcon(category) ?: Ico.OTHER, c, c.copy(alpha = if (Fin.c.dark) 0.16f else 0.12f), size = size, iconSize = size * 0.48f)
}

/** Ícone de categoria solto (sem o quadrado): o da categoria ou, se desconhecida, o genérico (sem letras). */
@Composable
fun CategoryGlyph(category: String, tint: Color) = AppIcon(categoryIcon(category) ?: Ico.OTHER, tint, size = 20.dp)
