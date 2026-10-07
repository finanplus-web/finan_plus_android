// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import com.finanplus.core.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

// ====================================================================== mensagens

/** Cabeçalho já validado; o corpo ainda não foi lido (o limite de tamanho depende dele). */
class HttpHead(val method: String, val path: String, val headers: Map<String, String>, val remote: String) {
    fun header(name: String): String? = headers[name]
    val bearer: String? get() = headers["authorization"]?.takeIf { it.startsWith("Bearer ") }?.substring(7)?.trim()?.takeIf { it.isNotEmpty() }
}

class HttpRequest(val head: HttpHead, val body: String) {
    val method get() = head.method
    val path get() = head.path

    /** Corpo como objeto JSON; qualquer outra coisa é 400. */
    @Suppress("UNCHECKED_CAST")
    fun jsonObject(): Map<String, Any?> = try {
        Json.parse(body.ifBlank { "{}" }) as? Map<String, Any?> ?: throw HttpError(HttpResponse.error(400, "Requisição inválida", "Esperado um objeto JSON."))
    } catch (e: Json.ParseException) {
        throw HttpError(HttpResponse.error(400, "Requisição inválida", "JSON inválido."))
    }
}

/**
 * Resposta. Cabeçalhos de segurança comuns são postos pelo servidor; aqui só o que varia.
 * [csp] = política para páginas HTML (null = a padrão do servidor); [corp] = Cross-Origin-Resource-Policy.
 */
class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray,
    val headers: List<Pair<String, String>> = emptyList(),
    val csp: String? = null,
    val corp: String = "same-origin",
) {
    init {
        // valores vêm só do próprio código, mas uma quebra de linha aqui viraria injeção de cabeçalho
        require(headers.all { (k, v) -> TOKEN.matches(k) && '\r' !in v && '\n' !in v }) { "cabeçalho inválido" }
    }

    companion object {
        private val TOKEN = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
        const val JSON = "application/json; charset=utf-8"
        const val HTML = "text/html; charset=utf-8"

        fun json(v: Any?, status: Int = 200) = HttpResponse(status, JSON, Json.stringify(v).toByteArray())
        fun error(status: Int, title: String, message: String) = json(linkedMapOf("error" to true, "title" to title, "message" to message), status)
    }
}

/** Interrompe o atendimento com uma resposta pronta (erro de validação, não autorizado…). */
class HttpError(val response: HttpResponse) : Exception(null, null, false, false)

fun interface HttpHandler {
    fun handle(req: HttpRequest, endpoint: HttpEndpoint): HttpResponse
}

/** Uma porta aberta. [origin] é exatamente o que o navegador manda em Origin; [host] o que manda em Host. */
class HttpEndpoint internal constructor(val socket: ServerSocket, val secure: Boolean, val handler: HttpHandler, bind: InetAddress) {
    val port: Int = socket.localPort
    val host: String = "${bind.hostAddress}:$port"
    val origin: String = (if (secure) "https://" else "http://") + host
}

// ====================================================================== servidor

/**
 * Servidor HTTP/1.1 mínimo (uma requisição por conexão), sem bibliotecas, para a rede local.
 * Só transporte: não sabe nada do Finan+. Proteções:
 *  - poucas threads ([LanConfig.workers]) e fila curta; sem vaga, a conexão é fechada na hora
 *    (sem responder: escrever num socket TLS faria o "aperto de mão" na thread que aceita conexões);
 *  - prazo por leitura ([LanConfig.readTimeoutMillis]) e prazo total por conexão
 *    ([LanConfig.connectionDeadlineMillis]): conexões lentas de propósito ("slowloris") são derrubadas;
 *  - limites de cabeçalho e de corpo, validação estrita da linha inicial e dos cabeçalhos;
 *  - falha momentânea ao aceitar conexões é tolerada; só várias seguidas encerram o servidor.
 */
