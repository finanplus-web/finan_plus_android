// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun d(s: String) = LocalDate.parse(s)
private fun tx(id: String, kind: Kind, value: Long, date: String, paid: Boolean = true, card: String = "", pay: String = "", acc: String = "main", cat: String = "Outros") =
    Tx(id, kind, value, d(date), id, cat, paid, acc, card, pay)
private fun AppState.ok(o: Outcome): AppState = when (o) { is Outcome.Ok -> o.state; is Outcome.Err -> error("esperava Ok: ${o.message}") }
private fun Outcome.msg() = (this as Outcome.Err).message

class JsonTest {
    @Test fun roundTrip() {
        val v = Json.parse("""{"a":[1,2.5,"x\"y\u00e9",true,null],"b":{}}""")
        assertEquals("""{"a":[1,2.5,"x\"yé",true,null],"b":{}}""", Json.stringify(v))
    }
    @Test fun rejectsGarbage() {
        for (bad in listOf("{", "{\"txs\":[{\"id\":1,\"kind\":\"expense\"", "[1,]", "tru", "{}x", "\"\\q\"")) assertFailsWith<Json.ParseException>(bad) { Json.parse(bad) }
    }
}

class MoneyTest {
    @Test fun parseBrazilianFormats() {
        val cases = mapOf("1500,50" to 150050L, "1.500,50" to 150050L, "1500.50" to 150050L, "R$ 2.000" to 200000L,
            "1,500.25" to 150025L, "-50" to -5000L, "0,1" to 10L, "10" to 1000L, "1,999" to 200L)
        for ((k, v) in cases) assertEquals(v, Money.parse(k), k)
        for (bad in listOf("", "abc", "1,2,3", "1.2.3,4.5", "x", "-")) assertNull(Money.parse(bad), bad)
    }
    @Test fun formatNeverShowsMinusZero() {
        assertEquals("R$ 1.234.567,89", Money.format(123456789))
        assertEquals("R$ 0,00", Money.format(0))
        assertEquals("-R$ 0,50", Money.format(-50))
        assertEquals("1500,50", Money.input(150050))
    }
}

class BackupTest {
    @Test fun freshStatesAreIndependent() {
        val a = AppState(); val b = AppState()
        assertEquals(0, b.txs.size); assertEquals(a, b)
    }
    @Test fun rejectsWithoutTxList() {
        for (raw in listOf("null", "{}", """{"txs":"x"}""", "[]")) assertFailsWith<BackupException>(raw) { Backup.parse(raw) }
    }
    @Test fun dropsInvalidAndFillsMissing() {
        val (s, dr) = Backup.parse("""{"txs":[
            {"id":1,"kind":"expense","value":10,"date":"2026-10-01","desc":"ok","category":"Lazer"},
            {"id":2,"kind":"expense","value":10,"desc":"sem data"},
            {"id":3,"kind":"expense","value":10,"date":"2026-02-31"},
            {"id":4,"kind":"x","value":10,"date":"2026-10-01"},
            {"id":5,"kind":"income","value":-3,"date":"2026-10-01"},
            {"id":6,"kind":"income","value":"12.5","date":"2026-10-02"},"lixo",null],
            "cats":{"expense":["A","a","",7]},"accounts":"nope","goals":[{"name":"g","target":0}],"limits":{"A":"abc","B":50}}""")
        assertEquals(2, s.txs.size); assertEquals(6, dr.txs)
        assertEquals(1250L, s.txs[1].value)
        assertEquals(listOf("A", "7"), s.cats.expense)
        assertEquals(AppState.DEFAULT_INCOME, s.cats.income)
        assertEquals("main", s.accounts[0].id)
        assertEquals(0, s.goals.size)
        assertEquals(mapOf("B" to 5000L), s.limits)
    }
    @Test fun neutralizesHtmlInIdsAndNumbers() {
        val evil = "\\\"><img src=x onerror=alert(1)>"
        val (s, _) = Backup.parse("""{"txs":[{"id":"$evil","kind":"expense","value":1,"date":"2026-10-01","cardId":"c1"}],
            "cards":[{"id":"c1","name":"Nu","limit":100,"close":"$evil","due":"$evil"}],
            "accounts":[{"id":"$evil","name":"A","initial":0}],"theme":"$evil","autoLock":"$evil"}""")
        assertEquals(5, s.cards[0].close); assertEquals(12, s.cards[0].due)
        assertTrue(Regex("^[A-Za-z0-9_.-]+$").matches(s.txs[0].id))
        assertTrue(Regex("^[A-Za-z0-9_.-]+$").matches(s.accounts[0].id))
        assertEquals(ThemeId.AUTO, s.theme); assertEquals(0, s.autoLock)
        assertEquals("c1", s.txs[0].cardId)
    }
    @Test fun duplicateIdsAndBrokenRefs() {
        val (s, _) = Backup.parse("""{"txs":[{"id":1,"kind":"expense","value":1,"date":"2026-10-01","accountId":"x","cardId":"x"},
            {"id":1,"kind":"income","value":1,"date":"2026-10-01","cardId":"c"}],"cards":[{"id":"c","name":"C"}]}""")
        assertNotEquals(s.txs[0].id, s.txs[1].id)
        assertEquals("main", s.txs[0].accountId); assertEquals("", s.txs[0].cardId)
        assertEquals("", s.txs[1].cardId, "receita não vai para cartão")
    }
    @Test fun readsPwaIdsAndExportsCompatibleJson() {
        val (s, _) = Backup.parse("""{"txs":[{"id":1696000000000.1234,"kind":"expense","value":0.1,"date":"2026-10-01","parcel":{"n":1,"total":3},"groupId":"g1"}],"theme":"dark","pin":"p1234"}""")
        assertTrue(s.txs[0].id.startsWith("1696000000000.123"))
        assertEquals(10L, s.txs[0].value)
        assertEquals(ThemeId.OLED, s.theme)
        val json = Backup.toJson(s, mapOf("app" to "Finan+"))
        assertTrue(json.contains("\"value\":0.10")); assertTrue(!json.contains("pin"))
        val again = Backup.parse(json).state
        assertEquals(s, again)
    }
}

