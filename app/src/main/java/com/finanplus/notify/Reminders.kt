// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.finanplus.MainActivity
import com.finanplus.R
import com.finanplus.core.Finance
import com.finanplus.core.Money
import com.finanplus.core.ReminderType
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.finanplus.widget.BalanceWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object Reminders {
    const val CHANNEL = "vencimentos"
    private const val WORK = "finanplus_diario"
    private const val NOTIF_ID = 1001

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL, "Vencimentos", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Contas a pagar, valores a receber e faturas de cartão próximas do vencimento"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Uma execução por dia, por volta das 9h: gera recorrências, atualiza o widget e avisa vencimentos. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // verificado em canNotify()
    fun notifyDue(ctx: Context, today: LocalDate = LocalDate.now()) {
        val prefs = DevicePrefs.get(ctx)
        if (!prefs.value.notifications || !canNotify(ctx)) return
        val s = Repo.state.value
        val items = Finance.reminders(s, today)
        if (items.isEmpty()) return
        val key = today.toString() + ":" + items.joinToString(",") { it.refId + it.date }
        if (prefs.lastNotified == key) return

        // Valores só aparecem se o usuário não pediu para ocultá-los; na tela de bloqueio, nunca.
        val showValues = !s.privacy
        val fmt = DateTimeFormatter.ofPattern("dd/MM")
        fun line(r: com.finanplus.core.Reminder): String {
            val what = when (r.type) {
                ReminderType.BILL_OVERDUE -> "Atrasada desde ${r.date.format(fmt)}"
                ReminderType.BILL_DUE -> if (r.date == today) "Vence hoje" else "Vence ${r.date.format(fmt)}"
                ReminderType.INCOME_DUE -> "A receber"
                ReminderType.INVOICE_DUE -> if (r.date.isBefore(today)) "Fatura vencida" else "Fatura vence ${r.date.format(fmt)}"
            }
            return "${r.title} · $what" + if (showValues) " · ${Money.format(r.amount)}" else ""
        }
        val title = if (items.size == 1) line(items[0]).substringBefore(" · ") else "${items.size} lançamentos pedem atenção"
        val text = if (items.size == 1) line(items[0]).substringAfter(" · ") else items.take(2).joinToString(" · ") { it.title }

        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_finan)
            .setContentTitle("Finan+").setContentText("Você tem vencimentos próximos").build()
        val style = NotificationCompat.InboxStyle().also { st -> items.take(6).forEach { st.addLine(line(it)) } }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_finan)
            .setContentTitle(title).setContentText(text).setStyle(style)
            .setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n); prefs.lastNotified = key } catch (_: SecurityException) { }
    }
}

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        Repo.runRecurring()
        Repo.flush()
        Reminders.notifyDue(applicationContext)
        BalanceWidget.refresh(applicationContext)
        return Result.success()
    }
}
