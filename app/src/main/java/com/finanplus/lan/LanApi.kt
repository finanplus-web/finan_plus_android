// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import com.finanplus.core.AppState
import com.finanplus.core.Backup
import com.finanplus.core.BackupException
import com.finanplus.core.Json
import com.finanplus.core.Outcome

/**
 * O que o servidor precisa do app. Fica separado para o servidor não depender de Android
 * (roda e é testado em JVM pura, como o restante do núcleo).
 */
interface LanBackend {
    /** Estado atual (somente leitura). */
    fun state(): AppState

    /**
     * Aplica uma operação de forma atômica sobre o estado mais recente (sem perder uma alteração
     * feita no celular ao mesmo tempo) e grava. Retorna o erro de validação, ou null se deu certo.
     */
    fun apply(op: (AppState) -> Outcome): Outcome.Err?

    /** Motivo para recusar gravações agora (ex.: o celular não está conseguindo salvar), ou null. */
    fun writeBlocked(): String? = null

    /** Arquivo das páginas web (ex.: "setup.html", "pwa/index.html"), ou null se não existir. */
    fun asset(name: String): ByteArray?

    /**
     * Cores do tema do app, para o navegador ficar igual ao celular:
     * {"id": "tokyo", "light": {bg, surface, …, dark}, "dark": {…}}. null = cores padrão.
     */
    fun theme(): Map<String, Any?>? = null
}

/**
 * Versão dos dados: um contador que sobe sempre que o objeto do estado (imutável) muda — no celular ou
 * por um navegador. O PWA devolve a versão que leu ao gravar; diferente = conflito.
 */
class StateVersion {
    private var seen: AppState? = null
    private var counter = 0L

    @Synchronized
    fun of(s: AppState): Long {
        if (s !== seen) { seen = s; counter++ }
        return counter
    }

    /** Reserva a versão do estado que está para ser gravado (chamado dentro da operação atômica). */
    @Synchronized
    fun assign(next: AppState): Long { seen = next; return ++counter }
}

/** Arquivos do Finan+ web (PWA) embutidos no APK beta. Só nomes simples e tipos conhecidos. */
object PwaAssets {
    /** marca que faz o PWA entrar no modo remoto (dados no celular) */
    const val REMOTE_MARKER = "<meta name=\"finanplus-remote\" content=\"1\">"
    /** a mesma política do index.html do PWA, mais frame-ancestors (que só vale em cabeçalho) */
    const val CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

    private val SAFE = Regex("^[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*(\\.[A-Za-z0-9_-]+)*\\.[a-z0-9]+$")
    private val MIME = mapOf(
        "html" to HttpResponse.HTML, "js" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8",
        "png" to "image/png", "svg" to "image/svg+xml", "ico" to "image/x-icon", "json" to HttpResponse.JSON,
        "webmanifest" to "application/manifest+json",
    )

    /** null = não servir (404). O service worker nunca é servido: no modo remoto nada fica em cache. */
    fun serve(path: String, load: (String) -> ByteArray?): HttpResponse? {
        val name = if (path == "/") "index.html" else path.removePrefix("/")
        if (!SAFE.matches(name) || name == "sw.js") return null
        val type = MIME[name.substringAfterLast('.')] ?: return null
        val bytes = load("pwa/$name") ?: return null
        if (name != "index.html") return HttpResponse(200, type, bytes)
        val html = bytes.toString(Charsets.UTF_8)
        if (!html.contains("<head>")) return null
        return HttpResponse(200, type, html.replaceFirst("<head>", "<head>\n$REMOTE_MARKER").toByteArray(), csp = CSP)
    }
}

