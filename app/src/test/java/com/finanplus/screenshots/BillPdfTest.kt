// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.finanplus.data.BillPdf
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Ler fatura ou boleto" com o PdfBox-Android de verdade (e as bibliotecas que vêm com ele, como o
 * Bouncy Castle): gera faturas em PDF sem senha e com senha (AES-256 e AES-128) e lê com o mesmo
 * código do app (BillPdf). Serve para conferir que atualizar essas bibliotecas não quebra a leitura.
 * Roda junto com as capturas (`-PfinanScreenshots=true`), que já usam o Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BillPdfTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()

    /** fatura de uma página; com [password], protegida com senha de abertura */
    private fun bill(password: String?, keyLength: Int = 256): Uri {
        PDFBoxResourceLoader.init(ctx)
        val file = File.createTempFile("fatura", ".pdf", ctx.cacheDir)
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Total da fatura R$ 1.234,56")
                cs.newLineAtOffset(0f, -18f)
                cs.showText("Vencimento 15/11/2026")
                cs.endText()
            }
            if (password != null) {
                val policy = StandardProtectionPolicy("dono-$password", password, AccessPermission())
                policy.encryptionKeyLength = keyLength
                doc.protect(policy)
            }
            doc.save(file)
        }
        return Uri.fromFile(file)
    }

    private fun assertReadsBill(r: BillPdf.Result) {
        assertTrue(r is BillPdf.Result.Text, "esperava o texto da fatura, veio $r")
        val text = (r as BillPdf.Result.Text).text
        assertTrue("1.234,56" in text && "15/11/2026" in text, "texto incompleto: $text")
    }

    @Test fun semSenha() = assertReadsBill(BillPdf.read(ctx, bill(null), null))

    @Test fun comSenhaAes256() {
        val uri = bill("1234")
        assertReadsBill(BillPdf.read(ctx, uri, "1234"))
        assertEquals(BillPdf.Result.NeedsPassword, BillPdf.read(ctx, uri, null))
        assertEquals(BillPdf.Result.WrongPassword, BillPdf.read(ctx, uri, "0000"))
    }

    @Test fun comSenhaAes128() {
        val uri = bill("senha", keyLength = 128)
        assertReadsBill(BillPdf.read(ctx, uri, "senha"))
        assertEquals(BillPdf.Result.WrongPassword, BillPdf.read(ctx, uri, "errada"))
    }
}
