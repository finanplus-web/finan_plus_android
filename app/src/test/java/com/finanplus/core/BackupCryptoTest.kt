// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Backup criptografado do backup automático (BACKUP-AUTOMATICO.md). */
class BackupCryptoTest {
    private val iter = 100_000 // o mínimo aceito; o app usa BackupCrypto.ITER
    private val state = AppState(
        accounts = listOf(Account("main", "Conta principal", 150000)),
        txs = listOf(Tx("a", Kind.EXPENSE, 4590, LocalDate.parse("2026-10-18"), "Mercado", "Alimentação", true, "main")),
    )

    @Test fun idaEVolta() {
        val json = Backup.toJson(state)
        val enc = BackupCrypto.encrypt(json, "senha forte 123", iter)
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(json))
        assertFalse("Mercado" in enc) // nada legível no arquivo
        val back = BackupCrypto.decrypt(enc, "senha forte 123")
        assertEquals(json, back)
        assertEquals(state.txs, Backup.parse(back).state.txs)
    }

    @Test fun cadaCopiaTemSalEIvProprios() {
        val json = Backup.toJson(state)
        assertTrue(BackupCrypto.encrypt(json, "senha forte 123", iter) != BackupCrypto.encrypt(json, "senha forte 123", iter))
    }

    @Test fun senhaErradaOuArquivoAlterado() {
        val enc = BackupCrypto.encrypt(Backup.toJson(state), "senha forte 123", iter)
        assertEquals("Senha incorreta ou arquivo danificado", assertFailsWith<BackupException> { BackupCrypto.decrypt(enc, "outra senha") }.message)
        // troca um caractere dos dados: a tag do GCM não confere
        val i = enc.indexOf("\"data\":\"") + 10
        val bad = enc.substring(0, i) + (if (enc[i] == 'A') 'B' else 'A') + enc.substring(i + 1)
        assertFailsWith<BackupException> { BackupCrypto.decrypt(bad, "senha forte 123") }
        assertFailsWith<BackupException> { BackupCrypto.decrypt("{}", "x") }
        assertFailsWith<BackupException> { BackupCrypto.decrypt("não é json", "x") }
    }

    @Test fun recusaParametrosForaDoLimite() {
        val enc = BackupCrypto.encrypt(Backup.toJson(state), "senha forte 123", iter)
        // iterações absurdas (travaria o app) ou fracas demais
        assertFailsWith<BackupException> { BackupCrypto.decrypt(enc.replace("\"iter\":100000", "\"iter\":999999999"), "senha forte 123") }
        assertFailsWith<BackupException> { BackupCrypto.decrypt(enc.replace("\"iter\":100000", "\"iter\":10"), "senha forte 123") }
        // versão desconhecida do formato
        assertFailsWith<BackupException> { BackupCrypto.decrypt(enc.replace("\"v\":1", "\"v\":2"), "senha forte 123") }
    }
}
