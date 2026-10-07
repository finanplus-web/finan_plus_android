// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.finanplus.MainActivity
import com.finanplus.R
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Serviço em primeiro plano que mantém o servidor vivo enquanto estiver ligado,
 * com uma notificação fixa (endereço, código e botão Parar) e uma notificação por pedido de acesso.
 *
 * Ciclo de vida: a partida roda numa thread (Keystore e geração de chaves levam um instante). Se o
 * serviço for destruído ou "Parar" for tocado durante a partida, o servidor recém-criado é parado
 * logo em seguida — nunca fica um servidor rodando sem serviço (e sem notificação).
 */
class LanService : Service() {
    private val lifecycle = Any()
    private var starting = false
    private var stopRequested = false
    private var destroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { requestStop(); return START_NOT_STICKY }
            ACTION_DENY -> {
                intent.getStringExtra(EXTRA_ID)?.takeIf { Pairing.REQUEST_ID.matches(it) }?.let(Lan::deny)
                return START_NOT_STICKY
            }
        }
        // precisa entrar em primeiro plano logo (limite de ~5 s do Android), antes de qualquer verificação
        createChannels()
        enterForeground(statusNotification("Iniciando acesso pela rede…", null))
        synchronized(lifecycle) {
            if (starting || Lan.server?.running == true) { refreshNotification(); return START_NOT_STICKY }
            starting = true; stopRequested = false
        }
        Lan.set { LanUi(starting = true, caSha256 = it.caSha256) }
        Thread(::startServer, "finan-lan-start").start()
        return START_NOT_STICKY
    }

    private fun startServer() {
        val message: String? = try {
            val backend = RepoBackend(applicationContext)
            val addr = LocalNetwork.address(this)
            when {
                backend.writeBlocked() != null -> backend.writeBlocked()
                addr == null -> "Conecte o celular a uma rede Wi-Fi (ou ligue o roteador do celular) e tente de novo."
                else -> {
                    val ca = LanCa.get(applicationContext)
                    val srv = LanServer(backend, addr, LanTls(ca, X509.issueServer(ca, addr)), Lan.config, Lan.log, ::onEvent)
                    Lan.set { it.copy(caSha256 = ca.sha256) }
                    srv.start()
                    Lan.server = srv
                    null
                }
            }
        } catch (e: IOException) {
            Lan.log.error("start_failed", e); "Não foi possível abrir a porta de rede. Tente de novo."
        } catch (e: GeneralSecurityException) {
            Lan.log.error("start_failed", e); "Não foi possível preparar a conexão segura (certificado)."
        } catch (e: IllegalArgumentException) {
            Lan.log.error("start_failed", e); "Configuração do acesso pela rede inválida neste build."
        } catch (e: LanCa.KeystoreUnavailable) {
            Lan.log.error("start_failed", e); "O cofre de chaves do celular não respondeu. Tente de novo em instantes."
        } catch (e: RuntimeException) {
            // fronteira da thread: sem isto, um erro inesperado derrubaria o app inteiro
            Lan.log.error("start_failed", e); "Não foi possível iniciar o acesso pela rede."
        }
        val stopNow: Boolean
        synchronized(lifecycle) {
            starting = false
            stopNow = stopRequested || destroyed
        }
        when {
            message != null -> finish(message)
            stopNow -> Lan.server?.stop(StopReason.USER) // tocou em Parar (ou o serviço morreu) durante a partida
        }
    }

    private fun requestStop() {
        synchronized(lifecycle) { if (starting) { stopRequested = true; return } }
        val s = Lan.server
        if (s != null) s.stop(StopReason.USER) else finish(null)
    }

    private fun onEvent(e: LanEvent) {
        when (e) {
            is LanEvent.Started -> Lan.set { it.copy(starting = false, running = true, setupUrl = e.setupUrl) }
            is LanEvent.CodeChanged -> Lan.set { it.copy(code = e.code) }
            is LanEvent.DevicesChanged -> Lan.set { it.copy(devices = e.devices) }
            is LanEvent.PairRequested -> {
                Lan.set { it.copy(pending = it.pending + e.request) }
                notifyRequest(e.request)
            }
            is LanEvent.PairClosed -> {
                Lan.set { ui -> ui.copy(pending = ui.pending.filterNot { it.id == e.id }) }
                NotificationManagerCompat.from(this).cancel(e.id, NOTIF_PAIR)
            }
            is LanEvent.Stopped -> {
                Lan.server = null
                finish(when (e.reason) {
                    StopReason.USER -> null
                    StopReason.IDLE -> "Desligado automaticamente após ${Lan.config.idleMillis / 60_000} min sem uso."
                    StopReason.ERROR -> "O servidor parou por um erro de rede (o Wi-Fi caiu?)."
                })
                return
            }
        }
        refreshNotification()
    }

    private fun finish(message: String?) {
        Lan.set { LanUi(message = message, caSha256 = it.caSha256) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        synchronized(lifecycle) { destroyed = true }
        Lan.server?.stop(StopReason.USER)
        Lan.server = null
        Lan.set { if (it.running || it.starting) LanUi(caSha256 = it.caSha256) else it }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- notificações

    // é a notificação do serviço em primeiro plano; sem a permissão de notificações o Android só não a exibe
    @SuppressLint("MissingPermission")
    private fun refreshNotification() {
        val ui = Lan.ui.value
        if (!ui.running) return
        val text = "Abra ${ui.setupUrl} no navegador · código ${ui.code}" +
            if (ui.devices.isNotEmpty()) " · ${ui.devices.size} aparelho(s)" else ""
        NotificationManagerCompat.from(this).notify(NOTIF_ID, statusNotification(text, ui.code))
    }

    private fun enterForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun openApp(requestCode: Int) =
        PendingIntent.getActivity(this, requestCode, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    private fun statusNotification(text: String, code: String?): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, LanService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        // na tela de bloqueio aparece só que o servidor está ligado (sem endereço nem código)
        val public = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_finan)
            .setContentTitle("Acesso pela rede ligado").build()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_finan)
            .setContentTitle(if (code != null) "Acesso pela rede · código $code" else "Acesso pela rede")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openApp(0))
            .addAction(0, "Parar", stop)
            .build()
    }

    /**
     * Pedido de acesso. "Abrir e permitir" abre o app (que pede PIN/digital se o bloqueio estiver ligado)
     * e a confirmação acontece lá dentro; "Recusar" funciona direto da notificação.
     */
    @SuppressLint("MissingPermission")
    private fun notifyRequest(r: LanPairRequest) {
        val deny = PendingIntent.getService(this, r.id.hashCode(),
            Intent(this, LanService::class.java).setAction(ACTION_DENY).putExtra(EXTRA_ID, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(this, CHANNEL_PAIR).setSmallIcon(R.drawable.ic_stat_finan)
            .setContentTitle("Pedido de acesso ao Finan+").build()
        val detail = "${r.label} (${r.address}) digitou o código certo e quer acessar seus dados do Finan+. Só permita se foi você."
        val n = NotificationCompat.Builder(this, CHANNEL_PAIR)
            .setSmallIcon(R.drawable.ic_stat_finan)
            .setContentTitle("Permitir ${r.label}?")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .setTimeoutAfter(Lan.config.pairTimeoutMillis)
            .setContentIntent(openApp(r.id.hashCode()))
            .addAction(0, "Abrir e permitir", openApp(r.id.hashCode() + 1))
            .addAction(0, "Recusar", deny)
            .build()
        NotificationManagerCompat.from(this).notify(r.id, NOTIF_PAIR, n)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Acesso pela rede", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mostra o endereço e o código enquanto o servidor local está ligado"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_PAIR, "Pedidos de acesso", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisa quando um navegador digita o código e pede para acessar seus dados"
        })
    }

    companion object {
        const val ACTION_STOP = "com.finanplus.lan.STOP"
        const val ACTION_DENY = "com.finanplus.lan.DENY"
        const val EXTRA_ID = "id"
        private const val CHANNEL = "rede_local"
        private const val CHANNEL_PAIR = "rede_local_pedidos"
        private const val NOTIF_ID = 2001
        private const val NOTIF_PAIR = 2002
    }
}