/** "Firefox no Linux", "Chrome no Windows"… Só um rótulo fixo para o celular mostrar (nunca o texto do navegador). */
object UserAgent {
    fun label(ua: String): String {
        val browser = when {
            "Edg/" in ua || "EdgA/" in ua || "EdgiOS/" in ua -> "Edge"
            "OPR/" in ua || "Opera" in ua -> "Opera"
            "SamsungBrowser" in ua -> "Samsung Internet"
            "Firefox/" in ua || "FxiOS/" in ua -> "Firefox"
            "Chrome/" in ua || "Chromium/" in ua || "CriOS/" in ua -> "Chrome"
            "Safari/" in ua -> "Safari"
            else -> "Navegador"
        }
        val os = when {
            "Android" in ua -> "Android"
            "iPhone" in ua -> "iPhone"
            "iPad" in ua -> "iPad"
            "Windows" in ua -> "Windows"
            "CrOS" in ua -> "ChromeOS"
            "Mac OS X" in ua || "Macintosh" in ua -> "macOS"
            "Linux" in ua -> "Linux"
            else -> null
        }
        return if (os == null) browser else "$browser no $os"
    }
}

/**
 * Rotas. Duas portas:
 *  - app (HTTPS): o PWA, o pareamento e a API do modo remoto;
 *  - instalação (HTTP): só a página do certificado e o .crt. Nada de dados.
 * Toda entrada é validada aqui, no início do fluxo, antes de chegar à lógica.
 */
