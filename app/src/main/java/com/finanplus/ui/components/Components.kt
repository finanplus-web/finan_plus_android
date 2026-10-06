// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.core.Cents
import com.finanplus.core.Money
import com.finanplus.ui.theme.Fin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val BR: Locale = Locale.forLanguageTag("pt-BR")
val DMY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun LocalDate.br(): String = format(DMY)

/** Privacidade ("Ocultar valores") disponível em toda a árvore. */
val LocalPrivacy = staticCompositionLocalOf { false }

/**
 * Valor em reais que respeita "Ocultar valores". Nunca é cortado com "…": se não couber na largura
 * (tela estreita, fonte grande, valores altos), a fonte diminui aos poucos até caber (mínimo 11sp).
 */
@Composable
fun MoneyText(c: Cents, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified, prefix: String = "") {
    val hide = LocalPrivacy.current
    val text = if (hide) "R$ ••••" else prefix + Money.format(c)
    val base: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 16.sp
    var size by remember(text, base) { mutableStateOf(base) }
    var fits by remember(text, base) { mutableStateOf(false) }
    // largura em que o tamanho atual foi calculado: se ela aumentar (ex.: girar a tela), recomeça do tamanho normal
    var measuredAt by remember(text, base) { mutableStateOf(-1) }
    Text(
        text,
        modifier.semantics { if (hide) contentDescription = "Valor oculto" }.drawWithContent { if (fits) drawContent() },
        style = style.copy(fontSize = size), color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        onTextLayout = { r ->
            val w = r.layoutInput.constraints.maxWidth
            val grew = measuredAt in 0 until w && size.value < base.value
            measuredAt = w
            when {
                grew -> { size = base; fits = false }
                r.didOverflowWidth && size.value > 11f -> size = (size.value * 0.9f).coerceAtLeast(11f).sp
                else -> fits = true
            }
        },
    )
}

/** Gráficos e barras: desfocados quando os valores estão ocultos. */
fun Modifier.sensitive(hide: Boolean): Modifier = if (!hide) this
    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(10.dp) else this.alpha(0.1f)

/** Fundo com os brilhos radiais do Finan+. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val p = Fin.c
    Box(
        Modifier.fillMaxSize().background(p.bg).drawBehind {
            if (p.glowA != p.bg) drawCircle(Brush.radialGradient(listOf(p.glowA, Color.Transparent), Offset(size.width * 0.9f, size.height * 0.05f), size.width * 0.7f), size.width * 0.7f, Offset(size.width * 0.9f, size.height * 0.05f))
            if (p.glowB != p.bg) drawCircle(Brush.radialGradient(listOf(p.glowB, Color.Transparent), Offset(0f, size.height * 0.3f), size.width * 0.65f), size.width * 0.65f, Offset(0f, size.height * 0.3f))
        },
    ) { content() }
}

/** Cartão translúcido ("Liquid Glass"). */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 25.dp, padding: Dp = 17.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = Fin.c
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.fillMaxWidth().clip(shape).background(p.surface, shape).border(1.dp, p.border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(BR), modifier, style = MaterialTheme.typography.labelSmall, color = Fin.c.muted)

@Composable
fun SectionHead(eyebrow: String?, title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        if (action != null && onAction != null) Pill(action, onClick = onAction)
    }
}

/** Botão em pílula. [icon] opcional (Material Symbols) antes do texto. Área de toque de pelo menos 48dp. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, danger: Boolean = false, icon: Ico? = null, onClick: () -> Unit) {
    val p = Fin.c
    val bg = when { danger -> p.red.copy(alpha = 0.14f); selected -> p.accent; else -> p.accent2 }
    val fg = when { danger -> p.red; selected -> p.onAccent; else -> p.text }
    // minimumInteractiveComponentSize reserva 48dp de altura para o toque sem mudar o desenho da pílula
    Row(
        modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(15.dp)).background(bg).clickable(role = Role.Button, onClick = onClick)
            .semantics { if (selected) stateDescription = "selecionado" }.padding(horizontal = 13.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { AppIcon(icon, fg, size = 18.dp); Spacer(Modifier.width(5.dp)) }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val p = Fin.c
    Button(
        onClick, modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp),
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = p.red.copy(alpha = 0.14f), contentColor = p.red)
        else ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
    ) { Text(text, fontWeight = FontWeight.ExtraBold) }
}

/** Barra de progresso simples (orçamento, metas, comparação). */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val p = Fin.c
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(p.track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height)).background(color))
    }
}

