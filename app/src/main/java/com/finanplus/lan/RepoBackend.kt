// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.lan

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.finanplus.core.AppState
import com.finanplus.core.Outcome
import com.finanplus.core.ThemeId
import com.finanplus.data.LoadProblem
import com.finanplus.data.Repo
import com.finanplus.ui.theme.Palette
import com.finanplus.ui.theme.paletteFor
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/** Liga a camada de dados do app (Repo) ao servidor. */
internal class RepoBackend(private val ctx: Context) : LanBackend {
    override fun state(): AppState = Repo.state.value

    override fun apply(op: (AppState) -> Outcome): Outcome.Err? {
        var err: Outcome.Err? = null
        // Repo.update é atômico (compare-and-set): a função pode rodar de novo se o celular alterar algo ao mesmo tempo
        Repo.update { s ->
            when (val o = op(s)) {
                is Outcome.Ok -> { err = null; o.state }
                is Outcome.Err -> { err = o; s }
            }
        }
        return err
    }

    /**
     * Se o app não está conseguindo gravar (arquivo com problema, disco cheio…), aceitar uma alteração pela
     * rede seria mentir para o navegador: ela ficaria só na memória e sumiria ao fechar o app.
     */
    override fun writeBlocked(): String? {
        if (Repo.problem.value.let { it != null && it !is LoadProblem.Dropped }) return "Resolva o aviso sobre o arquivo de dados no celular."
        return Repo.saveError.value
    }

    override fun asset(name: String): ByteArray? = try {
        ctx.assets.open("lan/$name").use { it.readBytes() }
    } catch (e: FileNotFoundException) {
        null
    } catch (e: IOException) {
        Lan.log.error("asset_read_failed", e)
        null
    }

    // ---- cores do tema: recalculadas quando o tema ou o modo claro/escuro mudam, e no máximo uma vez por minuto
    // (o papel de parede do Material You pode mudar sem aviso). Antes: a cada consulta do navegador (4 s).
    private var cachedKey: Triple<ThemeId, Int, Long>? = null
    private var cached: Map<String, Any?>? = null

    @Synchronized
    override fun theme(): Map<String, Any?> {
        val id = Repo.state.value.theme
        val key = Triple(id, ctx.resources.configuration.uiMode, System.currentTimeMillis() / 60_000L)
        cached?.takeIf { cachedKey == key }?.let { return it }
        val t = linkedMapOf<String, Any?>(
            "id" to id.json,
            "light" to css(paletteFor(id, ctx, dark = false)),
            "dark" to css(paletteFor(id, ctx, dark = true)),
        )
        cachedKey = key; cached = t
        return t
    }

    private fun css(p: Palette): Map<String, Any?> = linkedMapOf(
        "dark" to p.dark,
        "bg" to rgba(p.bg), "surface" to rgba(p.surface), "border" to rgba(p.border), "text" to rgba(p.text),
        "muted" to rgba(p.muted), "accent" to rgba(p.accent), "accent2" to rgba(p.accent2), "onAccent" to rgba(p.onAccent),
        "red" to rgba(p.red), "green" to rgba(p.green), "track" to rgba(p.track), "glowA" to rgba(p.glowA), "glowB" to rgba(p.glowB),
    )

    private fun rgba(c: Color): String {
        val v = c.toArgb()
        return "rgba(${(v shr 16) and 255},${(v shr 8) and 255},${v and 255},${String.format(Locale.ROOT, "%.3f", c.alpha)})"
    }
}
