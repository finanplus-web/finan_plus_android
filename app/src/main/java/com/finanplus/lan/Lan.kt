// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import com.finanplus.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** O que a tela de Ajustes (e o diálogo de permitir) mostra sobre o servidor local. */
data class LanUi(
    val starting: Boolean = false,
    val running: Boolean = false,
    /** página de instalação/entrada (http://…): o único endereço mostrado ao usuário */
    val setupUrl: String = "",
    val code: String = "",
    /** impressão digital SHA-256 da CA deste celular */
    val caSha256: String = "",
    val devices: List<LanDevice> = emptyList(),
    val pending: List<LanPairRequest> = emptyList(),
    /** motivo da última parada ou erro, para exibir; null = nada a avisar */
    val message: String? = null,
)

/** Ponto de entrada do recurso "Acesso pela rede (beta)": estado para a tela e comandos. */
object Lan {
    private val _ui = MutableStateFlow(LanUi())
    val ui: StateFlow<LanUi> = _ui

    /**
     * Parâmetros do build beta (propriedade Gradle `finanLan`, ver [LanConfig]). Inválidos = erro na
     * partida do servidor (com mensagem na tela), nunca valores ajustados em silêncio.
     */
    val config: LanConfig by lazy { LanConfig.parse(BuildConfig.LAN_CONFIG) }

    /** Log do Android sem dados sensíveis (só nomes de eventos e detalhes técnicos). */
    val log = LanLog { level, event, detail ->
        val msg = if (detail.isEmpty()) event else "$event $detail"
        when (level) { LanLog.Level.INFO -> Log.i(TAG, msg); LanLog.Level.WARN -> Log.w(TAG, msg); LanLog.Level.ERROR -> Log.e(TAG, msg) }
    }

    /** servidor em execução (definido pelo [LanService]) */
    @Volatile internal var server: LanServer? = null

    internal fun set(f: (LanUi) -> LanUi) = _ui.update(f)

    fun start(ctx: Context) {
        set { it.copy(message = null) }
        ContextCompat.startForegroundService(ctx, Intent(ctx, LanService::class.java))
    }

    fun stop(ctx: Context) {
        if (server == null && !ui.value.starting) return
        ctx.startService(Intent(ctx, LanService::class.java).setAction(LanService.ACTION_STOP))
    }

    fun approve(id: String) { server?.approve(id) }
    fun deny(id: String) { server?.deny(id) }
    fun revoke(deviceId: String) { server?.revoke(deviceId) }

    private const val TAG = "FinanLan"
}

/** Endereço IPv4 privado do celular na rede local (os únicos que a CA do Finan+ cobre). */
object LocalNetwork {
    private val HOTSPOT_INTERFACES = listOf("wlan", "swlan", "ap", "softap", "eth")

    /** Primeiro o Wi-Fi/Ethernet ativo; se o celular estiver compartilhando a própria rede (hotspot), a interface dele. */
    fun address(ctx: Context): InetAddress? = activeNetworkAddress(ctx) ?: hotspotAddress()

    private fun activeNetworkAddress(ctx: Context): InetAddress? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null
        return cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.firstOrNull(::isPrivateV4)
    }

    private fun hotspotAddress(): InetAddress? = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && HOTSPOT_INTERFACES.any { p -> it.name.startsWith(p) } }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull(::isPrivateV4)
    } catch (e: java.net.SocketException) {
        Lan.log.warn("interfaces_unavailable")
        null
    }

    private fun isPrivateV4(a: InetAddress) = a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress
}
