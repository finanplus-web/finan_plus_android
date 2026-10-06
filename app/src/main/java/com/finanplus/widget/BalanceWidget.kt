// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.finanplus.MainActivity
import com.finanplus.core.Finance
import com.finanplus.core.Money
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Widget de saldo: saldo atual, previsto no fim do mês e o próximo vencimento. */
class BalanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val s = Repo.state.value
        val today = LocalDate.now()
        val show = DevicePrefs.get(context).value.widgetValues && !s.privacy
        val hidden = "R$ ••••"
        val balance = if (show) Money.format(Finance.currentBalance(s)) else hidden
        val future = if (show) Money.format(Finance.futureBalance(s, today.withDayOfMonth(today.lengthOfMonth()), today)) else hidden
        val next = Finance.nextDue(s, today)
        val nextText = when {
            next == null -> "Nenhuma conta pendente"
            // modo oculto: sem o título (ex.: "Psicólogo") nem o valor, só quando vence
            !show -> if (next.date.isBefore(today)) "há conta atrasada" else "vencimento em ${next.date.format(DateTimeFormatter.ofPattern("dd/MM"))}"
            else -> "${next.title} · ${if (next.date.isBefore(today)) "atrasada" else next.date.format(DateTimeFormatter.ofPattern("dd/MM"))} · ${Money.format(next.amount)}"
        }
        provideContent { GlanceTheme { Content(balance, future, nextText) } }
    }

    @Composable
    private fun Content(balance: String, future: String, next: String) {
        val c = GlanceTheme.colors
        Column(
            modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
                .background(c.widgetBackground).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("FINAN+", style = TextStyle(color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("Saldo atual", style = TextStyle(color = c.onSurfaceVariant, fontSize = 11.sp))
                    Text(balance, style = TextStyle(color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("Previsto", style = TextStyle(color = c.onSurfaceVariant, fontSize = 11.sp))
                    Text(future, style = TextStyle(color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Text("Próximo: $next", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
        }
    }

    companion object {
        suspend fun refresh(context: Context) { try { BalanceWidget().updateAll(context) } catch (_: Exception) { } }
    }
}

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
