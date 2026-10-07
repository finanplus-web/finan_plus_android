// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import java.security.MessageDigest
import java.security.SecureRandom

/** Aparelho pareado (navegador). [id] é curto e não serve para se autenticar. */
data class LanDevice(val id: String, val label: String, val address: String, val pairedAt: Long, val lastUse: Long)

/** Pedido de pareamento aguardando "Permitir" no celular. */
data class LanPairRequest(val id: String, val label: String, val address: String, val createdAt: Long)

/** Resultado de uma tentativa com o código. */
sealed interface PairAttempt {
    data class Pending(val request: LanPairRequest) : PairAttempt
    data object WrongCode : PairAttempt
    data class Locked(val seconds: Long) : PairAttempt
    data object DeviceLimit : PairAttempt
    data object Busy : PairAttempt
}

/** Situação de um pedido, consultada pelo navegador até o celular responder. */
sealed interface PairStatus {
    data object Waiting : PairStatus
    data class Approved(val token: String) : PairStatus
    data object Denied : PairStatus
    data object Expired : PairStatus
}

/**
 * Pareamento e sessões. Só lógica (sem rede), segura entre threads (um único lock).
 *
 *  - Código de 6 dígitos, comparado em tempo constante. Muda a cada uso e a cada [LanConfig.maxFailsPerAddress]
 *    erros de um mesmo endereço. O bloqueio é POR ENDEREÇO: alguém errando de propósito não impede
 *    você de parear (antes, um bloqueio global permitia travar o pareamento de todos).
 *  - Código certo → pedido pendente; só "Permitir" no celular gera o token (256 bits), entregue UMA vez.
 *  - Guarda só o hash (SHA-256) do token, em memória. [clear] (parar o servidor) invalida todos.
 *  - Eventos são devolvidos para quem chamou disparar fora do lock (nunca código externo sob lock).
 */