class HttpServer(
    private val bind: InetAddress,
    private val cfg: LanConfig,
    private val log: LanLog,
    /** limite do corpo para esta requisição (pode depender de quem está pedindo) */
    private val bodyLimit: (HttpHead) -> Int,
    /** chamado (uma vez) se o servidor não conseguir mais aceitar conexões */
    private val onFatal: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val endpoints = CopyOnWriteArrayList<HttpEndpoint>()
    /** conexões abertas → início; o vigia derruba as que passam do prazo */
    private val open = ConcurrentHashMap<Socket, Long>()
    private var workers: ThreadPoolExecutor? = null
    private var reaper: ScheduledExecutorService? = null
    @Volatile var running = false; private set

    /**
     * Abre a primeira porta livre a partir de [preferred] (até 10 tentativas). [create] devolve um socket
     * ainda sem endereço: SO_REUSEADDR precisa vir antes do bind, senão religar logo depois de parar
     * cai noutra porta (conexões antigas em TIME_WAIT) e o endereço mostrado muda.
     */
    fun listen(preferred: Int, secure: Boolean, create: () -> ServerSocket, handler: HttpHandler): HttpEndpoint {
        check(!running) { "chame listen antes de start" }
        var last: IOException? = null
        for (p in preferred until preferred + 10) {
            val ss = create()
            try {
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(bind, p), BACKLOG)
                return HttpEndpoint(ss, secure, handler, bind).also { endpoints += it }
            } catch (e: IOException) { closeQuietly(ss); last = e }
        }
        throw last ?: IOException("nenhuma porta disponível")
    }

    @Synchronized
    fun start() {
        check(!running && endpoints.isNotEmpty())
        workers = ThreadPoolExecutor(cfg.workers, cfg.workers, 30, TimeUnit.SECONDS, ArrayBlockingQueue(maxOf(1, cfg.queue)))
            .apply { allowCoreThreadTimeOut(true) }
        reaper = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "finan-lan-reaper").apply { isDaemon = true } }.apply {
            scheduleWithFixedDelay({ reap() }, 250, 250, TimeUnit.MILLISECONDS)
        }
        running = true
        for (ep in endpoints) Thread({ acceptLoop(ep) }, "finan-lan-accept-${ep.port}").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        if (!running && endpoints.isEmpty()) return
        running = false
        endpoints.forEach { closeQuietly(it.socket) }
        endpoints.clear()
        workers?.shutdownNow(); workers = null
        reaper?.shutdownNow(); reaper = null
        open.keys.forEach { closeQuietly(it) }
        open.clear()
    }

    private fun reap() {
        val limit = clock() - cfg.connectionDeadlineMillis
        for ((s, started) in open) if (started < limit) {
            open.remove(s)
            closeQuietly(s)
            log.warn("connection_deadline")
        }
    }

    private fun acceptLoop(ep: HttpEndpoint) {
        var failures = 0
        while (running) {
            val client = try { ep.socket.accept().also { failures = 0 } } catch (e: IOException) {
                if (!running) return
                // EMFILE, rede oscilando…: tenta de novo; muitas falhas seguidas = algo realmente quebrado
                if (++failures >= MAX_ACCEPT_FAILURES) { log.error("accept_failed", e); onFatal(); return }
                Thread.sleep(200L * failures)
                continue
            }
            open[client] = clock()
            try {
                workers?.execute { serve(client, ep) } ?: drop(client)
            } catch (e: RejectedExecutionException) {
                log.warn("busy", "port=${ep.port}")
                drop(client)
            }
        }
    }

    private fun drop(s: Socket) { open.remove(s); closeQuietly(s) }

    private fun serve(sock: Socket, ep: HttpEndpoint) {
        try {
            sock.soTimeout = cfg.readTimeoutMillis
            val input = BufferedInputStream(sock.getInputStream())
            val res = try {
                val head = readHead(input, sock.inetAddress?.hostAddress ?: "")
                val body = readBody(input, head)
                ep.handler.handle(HttpRequest(head, body), ep)
            } catch (e: HttpError) {
                e.response
            }
            write(sock.getOutputStream(), res, ep.secure)
        } catch (e: SocketTimeoutException) {
            // cliente parou de mandar: só fecha
        } catch (e: SSLException) {
            // navegador que ainda não confia no certificado fecha durante o TLS: esperado na 1ª vez
        } catch (e: IOException) {
            // conexão caiu ou foi derrubada pelo prazo
        } catch (e: RuntimeException) {
            // erro de programação: registra o tipo e responde genérico (sem detalhes para o cliente)
            log.error("handler_failed", e)
            runCatching { write(sock.getOutputStream(), HttpResponse.error(500, "Erro no servidor", "Não foi possível concluir. Veja o app no celular."), ep.secure) }
        } finally {
            drop(sock)
        }
    }

    // ------------------------------------------------------------------ leitura

    private fun readHead(input: InputStream, remote: String): HttpHead {
        var budget = cfg.maxHeaderBytes
        fun line(): String {
            val buf = ByteArrayOutputStream(128)
            while (true) {
                val b = input.read()
                if (b == -1) throw IOException("conexão fechada")
                if (--budget < 0) throw HttpError(HttpResponse.error(431, "Requisição grande demais", "Cabeçalhos excedem o limite."))
                if (b == '\n'.code) break
                if (b != '\r'.code) buf.write(b)
            }
            return buf.toString(Charsets.ISO_8859_1.name())
        }
        val parts = line().split(' ')
        if (parts.size != 3 || parts[2] != "HTTP/1.1" && parts[2] != "HTTP/1.0") throw bad("Linha inicial inválida.")
        val method = parts[0]
        if (method !in METHODS) throw HttpError(HttpResponse.error(405, "Método não permitido", "Método não suportado."))
        val target = parts[1]
        if (!target.startsWith("/") || target.length > MAX_TARGET) throw bad("Endereço inválido.")

        val headers = HashMap<String, String>()
        while (true) {
            val l = line()
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i <= 0) throw bad("Cabeçalho inválido.")
            val name = l.substring(0, i).lowercase()
            if (!HEADER_NAME.matches(name)) throw bad("Cabeçalho inválido.")
            // repetidos que mudam o significado da requisição: recusa (evita ambiguidade entre camadas)
            if (name in SINGLE && name in headers) throw bad("Cabeçalho repetido.")
            if (headers.size >= MAX_HEADERS) throw HttpError(HttpResponse.error(431, "Requisição grande demais", "Cabeçalhos demais."))
            headers[name] = l.substring(i + 1).trim()
        }
        if ("transfer-encoding" in headers) throw HttpError(HttpResponse.error(411, "Requisição inválida", "Envie o tamanho do conteúdo."))
        headers["content-length"]?.let { if (!DIGITS.matches(it)) throw bad("Tamanho inválido.") }

        // caminho: decodifica %XX uma vez e recusa o que não for um caminho simples
        val raw = target.substringBefore('?')
        val path = try { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") } catch (e: IllegalArgumentException) { throw bad("Endereço mal formado.") }
        if (path.any { it < ' ' || it == '\\' } || path.contains("..")) throw HttpError(HttpResponse.error(404, "Não encontrado", "Endereço inválido."))
        return HttpHead(method, path, headers, remote)
    }

    private fun readBody(input: InputStream, head: HttpHead): String {
        val len = head.header("content-length")?.let { it.toLongOrNull() ?: throw bad("Tamanho inválido.") } ?: 0L
        val limit = bodyLimit(head)
        if (len > limit) throw HttpError(HttpResponse.error(413, "Conteúdo grande demais", "Limite de ${limit / 1024} KB."))
        val body = ByteArray(len.toInt())
        var off = 0
        while (off < body.size) {
            val n = input.read(body, off, body.size - off)
            if (n == -1) throw IOException("conteúdo incompleto")
            off += n
        }
        return String(body, Charsets.UTF_8)
    }

    private fun bad(message: String) = HttpError(HttpResponse.error(400, "Requisição inválida", message))

    // ------------------------------------------------------------------ escrita

    private fun write(out: OutputStream, r: HttpResponse, secure: Boolean) {
        val sb = StringBuilder(512)
        fun h(k: String, v: String) { sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("HTTP/1.1 ").append(r.status).append(' ').append(REASONS[r.status] ?: "Error").append("\r\n")
        h("Content-Type", r.contentType)
        h("Content-Length", r.body.size.toString())
        h("Connection", "close")
        h("Cache-Control", "no-store")
        h("X-Content-Type-Options", "nosniff")
        h("X-Frame-Options", "DENY")
        h("Referrer-Policy", "no-referrer")
        h("Cross-Origin-Opener-Policy", "same-origin")
        h("Cross-Origin-Resource-Policy", r.corp)
        if (r.contentType.startsWith("text/html")) h("Content-Security-Policy", (r.csp ?: DEFAULT_CSP) + if (secure) "; upgrade-insecure-requests" else "")
        for ((k, v) in r.headers) h(k, v)
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(r.body)
        out.flush()
    }

    private fun closeQuietly(c: java.io.Closeable) { try { c.close() } catch (_: IOException) { /* já fechado */ } }

    companion object {
        private const val MAX_ACCEPT_FAILURES = 5
        private const val BACKLOG = 16
        private const val MAX_HEADERS = 64
        private const val MAX_TARGET = 2048
        private val METHODS = setOf("GET", "POST", "PUT", "DELETE")
        private val SINGLE = setOf("host", "content-length", "authorization", "origin")
        private val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9a-z-]+$")
        private val DIGITS = Regex("^[0-9]{1,10}$")
        const val DEFAULT_CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"
        private val REASONS = mapOf(
            200 to "OK", 202 to "Accepted", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found",
            405 to "Method Not Allowed", 409 to "Conflict", 410 to "Gone", 411 to "Length Required", 413 to "Payload Too Large",
            422 to "Unprocessable Entity", 429 to "Too Many Requests", 431 to "Request Header Fields Too Large", 500 to "Internal Server Error",
            503 to "Service Unavailable",
        )
    }
}
