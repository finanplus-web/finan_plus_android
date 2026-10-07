// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

/**
 * Parâmetros operacionais do acesso pela rede. Não há segredos aqui: chaves e tokens são gerados em
 * tempo de execução (CA no Android Keystore, tokens só em memória) e nunca ficam no código nem no build.
 *
 * Os valores padrão são os de produção. Podem ser trocados sem mexer no código:
 *  - no app: propriedade Gradle `finanLan` (ou variável de ambiente `ORG_GRADLE_PROJECT_finanLan`),
 *    ex.: `httpsPort=9443;idleMinutes=5` → BuildConfig.LAN_CONFIG → [parse];
 *  - na JVM (testes, ferramentas): variáveis de ambiente `FINAN_LAN_*` → [fromEnv].
 * Valores fora dos limites são recusados (exceção na partida), nunca ajustados em silêncio.
 */
data class LanConfig(
    val httpPort: Int = 8090,
    val httpsPort: Int = 8443,
    val idleMillis: Long = 10 * 60_000L,
    val pairTimeoutMillis: Long = 2 * 60_000L,
    val maxFailsPerAddress: Int = 5,
    val lockoutMillis: Long = 30_000L,
    val maxDevices: Int = 4,
    val maxPending: Int = 2,
    val workers: Int = 4,
    val queue: Int = 8,
    val readTimeoutMillis: Int = 10_000,
    /** tempo máximo de uma conexão inteira (TLS + requisição + resposta): derruba conexões lentas de propósito */
    val connectionDeadlineMillis: Long = 30_000L,
    val maxHeaderBytes: Int = 16 * 1024,
    val maxBodyBytes: Int = 64 * 1024,
    /** limite só para o estado inteiro enviado pelo PWA, e só de quem já está pareado */
    val maxStateBytes: Int = 8 * 1024 * 1024,
) {
    init {
        require(httpPort in 1024..65535 && httpsPort in 1024..65535 && httpPort != httpsPort) { "portas inválidas" }
        require(idleMillis in 60_000L..24 * 3_600_000L) { "idleMillis fora do limite" }
        require(pairTimeoutMillis in 10_000L..10 * 60_000L) { "pairTimeoutMillis fora do limite" }
        require(maxFailsPerAddress in 1..20 && lockoutMillis in 1_000L..3_600_000L) { "bloqueio inválido" }
        require(maxDevices in 1..16 && maxPending in 1..8) { "limites de aparelhos inválidos" }
        require(workers in 1..16 && queue in 0..64) { "limites de conexões inválidos" }
        require(readTimeoutMillis in 500..60_000 && connectionDeadlineMillis in 1_000L..300_000L) { "tempos inválidos" }
        require(maxHeaderBytes in 1024..64 * 1024 && maxBodyBytes in 1024..1024 * 1024 && maxStateBytes in maxBodyBytes..64 * 1024 * 1024) { "tamanhos inválidos" }
    }

    companion object {
        /** chave → construtor a partir do texto (só as chaves que faz sentido trocar) */
        private val KEYS: Map<String, (LanConfig, String) -> LanConfig> = mapOf(
            "httpPort" to { c, v -> c.copy(httpPort = v.toInt()) },
            "httpsPort" to { c, v -> c.copy(httpsPort = v.toInt()) },
            "idleMinutes" to { c, v -> c.copy(idleMillis = v.toLong() * 60_000L) },
            "maxDevices" to { c, v -> c.copy(maxDevices = v.toInt()) },
            "workers" to { c, v -> c.copy(workers = v.toInt()) },
        )

        /** `"httpsPort=9443;idleMinutes=5"` (vazio = padrões). Chave desconhecida ou número inválido → exceção. */
        fun parse(spec: String): LanConfig =
            spec.split(';').map { it.trim() }.filter { it.isNotEmpty() }.fold(LanConfig()) { c, kv ->
                val k = kv.substringBefore('=').trim()
                val v = kv.substringAfter('=', "").trim()
                val set = KEYS[k] ?: throw IllegalArgumentException("chave desconhecida em LAN_CONFIG: $k")
                try { set(c, v) } catch (e: NumberFormatException) { throw IllegalArgumentException("valor inválido para $k") }
            }

        /** FINAN_LAN_HTTP_PORT, FINAN_LAN_HTTPS_PORT, FINAN_LAN_IDLE_MINUTES, FINAN_LAN_MAX_DEVICES, FINAN_LAN_WORKERS */
        fun fromEnv(env: Map<String, String> = System.getenv()): LanConfig {
            val names = mapOf(
                "FINAN_LAN_HTTP_PORT" to "httpPort", "FINAN_LAN_HTTPS_PORT" to "httpsPort", "FINAN_LAN_IDLE_MINUTES" to "idleMinutes",
                "FINAN_LAN_MAX_DEVICES" to "maxDevices", "FINAN_LAN_WORKERS" to "workers",
            )
            return parse(names.mapNotNull { (name, key) -> env[name]?.let { "$key=$it" } }.joinToString(";"))
        }
    }
}

/** Registro de eventos sem dados sensíveis: nunca código, token, conteúdo de requisição nem dados financeiros. */
fun interface LanLog {
    enum class Level { INFO, WARN, ERROR }

    fun log(level: Level, event: String, detail: String)

    fun info(event: String, detail: String = "") = log(Level.INFO, event, detail)
    fun warn(event: String, detail: String = "") = log(Level.WARN, event, detail)
    fun error(event: String, e: Throwable) = log(Level.ERROR, event, e.javaClass.simpleName) // só o tipo: a mensagem pode conter dados

    companion object {
        val NONE = LanLog { _, _, _ -> }
    }
}