class LanRoutes(
    private val cfg: LanConfig,
    private val backend: LanBackend,
    private val tls: LanTls,
    private val pairing: Pairing,
    private val versions: StateVersion,
    private val log: LanLog,
    /** dispara eventos (fora de locks) */
    private val emit: (List<LanEvent>) -> Unit,
    /** registra uso por um aparelho pareado (para o desligamento por inatividade) */
    private val touch: () -> Unit,
    /** endereço https do app, para links na página de instalação */
    private val appOrigin: () -> String,
) {
    // ------------------------------------------------------------------ porta do app (HTTPS)

    val app = HttpHandler { req, ep ->
        guard(req, ep)
        val p = req.path
        if (!p.startsWith("/api/")) {
            if (req.method != "GET") throw HttpError(HttpResponse.error(405, "Método não permitido", "Use GET."))
            return@HttpHandler PwaAssets.serve(p, backend::asset) ?: notFound()
        }
        when {
            // a página de instalação (outra origem: http://…:8090) testa se o navegador confia no certificado;
            // única rota liberada para outra origem, e sem dados
            p == "/api/ping" && req.method == "GET" -> HttpResponse(200, HttpResponse.JSON, "{\"ok\":true}".toByteArray(), corp = "cross-origin")
            p == "/api/pair" && req.method == "POST" -> pair(req)
            p.startsWith("/api/pair/") && req.method == "GET" -> pairStatus(p.removePrefix("/api/pair/"))
            else -> authenticated(req)
        }
    }

    private fun authenticated(req: HttpRequest): HttpResponse {
        val token = req.head.bearer
        if (!pairing.authenticate(token)) throw HttpError(HttpResponse.error(401, "Não pareado", "Digite o código mostrado no celular."))
        val p = req.path
        // consulta periódica do PWA ("mudou algo?"): não conta como uso, senão o servidor nunca desligaria sozinho
        if (p == "/api/rev" && req.method == "GET") return revision()
        touch()
        return when {
            p == "/api/remote/state" && req.method == "GET" -> remoteState()
            p == "/api/remote/state" && req.method == "PUT" -> remoteSave(req)
            p == "/api/logout" && req.method == "POST" -> {
                val ev = ArrayList<LanEvent>(); pairing.logout(token, ev); emit(ev)
                HttpResponse.json(mapOf("ok" to true))
            }
            else -> notFound()
        }
    }

    /**
     * Host precisa ser o endereço do celular (bloqueia "DNS rebinding": um site que aponte o próprio domínio
     * para este IP chega com outro Host). Requisições que alteram algo só podem vir da própria página.
     */
    private fun guard(req: HttpRequest, ep: HttpEndpoint) {
        if (req.head.header("host") != ep.host) throw HttpError(HttpResponse.error(403, "Acesso recusado", "Abra pelo endereço mostrado no celular."))
        val origin = req.head.header("origin")
        if (req.method != "GET" && origin != null && origin != ep.origin) throw HttpError(HttpResponse.error(403, "Acesso recusado", "Origem não permitida."))
    }

    // ------------------------------------------------------------------ pareamento

    private fun pair(req: HttpRequest): HttpResponse {
        val code = req.jsonObject()["code"] as? String
        if (code == null || !Pairing.CODE_FORMAT.matches(code)) throw HttpError(HttpResponse.error(400, "Código inválido", "O código tem 6 dígitos."))
        val ev = ArrayList<LanEvent>()
        val result = pairing.attempt(req.head.remote, code, UserAgent.label(req.head.header("user-agent") ?: ""), ev)
        emit(ev)
        return when (result) {
            is PairAttempt.Pending -> {
                log.info("pair_requested")
                HttpResponse.json(linkedMapOf("pending" to result.request.id, "label" to result.request.label, "timeout" to cfg.pairTimeoutMillis / 1000), 202)
            }
            PairAttempt.WrongCode -> { log.warn("pair_wrong_code"); HttpResponse.error(401, "Código incorreto", "Confira o código mostrado no celular.") }
            is PairAttempt.Locked -> { log.warn("pair_locked"); HttpResponse.error(429, "Muitas tentativas", "Aguarde ${result.seconds} s e use o novo código mostrado no celular.") }
            PairAttempt.DeviceLimit -> HttpResponse.error(403, "Limite de aparelhos", "Desconecte um aparelho no celular e tente de novo.")
            PairAttempt.Busy -> HttpResponse.error(429, "Pedido em andamento", "Já há um pedido aguardando resposta no celular.")
        }
    }

    private fun pairStatus(id: String): HttpResponse {
        if (!Pairing.REQUEST_ID.matches(id)) return notFound()
        return when (val s = pairing.status(id)) {
            PairStatus.Waiting -> HttpResponse.json(mapOf("status" to "waiting"))
            PairStatus.Denied -> HttpResponse.json(mapOf("status" to "denied"))
            PairStatus.Expired -> HttpResponse.json(mapOf("status" to "expired"), 410)
            is PairStatus.Approved -> { log.info("pair_approved"); HttpResponse.json(mapOf("status" to "approved", "token" to s.token)) }
        }
    }

    // ------------------------------------------------------------------ modo remoto do PWA

    /** "<versão dos dados>-<cores>" e a versão dos dados à parte. */
    private fun revision(): HttpResponse {
        val data = versions.of(backend.state())
        return HttpResponse.json(mapOf("rev" to "$data-" + Integer.toHexString(backend.theme()?.hashCode() ?: 0), "data" to data))
    }

    private fun remoteState(): HttpResponse {
        val s = backend.state()
        val body = linkedMapOf("rev" to versions.of(s), "palette" to backend.theme(), "data" to Json.Raw(Backup.toJson(s)))
        return HttpResponse(200, HttpResponse.JSON, Json.stringify(body).toByteArray())
    }

    /**
     * O PWA grava o estado inteiro (formato do backup) com a versão que leu.
     *  - versão diferente → 409 (o celular ou outro navegador mudou algo), nada gravado;
     *  - mesma validação do backup do app; se algum item fosse descartado → 422, nada gravado;
     *  - celular sem conseguir salvar → 503, nada aceito (antes a alteração ficava só na memória).
     */
    @Suppress("UNCHECKED_CAST")
    private fun remoteSave(req: HttpRequest): HttpResponse {
        backend.writeBlocked()?.let { return HttpResponse.error(503, "O celular não está salvando", it) }
        val body = req.jsonObject()
        val rev = (body["rev"] as? Double)?.takeIf { it >= 0 && it <= MAX_SAFE_INT && it == Math.floor(it) }?.toLong()
            ?: return HttpResponse.error(400, "Requisição inválida", "Versão ausente ou inválida.")
        val data = body["data"] as? Map<String, Any?> ?: return HttpResponse.error(400, "Requisição inválida", "Dados ausentes.")
        val normalized = try { Backup.normalize(data) } catch (e: BackupException) {
            return HttpResponse.error(422, "Dados inválidos", "O formato não foi reconhecido pelo celular.")
        }
        if (normalized.dropped.total > 0) {
            log.warn("remote_save_rejected", "dropped=${normalized.dropped.total}")
            return HttpResponse.error(422, "Dados recusados", "${normalized.dropped.total} item(ns) não passaram na validação do celular. Nada foi gravado.")
        }
        var conflict = false
        var newRev = 0L
        val err = backend.apply { s ->
            conflict = versions.of(s) != rev
            if (conflict) Outcome.Err("Conflito", "Os dados mudaram no celular.")
            else { newRev = versions.assign(normalized.state); Outcome.Ok(normalized.state) }
        }
        if (conflict) return HttpResponse.json(linkedMapOf("error" to true, "conflict" to true, "rev" to versions.of(backend.state()),
            "title" to "Alteração não salva", "message" to "Os dados foram alterados no celular ao mesmo tempo."), 409)
        if (err != null) return HttpResponse.error(422, err.title, err.message)
        return HttpResponse.json(mapOf("ok" to true, "rev" to newRev))
    }

    // ------------------------------------------------------------------ porta de instalação (HTTP)

    val setup = HttpHandler { req, ep ->
        guard(req, ep)
        if (req.path.startsWith("/api/")) throw HttpError(HttpResponse.error(403, "Use HTTPS", "Os dados só trafegam pela conexão segura."))
        if (req.method != "GET") throw HttpError(HttpResponse.error(405, "Método não permitido", "Use GET."))
        val ca = tls.ca
        when (req.path) {
            "/" -> setupPage()
            // DER com este tipo e sem "attachment": o Firefox abre direto a janela "Confiar nesta autoridade"
            "/finanplus-ca.crt" -> HttpResponse(200, "application/x-x509-ca-cert", ca.certDer)
            "/finanplus-ca.pem" -> HttpResponse(200, "application/x-pem-file", ca.pem.toByteArray(),
                headers = listOf("Content-Disposition" to "attachment; filename=\"finanplus-ca.pem\""))
            else -> notFound()
        }
    }

    private fun setupPage(): HttpResponse {
        val tpl = backend.asset("setup.html")?.toString(Charsets.UTF_8) ?: return notFound()
        val origin = appOrigin()
        val html = tpl
            .replace("/*{{THEME}}*/null", jsonForScript(backend.theme()))
            .replace("\"{{HTTPS_URL_JS}}\"", jsonForScript(origin)) // dentro de <script>: JSON, não HTML
            .replace("{{HTTPS_URL}}", Html.escape(origin))
            .replace("{{SHA256}}", Html.escape(tls.ca.sha256))
            .replace("{{SHA1}}", Html.escape(tls.ca.sha1))
            .replace("{{CA_NAME}}", Html.escape(tls.ca.commonName))
        // a página consulta o endereço seguro para abrir o app direto quando o navegador já confia na CA
        return HttpResponse(200, HttpResponse.HTML, html.toByteArray(), csp = HttpServer.DEFAULT_CSP.replace("connect-src 'self'", "connect-src 'self' $origin"))
    }

    private fun notFound(): HttpResponse = HttpResponse.error(404, "Não encontrado", "Endereço inexistente.")

    companion object {
        private const val MAX_SAFE_INT = 9007199254740991.0
        /** JSON seguro dentro de <script> (sem "</script>" nem comentários HTML) */
        fun jsonForScript(v: Any?) = Json.stringify(v).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    }
}

object Html {
    fun escape(s: String) = buildString(s.length) {
        for (c in s) when (c) { '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&#39;"); else -> append(c) }
    }
}