class FinanceTest {
    private val card = Card("c", "C", 100000, 5, 12)

    @Test fun datesClampToMonthEnd() {
        assertEquals(d("2026-02-28"), d("2026-01-31").plusMonthsClamped(1))
        assertEquals(d("2027-01-15"), d("2026-12-15").plusMonthsClamped(1))
        assertEquals(d("2028-02-29"), YearMonth.of(2028, 2).dayClamped(31))
    }
    @Test fun balanceIncludesInitialCardDoesNotTouchAccountPaymentDoes() {
        var s = AppState(accounts = listOf(Account("main", "Conta", 100000)), cards = listOf(card.copy(limit = 50000)))
        assertEquals(100000L, Finance.currentBalance(s))
        s = s.copy(txs = s.txs + tx("t1", Kind.EXPENSE, 20000, "2026-10-02", card = "c"))
        assertEquals(100000L, Finance.currentBalance(s))
        assertEquals(Finance.accountBalance(s, s.accounts[0]), Finance.currentBalance(s))
        s = s.copy(txs = s.txs + tx("t2", Kind.EXPENSE, 20000, "2026-10-12", pay = "c"))
        assertEquals(80000L, Finance.currentBalance(s))
        assertEquals(0L, Finance.cardStatus(s, s.cards[0], d("2026-10-20")).used)
    }
    @Test fun invoiceRespectsCloseAndDueAndFutureParcelsUseLimit() {
        assertEquals(YearMonth.of(2026, 10), Finance.invoiceYm(card, d("2026-10-05")))
        assertEquals(YearMonth.of(2026, 11), Finance.invoiceYm(card, d("2026-10-06")))
        assertEquals(d("2026-10-12"), Finance.invoiceDue(card, YearMonth.of(2026, 10)))
        assertEquals(d("2026-11-05"), Finance.invoiceDue(Card("d", "D", 0, 25, 5), YearMonth.of(2026, 10)))
        val s = AppState(cards = listOf(card), txs = listOf("2026-10-02", "2026-11-02", "2026-12-02").mapIndexed { i, x -> tx("p$i", Kind.EXPENSE, 10000, x, card = "c") })
        val st = Finance.cardStatus(s, card, d("2026-10-03"))
        assertEquals(30000L, st.used); assertEquals(70000L, st.available)
        assertEquals(YearMonth.of(2026, 10), st.current!!.ym); assertEquals(10000L, st.current!!.open)
    }
    @Test fun futureBalanceCountsPendingAndInvoices() {
        val s = AppState(accounts = listOf(Account("main", "C", 100000)), cards = listOf(card), txs = listOf(
            tx("a", Kind.EXPENSE, 30000, "2026-10-02", card = "c"),
            tx("b", Kind.INCOME, 5000, "2026-10-20", paid = false),
            tx("n", Kind.EXPENSE, 99900, "2026-11-20", paid = false)))
        assertEquals(75000L, Finance.futureBalance(s, d("2026-10-31"), d("2026-10-03")))
    }
    @Test fun recurringStartingInFutureGeneratesNothing() {
        val r = Recurring("r", Kind.EXPENSE, "Aluguel", 1000, "Moradia", "main", "", 10, true, d("2026-12-10"), YearMonth.of(2026, 12))
        assertEquals(0, Finance.generateRecurring(AppState(recurring = listOf(r)), d("2026-10-03")).second)
        assertEquals(0, Finance.generateRecurring(AppState(recurring = listOf(r.copy(last = null))), d("2026-10-03")).second)
    }
    @Test fun recurringCatchesUpMissedMonthsWithoutDuplicates() {
        val r = Recurring("r", Kind.INCOME, "Salário", 1000, "Salário", "main", "", 31, true, d("2026-06-30"), YearMonth.of(2026, 7))
        val (s, n) = Finance.generateRecurring(AppState(recurring = listOf(r)), d("2026-10-03"))
        assertEquals(3, n)
        assertEquals(listOf("2026-08-31", "2026-09-30", "2026-10-31"), s.txs.map { it.date.toString() })
        assertEquals(0, Finance.generateRecurring(s, d("2026-10-20")).second)
    }
    @Test fun recurringNeverBeforeStartDate() {
        val r = Recurring("r", Kind.EXPENSE, "x", 1000, "Lazer", "main", "", 5, true, d("2026-10-15"), null)
        assertEquals(listOf("2026-11-05"), Finance.generateRecurring(AppState(recurring = listOf(r)), d("2026-11-20")).first.txs.map { it.date.toString() })
    }
    @Test fun installmentsSplitInCents() {
        assertEquals(listOf(3334L, 3333L, 3333L), Finance.splitInstallments(10000, 3))
    }
    @Test fun goalPlan() {
        val p = Finance.goalPlan(Goal("g", "G", 120000, 20000, d("2027-03-31"), 10000), d("2026-10-03"))
        assertEquals(100000L, p.remaining); assertEquals(16667L, p.needed)
        assertEquals(YearMonth.of(2027, 7), p.eta); assertTrue(p.late)
        assertTrue(Finance.goalPlan(Goal("g", "G", 1000, 1000, null, 0), d("2026-10-03")).done)
    }
    @Test fun remindersForBillsAndInvoices() {
        val s = AppState(cards = listOf(card), txs = listOf(
            tx("late", Kind.EXPENSE, 100, "2026-10-01", paid = false),
            tx("soon", Kind.EXPENSE, 200, "2026-10-04", paid = false),
            tx("far", Kind.EXPENSE, 300, "2026-10-20", paid = false),
            tx("buy", Kind.EXPENSE, 400, "2026-10-01", card = "c")))
        val r = Finance.reminders(s, d("2026-10-10"))
        assertEquals(listOf(ReminderType.BILL_OVERDUE, ReminderType.BILL_OVERDUE, ReminderType.INVOICE_DUE), r.map { it.type })
        assertEquals("Fatura C", r.last().title)
        assertEquals("late", Finance.nextDue(s, d("2026-10-02"))!!.refId)
    }
    @Test fun csvEscapesAndBlocksFormulas() {
        val s = AppState(cards = listOf(Card("c", "Nu", 1, 1, 2)), txs = listOf(Tx("a", Kind.EXPENSE, 1050, d("2026-10-01"), "=HYPERLINK(\"x\")", "Ca;sa \"1\"", true, "main", "c")))
        val csv = Csv.build(s)
        assertTrue(csv.startsWith("\uFEFFdata;tipo;categoria;descricao;valor;situacao;conta;cartao"))
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""))
        assertTrue(csv.contains("\"Ca;sa \"\"1\"\"\""))
        assertTrue(csv.contains("\"10,50\""))
        assertTrue(csv.endsWith("\"Nu\""))
    }
}

