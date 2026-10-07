// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Avisos do servidor para a tela/notificação. Chegam de outras threads, sempre fora de locks internos. */
sealed interface LanEvent {
    data class Started(val url: String, val setupUrl: String) : LanEvent
    data class CodeChanged(val code: String) : LanEvent
    data class PairRequested(val request: LanPairRequest) : LanEvent
    /** pedido aprovado, recusado ou expirado: some da tela e da notificação */
    data class PairClosed(val id: String) : LanEvent
    data class DevicesChanged(val devices: List<LanDevice>) : LanEvent
    data class Stopped(val reason: StopReason) : LanEvent
}

enum class StopReason { USER, IDLE, ERROR }

/**
 * Acesso pela rede: junta as peças e controla o ciclo de vida.
 *
 *  - [HttpServer]: transporte (duas portas, limites, prazos);
 *  - [LanRoutes]: rotas e validação de entrada;
 *  - [Pairing]: código, pedidos, tokens, aparelhos;
 *  - [StateVersion]: versão dos dados para o PWA.
 *
 * Sempre HTTPS para dados: a porta HTTP só entrega a instalação do certificado. Escuta apenas em
 * [bindAddress] (o IP privado do Wi-Fi), nunca em todas as interfaces. Desliga sozinho após
 * [LanConfig.idleMillis] sem uso por um aparelho pareado.
 */
class LanServer(
    backend: LanBackend,
    private val bindAddress: InetAddress,
    tls: LanTls,
    private val config: LanConfig = LanConfig(),
    private val log: LanLog = LanLog.NONE,
    private val onEvent: (LanEvent) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val pairing = Pairing(config, clock)
    private val routes = LanRoutes(config, backend, tls, pairing, StateVersion(), log, ::emit, ::touch) { appEndpoint?.origin ?: "" }
    private val http = HttpServer(bindAddress, config, log, ::bodyLimit, onFatal = { stop(StopReason.ERROR) }, clock = clock)
    private val tlsSockets = tls

    @Volatile private var appEndpoint: HttpEndpoint? = null
    @Volatile private var setupEndpoint: HttpEndpoint? = null
    @Volatile private var lastActivity = clock()
    private var ticker: ScheduledExecutorService? = null
    @Volatile var running = false; private set

    /** Endereço do app web (https://…). */
    val url: String get() = appEndpoint?.origin ?: ""
    /** Página de instalação do certificado (http://…). É o único endereço mostrado ao usuário. */
    val setupUrl: String get() = setupEndpoint?.origin ?: ""
    val host: String get() = appEndpoint?.host ?: ""
    val port: Int get() = appEndpoint?.port ?: 0
    val code: String get() = pairing.code

    fun devices(): List<LanDevice> = pairing.devices()
    fun pendingRequests(): List<LanPairRequest> = pairing.pendingRequests()

    fun start() {
        synchronized(this) {
            check(!running) { "já iniciado" }
            try {
                appEndpoint = http.listen(config.httpsPort, secure = true, create = tlsSockets::serverSocket, handler = routes.app)
                setupEndpoint = http.listen(config.httpPort, secure = false, create = ::ServerSocket, handler = routes.setup)
                http.start()
            } catch (e: Exception) {
                http.stop() // não deixa uma porta aberta pela metade
                appEndpoint = null; setupEndpoint = null
                log.error("start_failed", e)
                throw e
            }
            lastActivity = clock()
            ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "finan-lan-tick").apply { isDaemon = true } }.apply {
                scheduleWithFixedDelay({ tick() }, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS)
            }
            running = true
        }
        log.info("started", "https=${port}")
        emit(listOf(LanEvent.Started(url, setupUrl), LanEvent.CodeChanged(code)))
    }

    fun stop(reason: StopReason = StopReason.USER) {
        val ev = ArrayList<LanEvent>()
        synchronized(this) {
            if (!running) return
            running = false
            http.stop()
            ticker?.shutdownNow(); ticker = null
            pairing.clear(ev)
            appEndpoint = null; setupEndpoint = null
        }
        log.info("stopped", reason.name)
        ev += LanEvent.Stopped(reason)
        emit(ev)
    }

    /** Usuário tocou em "Permitir" no celular. */
    fun approve(id: String): Boolean = withEvents { pairing.approve(id, it) }.also { if (it) touch() }

    /** Usuário tocou em "Recusar" (ou fechou o pedido). */
    fun deny(id: String): Boolean = withEvents { pairing.deny(id, it) }

    /** Desconecta um aparelho (o navegador volta para a tela de conexão). */
    fun revoke(deviceId: String): Boolean = withEvents { pairing.revoke(deviceId, it) }

    private fun <T> withEvents(block: (MutableList<LanEvent>) -> T): T {
        val ev = ArrayList<LanEvent>()
        val r = block(ev)
        emit(ev)
        return r
    }

    private fun emit(events: List<LanEvent>) {
        for (e in events) try { onEvent(e) } catch (t: RuntimeException) { log.error("event_handler_failed", t) }
    }

    private fun touch() { lastActivity = clock() }

    /** O estado inteiro do PWA pode ser grande; o limite maior só vale para quem já está pareado. */
    private fun bodyLimit(head: HttpHead): Int =
        if (head.method == "PUT" && head.path == "/api/remote/state" && pairing.isPairedToken(head.bearer)) config.maxStateBytes
        else config.maxBodyBytes

    private fun tick() {
        if (!running) return
        withEvents { pairing.expire(it) }
        if (clock() - lastActivity > config.idleMillis) stop(StopReason.IDLE)
    }

    companion object {
        private const val TICK_SECONDS = 5L
    }
}
