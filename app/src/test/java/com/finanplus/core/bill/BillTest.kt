// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core.bill

import com.finanplus.core.AppState
import com.finanplus.core.Card
import com.finanplus.core.Kind
import com.finanplus.core.Tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BillTest {
    private val today = LocalDate.of(2026, 10, 13)

    // exemplos públicos com os quatro dígitos verificadores corretos
    private val itau = "34191.79001 01043.510047 91020.150008 5 84380000026000"
    private val bb = "00190500954014481606906809350314337370000000100"
    private val luz = "836200000005 667800481000 180975657313 001589636081"

    @Test fun boletoBancario() {
        val b = Boleto.parse(itau, LocalDate.of(2020, 11, 1))!!
        assertEquals(Boleto.Type.BANK, b.type)
        assertEquals(26000L, b.amount)
        assertEquals(LocalDate.of(2020, 11, 13), b.due)
        assertEquals("Itaú", b.bank)
        assertEquals("34195843800000260001790001043510049102015000", b.barcode)
        // o código de barras (44) dá o mesmo resultado
        assertEquals(26000L, Boleto.parse(b.barcode, LocalDate.of(2020, 11, 1))!!.amount)
        assertEquals("Banco do Brasil", Boleto.parse(bb, today)!!.bank)
        assertEquals(100L, Boleto.parse(bb, today)!!.amount)
    }

    @Test fun boletoComDigitoErradoNaoValeNada() {
        assertNull(Boleto.parse(itau.replace("84380000026000", "84380000026100"), today)) // valor alterado: DV geral não confere
        assertNull(Boleto.parse(itau.replaceFirst("79001", "79002"), today))            // campo 1 alterado
        assertNull(Boleto.parse("1234", today))
        assertNull(Boleto.parse(luz.replace("667800481000", "667800481001"), today))
    }

    @Test fun fatorDeVencimentoAntesEDepoisDaVirada() {
        // 9999 = 21/02/2025 (base antiga); 1000 = 22/02/2025 (nova base, depois da virada)
        assertEquals(LocalDate.of(2025, 2, 21), Boleto.dueFromFactor(9999, LocalDate.of(2025, 2, 1)))
        assertEquals(LocalDate.of(2025, 2, 22), Boleto.dueFromFactor(1000, LocalDate.of(2025, 2, 1)))
        assertEquals(LocalDate.of(2026, 11, 10), Boleto.dueFromFactor(1626, today)) // 2022-05-29 + 1626
        assertNull(Boleto.dueFromFactor(0, today))
    }

    @Test fun boletoDeArrecadacao() {
        val b = Boleto.parse(luz, today)!!
        assertEquals(Boleto.Type.UTILITY, b.type)
        assertEquals(6678L, b.amount)         // R$ 66,78
        assertEquals("Energia elétrica e gás", b.segment)
        assertNull(b.due)                      // não há vencimento padronizado nesse tipo
        assertEquals(14359L, Boleto.parse("846700000017435900240209024050002435842210108119", today)!!.amount)
    }

    @Test fun arrecadacaoModulo11() {
        // monta um código com identificador 8 (módulo 11) e confere que os DVs calculados são aceitos
        val body = "848" + "00000012345" + "0001" + "2026111500000000000000000"
        val bc = body.substring(0, 3) + Boleto.mod11Utility(body) + body.substring(3)
        assertEquals(44, bc.length)
        val line = (0 until 4).joinToString("") { val p = bc.substring(it * 11, it * 11 + 11); p + Boleto.mod11Utility(p) }
        val info = Boleto.parse(line, today)!!
        assertEquals(12345L, info.amount)
        assertEquals("Telefone e internet", info.segment)
    }

    @Test fun pix() {
        assertEquals("29B1", Pix.crc16("123456789"))
        val manual = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D"
        val p = Pix.parse(manual)!!
        assertNull(p.amount)
        assertEquals("Fulano de Tal", p.name)
        val withValue = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-42665544000052040000530398654071234.565802BR5913Fulano de Tal6008BRASILIA62070503***63046EE5"
        assertEquals(123456L, Pix.parse(withValue)!!.amount)
        assertNull(Pix.parse(withValue.replace("1234.56", "1234.57"))) // CRC não confere
    }

    @Test fun codigoColado() {
        val i = BillReader.fromCode(" $itau \n", LocalDate.of(2020, 11, 1))!!
        assertEquals(26000L, i.amount!!.value)
        assertEquals(Source.BOLETO, i.amount!!.source)
        assertEquals(LocalDate.of(2020, 11, 13), i.due!!.value)
        assertNull(BillReader.fromCode("qualquer coisa", today))
    }

    @Test fun faturaDeCartaoPorRotulos() {
        val text = """
            BANCO EXEMPLO S.A.
            Resumo da fatura
            Total da fatura anterior R$ 1.020,00
            Pagamento mínimo R$ 98,31
            Total desta fatura
            R$ 1.655,40
            Data de vencimento: 15/11/2026
            Próximo vencimento 15/12/2026
            Limite disponível R$ 3.000,00
        """.trimIndent()
        val i = BillReader.fromText(text, today)
        assertEquals(165540L, i.amount!!.value)
        assertEquals(Source.TEXT, i.amount!!.source)
        assertEquals(LocalDate.of(2026, 11, 15), i.due!!.value)
    }

    @Test fun rotulosNaMesmaLinhaEMesPorExtenso() {
        val text = "Total da fatura R$ 655,40   Pagamento mínimo R$ 98,31\nVencimento 15 NOV\n"
        val i = BillReader.fromText(text, today)
        assertEquals(65540L, i.amount!!.value)
        assertEquals(LocalDate.of(2026, 11, 15), i.due!!.value)
        assertEquals(LocalDate.of(2027, 1, 5), BillReader.dateIn("vence em 5 de janeiro", today))
        assertEquals(LocalDate.of(2026, 12, 1), BillReader.dateIn("01/12/26", today))
        assertNull(BillReader.dateIn("31/02/2026", today))
    }

    @Test fun pagamentoMinimoNaoEhOTotal() {
        val i = BillReader.fromText("Pagamento mínimo do total da fatura R$ 98,31\nValor total R$ 980,00", today)
        assertEquals(98000L, i.amount!!.value)
    }

    @Test fun linhaDigitavelNoTextoTemPrioridade() {
        val text = "Valor total R$ 999,99\nVencimento 01/01/2027\nPague com o código:\n$itau\n"
        val i = BillReader.fromText(text, LocalDate.of(2020, 11, 1))
        assertEquals(26000L, i.amount!!.value)
        assertEquals(Source.BOLETO, i.amount!!.source)
        assertEquals(LocalDate.of(2020, 11, 13), i.due!!.value)
    }

    @Test fun pixNoTextoQuebradoEmLinhas() {
        val code = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-42665544000052040000530398654071234.565802BR5913Fulano de Tal6008BRASILIA62070503***63046EE5"
        val text = "Pague com Pix copia e cola:\n" + code.chunked(40).joinToString("\n")
        val i = BillReader.fromText(text, today)
        assertEquals(123456L, i.amount!!.value)
        assertEquals(Source.PIX, i.amount!!.source)
        assertEquals("Fulano de Tal", i.payee)
    }

    @Test fun textoSemNada() {
        val i = BillReader.fromText("Olá, este documento não tem valores.", today)
        assertTrue(i.isEmpty)
        assertFalse(i.dynamicPix)
    }

    @Test fun faturaDoCartao() {
        val card = Card("c", "Cartão X", 500000, close = 5, due = 12)
        val s = AppState(cards = listOf(card), txs = listOf(
            Tx("1", Kind.EXPENSE, 10000, LocalDate.of(2026, 10, 3), "A", "Outros", true, "main", cardId = "c"),  // fatura de outubro (vence 12/10)
            Tx("2", Kind.EXPENSE, 20000, LocalDate.of(2026, 10, 9), "B", "Outros", true, "main", cardId = "c"),  // fatura de novembro (vence 12/11)
            Tx("3", Kind.EXPENSE, 5000, LocalDate.of(2026, 11, 2), "C", "Outros", true, "main", cardId = "c"),   // novembro
        ))
        val ym = BillMatch.invoiceYm(s, card, LocalDate.of(2026, 11, 12), today)
        assertEquals(YearMonth.of(2026, 11), ym)
        assertEquals(25000L, BillMatch.appTotal(s, card, ym))
        // a compra da diferença cai na mesma fatura
        val d = BillMatch.dateInInvoice(card, ym)
        assertEquals(ym, com.finanplus.core.Finance.invoiceYm(card, d))
        // cartão que vence antes do fechamento: a fatura de outubro vence em novembro
        val late = Card("l", "Late", 0, close = 25, due = 5)
        assertEquals(YearMonth.of(2026, 10), BillMatch.invoiceYm(s, late, LocalDate.of(2026, 11, 5), today))
        assertNotNull(BillMatch.invoiceYm(s, card, null, today))
    }
}