class OpsTest {
    private val base = AppState()
    private fun draft(value: String = "100", reps: Int = 1, mode: RepsMode = RepsMode.TOTAL, card: String = "", rec: Boolean = false, date: String = "2026-10-03", desc: String = "Mercado") =
        TxDraft(Kind.EXPENSE, desc, value, "Alimentação", d(date), true, "main", card, reps, mode, rec)

    @Test fun validationMessages() {
        assertEquals("Informe uma descrição.", Ops.saveTx(base, null, draft(desc = "   ")).msg())
        assertTrue(Ops.saveTx(base, null, draft(value = "0")).msg().contains("maior que zero"))
        assertTrue(Ops.saveTx(base, null, draft(card = "nope")).msg().contains("Cadastre um cartão"))
        assertTrue(Ops.saveTx(base, null, draft(reps = 3, rec = true)).msg().contains("parcelas ou repetição"))
        assertEquals("Informe o nome da conta.", Ops.saveAccount(base, null, "", "").msg())
    }
    @Test fun installmentsLinkedAndDeletedTogether() {
        val s = base.ok(Ops.saveTx(base, null, draft(value = "1000", reps = 3)))
        assertEquals(listOf(33334L, 33333L, 33333L), s.txs.map { it.value })
        assertEquals(listOf(true, false, false), s.txs.map { it.paid })
        assertEquals(1, s.txs.map { it.groupId }.toSet().size)
        assertEquals("Mercado (2/3)", s.txs[1].desc)
        assertEquals(2, Ops.laterParcels(s, s.txs[0].id).size)
        assertEquals(0, Ops.deleteTx(s, s.txs[0].id, true).txs.size)
        assertEquals(2, Ops.deleteTx(s, s.txs[0].id, false).txs.size)
    }
    @Test fun recurringFromTxStartingInTwoMonthsDoesNotDuplicate() {
        val s = base.ok(Ops.saveTx(base, null, draft(rec = true, date = "2026-12-10")))
        assertEquals(1, s.txs.size)
        assertEquals(0, Finance.generateRecurring(s, d("2026-10-03")).second)
        assertEquals(0, Finance.generateRecurring(s, d("2026-12-20")).second)
        assertEquals(1, Finance.generateRecurring(s, d("2027-01-20")).second)
    }
    @Test fun cardPurchaseAndInvoicePayment() {
        var s = base.ok(Ops.saveAccount(base, "main", "Conta", "1.000,00"))
        s = s.ok(Ops.saveCard(s, null, "Nu", "500", "5", "12"))
        val c = s.cards[0].id
        s = s.ok(Ops.saveTx(s, null, draft(value = "300", reps = 3, card = c)))
        assertTrue(s.txs.all { it.paid && it.cardId == c })
        assertEquals(100000L, Finance.currentBalance(s))
        assertEquals(20000L, Finance.cardStatus(s, s.cards[0], d("2026-10-03")).available)
        s = s.ok(Ops.payInvoice(s, c, "100", "main", d("2026-10-12")))
        assertEquals(90000L, Finance.currentBalance(s))
        assertTrue(Ops.deleteCard(s, c).msg().contains("compras ou pagamentos"))
    }
    @Test fun goalCentsEditAndMove() {
        var s = base.ok(Ops.saveGoal(base, null, "Carro", "1500,50", "", null, "100"))
        val g = s.goals[0]
        assertEquals(150050L, g.target)
        s = s.ok(Ops.saveGoal(s, g.id, "Carro novo", "1500,50", "200,25", null, "100"))
        assertEquals(20025L, s.goals[0].saved); assertEquals("Carro novo", s.goals[0].name)
        s = s.ok(Ops.saveGoal(s, g.id, "Carro novo", "1500,50", "-999999", null, ""))
        assertEquals(0L, s.goals[0].saved)
    }
    @Test fun renameCategoryCarriesTxsLimitsAndRecurring() {
        var s = base.ok(Ops.saveTx(base, null, draft(rec = true)))
        s = s.ok(Ops.saveLimit(s, null, "Alimentação", "300"))
        s = s.ok(Ops.renameCategory(s, Kind.EXPENSE, "Alimentação", "Comida"))
        assertEquals("Comida", s.txs[0].category); assertEquals("Comida", s.recurring[0].category)
        assertEquals(mapOf("Comida" to 30000L), s.limits)
        assertTrue(Ops.addCategory(s, Kind.EXPENSE, "comida").msg().contains("já existe"))
        assertTrue(Ops.checkDeleteCategory(s, Kind.EXPENSE, "Comida").msg().contains("recorrência"))
    }
    @Test fun accountInUseCannotBeDeleted() {
        var s = base.ok(Ops.saveAccount(base, null, "Banco 2", ""))
        val b2 = s.accounts[1].id
        s = s.ok(Ops.saveRecurring(s, null, Kind.EXPENSE, "Curso", "250", "10", "Educação", b2, "", true, d("2026-12-01"), d("2026-10-03")))
        assertTrue(Ops.deleteAccount(s, b2).msg().contains("recorrências"))
        assertTrue(Ops.deleteAccount(AppState(), "main").msg().contains("pelo menos uma"))
    }
}
