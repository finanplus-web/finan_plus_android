// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Abre a fatura em PDF com o PdfBox-Android (Apache 2.0, roda no aparelho) e devolve o texto.
 *
 * - O arquivo é lido só na memória: nada é gravado no aparelho, nem uma cópia sem senha.
 * - PDF com senha de abertura: só abre com a senha que a pessoa digitou (ou que ela pediu para lembrar).
 *   O app nunca tenta adivinhar a senha.
 * - PDF com "proteção" só contra cópia ou impressão abre sem senha: o texto é lido normalmente,
 *   porque é o documento da própria pessoa lido no aparelho dela.
 */
object BillPdf {
    sealed class Result {
        data class Text(val text: String) : Result()
        /** o PDF tem senha de abertura e nenhuma foi informada */
        data object NeedsPassword : Result()
        data object WrongPassword : Result()
        /** abriu, mas não tem texto (fatura escaneada ou só imagem) */
        data object NoText : Result()
        data class Error(val message: String) : Result()
    }

    /** Faturas têm poucas páginas; o resumo (total e vencimento) fica no começo. */
    private const val MAX_PAGES = 12
    private const val MAX_BYTES = 25 * 1024 * 1024

    @Volatile private var ready = false

    private fun init(context: Context) {
        if (ready) return
        synchronized(this) { if (!ready) { PDFBoxResourceLoader.init(context.applicationContext); ready = true } }
    }

    /** Chamar fora da thread principal (abrir e extrair pode levar alguns segundos em PDFs grandes). */
    fun read(context: Context, uri: Uri, password: String?): Result {
        init(context)
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return Result.Error("O arquivo é grande demais para uma fatura (mais de 25 MB).")
                }
                out.toByteArray()
            } ?: return Result.Error("Não foi possível abrir o arquivo.")
        } catch (e: SecurityException) {
            return Result.Error("O Finan+ não tem mais permissão para ler este arquivo. Escolha de novo.")
        } catch (e: Exception) {
            return Result.Error("Não foi possível ler o arquivo.")
        }
        if (bytes.size < 5 || String(bytes, 0, 5, Charsets.US_ASCII) != "%PDF-") return Result.Error("Este arquivo não é um PDF.")
        return try {
            PDDocument.load(bytes, password.orEmpty()).use { doc ->
                // senha de dono (restrições de cópia/impressão): ignorada para ler o texto
                doc.isAllSecurityToBeRemoved = true
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true // rótulo e valor da mesma linha visual ficam juntos
                    startPage = 1
                    endPage = minOf(doc.numberOfPages, MAX_PAGES)
                }
                val text = stripper.getText(doc)
                if (text.isBlank()) Result.NoText else Result.Text(text)
            }
        } catch (e: InvalidPasswordException) {
            if (password.isNullOrEmpty()) Result.NeedsPassword else Result.WrongPassword
        } catch (e: OutOfMemoryError) {
            Result.Error("O PDF é pesado demais para abrir neste aparelho.")
        } catch (e: Exception) {
            Result.Error("Não foi possível ler este PDF. Ele pode estar danificado.")
        }
    }
}