// ------------------------------------------------------------------ campos
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Fin.c.accent, unfocusedBorderColor = Fin.c.border.copy(alpha = 1f).let { if (Fin.c.dark) it else Color(0xFFD9DEEA) },
)

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, maxLength: Int = 200) {
    OutlinedTextField(
        value, { onChange(it.take(maxLength)) }, modifier.fillMaxWidth().padding(vertical = 5.dp),
        label = { Text(label) }, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(16.dp), colors = fieldColors(),
    )
}

@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "0,00") =
    Field(label, value, { v -> onChange(v.filter { it.isDigit() || it in ".,-" }) }, modifier, placeholder, KeyboardType.Decimal, maxLength = 18)

/** Campo de leitura que abre algo ao tocar (data, seleção). */
@Composable
private fun TapField(label: String, value: String, modifier: Modifier, onTap: () -> Unit) {
    Box(modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        OutlinedTextField(value, {}, Modifier.fillMaxWidth(), label = { Text(label) }, readOnly = true, singleLine = true,
            trailingIcon = { AppIcon(Ico.DROPDOWN, Fin.c.muted, size = 24.dp) }, shape = RoundedCornerShape(16.dp), colors = fieldColors())
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable(role = Role.DropdownList, onClick = onTap).semantics { contentDescription = "$label: $value" })
    }
}

@Composable
fun <T> SelectField(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TapField(label, options.firstOrNull { it.first == selected }?.second ?: "", Modifier) { open = true }
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false }) }
        }
    }
}

@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    TapField(label, date?.br() ?: "Sem data", modifier) { open = true }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; open = false }) { Text("OK") } },
            dismissButton = {
                Row {
                    if (allowClear) TextButton({ onChange(null); open = false }) { Text("Sem data") }
                    TextButton({ open = false }) { Text("Cancelar") }
                }
            },
        ) { DatePicker(st) }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        // toggleable: o TalkBack anuncia "ativado"/"desativado" (com clickable só dizia "interruptor")
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Fin.c.muted)
        }
        Spacer(Modifier.padding(4.dp))
        Switch(checked, null)
    }
}

/** Linha de item gerenciável em Ajustes (conta, cartão, limite, categoria...). */
@Composable
fun ManageItem(title: String, subtitle: String, actions: @Composable () -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(p.accent2.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        actions()
    }
}

// ------------------------------------------------------------------ diálogos
data class DialogSpec(
    val title: String,
    val message: String,
    val ok: String = "OK",
    val cancel: String? = null,
    val danger: Boolean = false,
    val input: String? = null,
    val inputLabel: String = "",
    val password: Boolean = false,
    val onOk: (String) -> Unit = {},
    val onCancel: () -> Unit = {},
)

/** Avisos, confirmações e perguntas com campo, no estilo do app (substitui alert/confirm/prompt). */
class Dialogs {
    var current by mutableStateOf<DialogSpec?>(null); private set
    fun notice(title: String, message: String) { current = DialogSpec(title, message) }
    fun confirm(title: String, message: String, ok: String = "Confirmar", cancel: String = "Cancelar", danger: Boolean = false, onCancel: () -> Unit = {}, onOk: () -> Unit) {
        current = DialogSpec(title, message, ok, cancel, danger, onOk = { onOk() }, onCancel = onCancel)
    }
    fun input(title: String, message: String, label: String, value: String = "", ok: String = "OK", password: Boolean = false, onOk: (String) -> Unit) {
        current = DialogSpec(title, message, ok, "Cancelar", input = value, inputLabel = label, password = password, onOk = onOk)
    }
    fun close() { current = null }
}

val LocalDialogs = staticCompositionLocalOf { Dialogs() }

@Composable
fun DialogHost(d: Dialogs) {
    val spec = d.current ?: return
    var text by remember(spec) { mutableStateOf(spec.input ?: "") }
    val p = Fin.c
    AlertDialog(
        onDismissRequest = { d.close(); spec.onCancel() },
        title = { Text(spec.title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                if (spec.message.isNotEmpty()) Text(spec.message, color = p.muted)
                if (spec.input != null) Field(
                    spec.inputLabel, text, { v -> text = if (spec.password) v.filter { it.isDigit() } else v }, Modifier.padding(top = 8.dp),
                    keyboard = if (spec.password) KeyboardType.NumberPassword else KeyboardType.Text, password = spec.password, maxLength = if (spec.password) 8 else 40,
                )
            }
        },
        confirmButton = {
            TextButton({ d.close(); spec.onOk(text) }) { Text(spec.ok, color = if (spec.danger) p.red else p.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = if (spec.cancel != null) {
            { TextButton({ d.close(); spec.onCancel() }) { Text(spec.cancel) } }
        } else null,
        shape = RoundedCornerShape(28.dp),
    )
}

val ContentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 120.dp)