class Pairing(
    private val cfg: LanConfig,
    private val clock: () -> Long,
    private val rnd: SecureRandom = SecureRandom(),
) {
    private class Device(val label: String, val address: String, val pairedAt: Long, var lastUse: Long)
    private class Fails(var count: Int = 0, var lockedUntil: Long = 0L)
    private class Pending(val req: LanPairRequest) {
        var status: PairStatus = PairStatus.Waiting
        var closedAt = 0L
    }

    private val lock = Any()
    private val devices = HashMap<String, Device>()       // hash do token → aparelho
    private val pending = HashMap<String, Pending>()      // id do pedido → pedido
    /** erros por endereço; tamanho limitado (o mais antigo sai), para não crescer sem fim */
    private val fails = object : LinkedHashMap<String, Fails>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Fails>?) = size > MAX_TRACKED_ADDRESSES
    }

    @Volatile var code: String = newCode(); private set

    /** Tentativa com o código. [events] recebe o que deve ser avisado (código novo, pedido novo). */
    fun attempt(address: String, typed: String, label: String, events: MutableList<LanEvent>): PairAttempt {
        return synchronized(lock) {
            val now = clock()
            val f = fails.getOrPut(address) { Fails() }
            if (now < f.lockedUntil) return PairAttempt.Locked((f.lockedUntil - now + 999) / 1000)
            if (!MessageDigest.isEqual(typed.toByteArray(), code.toByteArray())) {
                if (++f.count >= cfg.maxFailsPerAddress) {
                    f.count = 0
                    f.lockedUntil = now + cfg.lockoutMillis
                    rotate(events)
                    return PairAttempt.Locked(cfg.lockoutMillis / 1000)
                }
                return PairAttempt.WrongCode
            }
            if (devices.size >= cfg.maxDevices) return PairAttempt.DeviceLimit
            if (pending.values.count { it.status == PairStatus.Waiting } >= cfg.maxPending) return PairAttempt.Busy
            fails.remove(address)
            rotate(events) // um código visto por cima do ombro não serve de novo
            val req = LanPairRequest(hex(16), label, address, now)
            pending[req.id] = Pending(req)
            events += LanEvent.PairRequested(req)
            PairAttempt.Pending(req)
        }
    }

    /** O navegador pergunta pelo pedido [id]. O token aprovado é entregue uma única vez. */
    fun status(id: String): PairStatus {
        return synchronized(lock) {
            val p = pending[id] ?: return PairStatus.Expired
            when (val s = p.status) {
                PairStatus.Waiting -> s
                else -> { pending.remove(id); s }
            }
        }
    }

    /** "Permitir" no celular. */
    fun approve(id: String, events: MutableList<LanEvent>): Boolean {
        return synchronized(lock) {
            val p = pending[id]?.takeIf { it.status == PairStatus.Waiting } ?: return false
            val now = clock()
            p.closedAt = now
            if (devices.size >= cfg.maxDevices) { p.status = PairStatus.Denied; events += LanEvent.PairClosed(id); return false }
            val token = hex(32)
            devices[sha256(token)] = Device(p.req.label, p.req.address, now, now)
            p.status = PairStatus.Approved(token)
            events += LanEvent.PairClosed(id)
            events += LanEvent.DevicesChanged(devicesLocked())
            true
        }
    }

    /** "Recusar" no celular (ou janela fechada). */
    fun deny(id: String, events: MutableList<LanEvent>): Boolean {
        return synchronized(lock) {
            val p = pending[id]?.takeIf { it.status == PairStatus.Waiting } ?: return false
            p.status = PairStatus.Denied
            p.closedAt = clock()
            events += LanEvent.PairClosed(id)
            true
        }
    }

    /** Token válido? Atualiza o último uso do aparelho. */
    fun authenticate(token: String?): Boolean {
        if (token == null || !TOKEN_FORMAT.matches(token)) return false
        return synchronized(lock) { devices[sha256(token)]?.also { it.lastUse = clock() } != null }
    }

    fun logout(token: String?, events: MutableList<LanEvent>) {
        synchronized(lock) {
            if (token != null && devices.remove(sha256(token)) != null) events += LanEvent.DevicesChanged(devicesLocked())
        }
    }

    /** Desconecta pelo celular. [deviceId] é o prefixo mostrado na lista. */
    fun revoke(deviceId: String, events: MutableList<LanEvent>): Boolean {
        return synchronized(lock) {
            if (!DEVICE_ID.matches(deviceId)) return false
            val key = devices.keys.firstOrNull { it.startsWith(deviceId) } ?: return false
            devices.remove(key)
            events += LanEvent.DevicesChanged(devicesLocked())
            true
        }
    }

    /** Expira pedidos sem resposta e os aprovados que o navegador nunca buscou (token anulado). */
    fun expire(events: MutableList<LanEvent>) {
        synchronized(lock) {
            val now = clock()
            val it = pending.entries.iterator()
            var devicesChanged = false
            while (it.hasNext()) {
                val (id, p) = it.next()
                val waiting = p.status == PairStatus.Waiting
                val age = now - if (waiting) p.req.createdAt else p.closedAt
                if (age <= cfg.pairTimeoutMillis) continue
                it.remove()
                if (waiting) events += LanEvent.PairClosed(id)
                (p.status as? PairStatus.Approved)?.let { a -> if (devices.remove(sha256(a.token)) != null) devicesChanged = true }
            }
            if (devicesChanged) events += LanEvent.DevicesChanged(devicesLocked())
        }
    }

    /** Fecha tudo (servidor parado): pedidos abertos viram eventos de fechamento. */
    fun clear(events: MutableList<LanEvent>) {
        synchronized(lock) {
            pending.filterValues { it.status == PairStatus.Waiting }.keys.forEach { events += LanEvent.PairClosed(it) }
            pending.clear(); devices.clear(); fails.clear()
        }
    }

    fun isPairedToken(token: String?): Boolean = token != null && TOKEN_FORMAT.matches(token) && synchronized(lock) { devices.containsKey(sha256(token)) }
    fun devices(): List<LanDevice> = synchronized(lock) { devicesLocked() }
    fun pendingRequests(): List<LanPairRequest> {
        return synchronized(lock) {
            pending.values.filter { it.status == PairStatus.Waiting }.map { it.req }.sortedBy { it.createdAt }
        }
    }

    private fun devicesLocked() = devices.entries.map { (h, d) -> LanDevice(h.take(DEVICE_ID_LEN), d.label, d.address, d.pairedAt, d.lastUse) }.sortedBy { it.pairedAt }

    private fun rotate(events: MutableList<LanEvent>) { code = newCode(); events += LanEvent.CodeChanged(code) }
    private fun newCode() = "%06d".format(rnd.nextInt(1_000_000))
    private fun hex(bytes: Int) = ByteArray(bytes).also(rnd::nextBytes).toHex()

    companion object {
        private const val MAX_TRACKED_ADDRESSES = 256
        private const val DEVICE_ID_LEN = 12
        val TOKEN_FORMAT = Regex("^[0-9a-f]{64}$")
        val REQUEST_ID = Regex("^[0-9a-f]{32}$")
        private val DEVICE_ID = Regex("^[0-9a-f]{$DEVICE_ID_LEN}$")
        val CODE_FORMAT = Regex("^[0-9]{6}$")

        fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).toHex()
        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
