// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

class BackupException(msg: String) : Exception(msg)

data class Dropped(val txs: Int = 0, val goals: Int = 0, val accounts: Int = 0, val cards: Int = 0, val recurring: Int = 0, val shared: Int = 0) {
    val total get() = txs + goals + accounts + cards + recurring + shared
}

/** [version] = backupVersion do arquivo (null se ausente); [newer] = feito por uma versão mais nova do Finan+. */
data class Normalized(val state: AppState, val dropped: Dropped, val version: Int? = null) {
    val newer get() = (version ?: 0) > Backup.VERSION
}

/**
 * Leitura e escrita do formato JSON (o mesmo do backup do Finan+ PWA, versões 4 e 5).
 * Toda entrada externa passa por [normalize]: tipos, datas, valores, ids e referências
 * são validados; itens inválidos são descartados e contados.
 */
object Backup {
    const val VERSION = 5
    private val ID_RE = Regex("^[A-Za-z0-9_.-]{1,48}$")
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val YM_RE = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

    fun parseDate(v: Any?): LocalDate? {
        if (v !is String || !DATE_RE.matches(v)) return null
        return try { LocalDate.parse(v) } catch (e: DateTimeParseException) { null }
    }
    private fun parseYm(v: Any?): YearMonth? = if (v is String && YM_RE.matches(v)) YearMonth.parse(v) else null

    private fun str(v: Any?, max: Int = 120): String = when (v) {
        is String -> v
        is Json.NumberToken -> numToString(v)
        is Double -> numToString(v)
        is Number -> v.toString()
        else -> ""
    }.trim().take(max)

    private fun numToString(d: Double): String =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString()
        else java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()
    private fun numToString(n: Json.NumberToken): String = try {
        java.math.BigDecimal(n.text).stripTrailingZeros().toPlainString()
    } catch (_: NumberFormatException) { "" }

    private fun num(v: Any?): Double? = when (v) {
        is Json.NumberToken -> v.text.toDoubleOrNull()?.takeIf { it.isFinite() }
        is Number -> v.toDouble().takeIf { it.isFinite() }
        is String -> v.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        is Boolean -> if (v) 1.0 else 0.0
        else -> null
    }
    /** Dinheiro: número ou texto numérico; booleano não vale (true virava R$ 1,00) e valor acima de
     *  [Money.MAX_ABS] é recusado (somas estouravam e o saldo trocava de sinal). Inválido = 0. */
    private fun cents(v: Any?): Cents = when (v) {
        is Boolean -> 0L
        is Json.NumberToken -> Money.fromReaisOrNull(v.text) ?: 0L
        is String -> Money.fromReaisOrNull(v) ?: 0L
        else -> num(v)?.let { Money.fromReaisOrNull(it) } ?: 0L
    }
    private fun intIn(v: Any?, a: Int, b: Int, def: Int): Int {
        val d = num(v) ?: return def
        if (d < Int.MIN_VALUE.toDouble() || d > Int.MAX_VALUE.toDouble()) return def
        val n = d.roundToInt()
        return if (n in a..b) n else def
    }
    private fun safeId(v: Any?): String {
        val s = when (v) { is Json.NumberToken -> numToString(v); is Double -> numToString(v); is String -> v; else -> "" }
        return if (ID_RE.matches(s)) s else ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    /** Lança [BackupException] quando não há uma lista de lançamentos. */
    fun normalize(raw: Any?, enforceLimits: Boolean = true): Normalized {
        val r = obj(raw) ?: throw BackupException("Formato inválido")
        val rawTxs = r["txs"] as? List<*> ?: throw BackupException("Backup sem lista de lançamentos")
        if (enforceLimits && rawTxs.size > AppState.Limits.TRANSACTIONS) throw BackupException("Muitos lançamentos no backup")
        var d = Dropped()
        val used = HashSet<String>()
        fun idFor(v: Any?): String { var id = safeId(v); if (id.isEmpty() || id in used) id = Ids.new(); used.add(id); return id }
        // id original do arquivo → id usado aqui (quando o original é inválido ou repetido, as referências acompanham a troca)
        val accMap = HashMap<String, String>(); val cardMap = HashMap<String, String>()
        fun rawId(v: Any?) = when (v) { is Json.NumberToken -> numToString(v); is Double -> numToString(v); is String -> v.trim(); else -> "" }

        // categorias
        var cats = Categories(AppState.DEFAULT_EXPENSE, AppState.DEFAULT_INCOME)
        obj(r["cats"])?.let { c ->
            for (k in Kind.entries) {
                val list = c[k.json] as? List<*> ?: continue
                if (enforceLimits && list.size > AppState.Limits.CATEGORIES_PER_KIND) throw BackupException("Muitas categorias no backup")
                val seen = HashSet<String>(); val out = ArrayList<String>()
                for (x in list) { val n = str(x, 40); if (n.isNotEmpty() && seen.add(n.lowercase())) out.add(n) }
                if (out.isNotEmpty()) cats = cats.with(k, out)
            }
        }

        // contas
        val accounts = ArrayList<Account>()
        val rawAccounts = r["accounts"] as? List<*> ?: emptyList<Any?>()
        if (enforceLimits && rawAccounts.size > AppState.Limits.ACCOUNTS) throw BackupException("Muitas contas no backup")
        rawAccounts.forEach { x ->
            val a = obj(x)
            if (a == null || str(a["name"], 40).isEmpty()) { d = d.copy(accounts = d.accounts + 1); return@forEach }
            val id = idFor(a["id"]); rawId(a["id"]).takeIf { it.isNotEmpty() }?.let { accMap.putIfAbsent(it, id) }
            accounts.add(Account(id, str(a["name"], 40), cents(a["initial"])))
        }
        if (accounts.isEmpty()) { used.add(AppState.MAIN_ACCOUNT); accounts.add(Account(AppState.MAIN_ACCOUNT, "Conta principal", 0)) }
        val accIds = accounts.map { it.id }.toSet()
        val firstAcc = accounts[0].id
        fun accOf(v: Any?) = rawId(v).let { accMap[it] ?: if (it in accIds) it else firstAcc }

        // cartões
        val cards = ArrayList<Card>()
        val rawCards = r["cards"] as? List<*> ?: emptyList<Any?>()
        if (enforceLimits && rawCards.size > AppState.Limits.CARDS) throw BackupException("Muitos cartões no backup")
        rawCards.forEach { x ->
            val c = obj(x)
            if (c == null || str(c["name"], 40).isEmpty()) { d = d.copy(cards = d.cards + 1); return@forEach }
            val id = idFor(c["id"]); rawId(c["id"]).takeIf { it.isNotEmpty() }?.let { cardMap.putIfAbsent(it, id) }
            cards.add(Card(id, str(c["name"], 40), maxOf(0, cents(c["limit"])), intIn(c["close"], 1, 31, 5), intIn(c["due"], 1, 31, 12)))
        }
        val cardIds = cards.map { it.id }.toSet()
        fun cardOf(v: Any?) = rawId(v).let { if (it.isEmpty()) "" else cardMap[it] ?: if (it in cardIds) it else "" }

        // recorrências
        val recurring = ArrayList<Recurring>()
        val rawRecurring = r["recurring"] as? List<*> ?: emptyList<Any?>()
        if (enforceLimits && rawRecurring.size > AppState.Limits.RECURRING) throw BackupException("Muitas recorrências no backup")
        rawRecurring.forEach { x ->
            val o = obj(x); val kind = Kind.of(o?.get("kind")); val value = cents(o?.get("value"))
            if (o == null || kind == null || value <= 0 || str(o["desc"]).isEmpty()) { d = d.copy(recurring = d.recurring + 1); return@forEach }
            recurring.add(Recurring(
                id = idFor(o["id"]), kind = kind, desc = str(o["desc"]), value = value,
                category = str(o["category"], 40).ifEmpty { cats.of(kind)[0] },
                accountId = accOf(o["accountId"]), cardId = if (kind == Kind.EXPENSE) cardOf(o["cardId"]) else "",
                day = intIn(o["day"], 1, 31, 1), active = o["active"] != false,
                start = parseDate(o["start"]), last = parseYm(o["last"]),
            ))
        }

        // lançamentos
        val txs = ArrayList<Tx>()
        for (x in rawTxs) {
            val o = obj(x); val kind = Kind.of(o?.get("kind")); val value = cents(o?.get("value")); val date = parseDate(o?.get("date"))
            if (o == null || kind == null || value <= 0 || date == null) { d = d.copy(txs = d.txs + 1); continue }
            val cardId = if (kind == Kind.EXPENSE) cardOf(o["cardId"]) else ""
            val cardPayment = if (kind == Kind.EXPENSE && cardId.isEmpty()) cardOf(o["cardPayment"]) else ""
            val paid = if (cardId.isNotEmpty()) true else o["paid"] != false
            // reserva para meta (1.7.0): nunca no cartão nem junto com pagamento de fatura; sempre realizada
            val goalId = if (cardId.isEmpty() && cardPayment.isEmpty()) safeId(o["goalId"]) else ""
            // transferência entre contas (1.12.0): nunca no cartão, em fatura ou meta; sempre realizada
            val transferId = if (cardId.isEmpty() && cardPayment.isEmpty() && goalId.isEmpty()) safeId(o["transferId"]) else ""
            val scheduledDate = if (paid && cardId.isEmpty() && cardPayment.isEmpty() && goalId.isEmpty() && transferId.isEmpty()) parseDate(o["scheduledDate"]) else null
            val parcel = obj(o["parcel"])
            val pTotal = parcel?.let { intIn(it["total"], 1, 120, 0) } ?: 0
            val pN = parcel?.let { intIn(it["n"], 1, 120, 0) } ?: 0
            val okParcel = pTotal > 0 && pN in 1..pTotal
            txs.add(Tx(
                id = idFor(o["id"]), kind = kind, value = value, date = date,
                desc = str(o["desc"], 200).ifEmpty { "Sem descrição" },
                category = str(o["category"], 40).ifEmpty { if (cardPayment.isNotEmpty()) AppState.CARD_PAYMENT_CAT else if (goalId.isNotEmpty()) AppState.GOAL_CAT else if (transferId.isNotEmpty()) AppState.TRANSFER_CAT else "Outros" },
                paid = paid || goalId.isNotEmpty() || transferId.isNotEmpty(),
                accountId = accOf(o["accountId"]), cardId = cardId, cardPayment = cardPayment,
                recurringId = safeId(o["recurringId"]), groupId = safeId(o["groupId"]),
                parcelN = if (okParcel) pN else 0, parcelTotal = if (okParcel) pTotal else 0,
                scheduledDate = scheduledDate, goalId = goalId, transferId = transferId,
            ))
        }

        // metas
        val goals = ArrayList<Goal>()
        val rawGoals = r["goals"] as? List<*> ?: emptyList<Any?>()
        if (enforceLimits && rawGoals.size > AppState.Limits.GOALS) throw BackupException("Muitas metas no backup")
        rawGoals.forEach { x ->
            val g = obj(x); val target = cents(g?.get("target"))
            if (g == null || str(g["name"], 60).isEmpty() || target <= 0) { d = d.copy(goals = d.goals + 1); return@forEach }
            goals.add(Goal(idFor(g["id"]), str(g["name"], 60), target, maxOf(0, cents(g["saved"])), parseDate(g["deadline"]), maxOf(0, cents(g["monthly"]))))
        }

        // despesas compartilhadas (campo só do Android; sem ele, nenhuma)
        val shared = ArrayList<SharedExpense>()
        val rawShared = r["shared"] as? List<*> ?: emptyList<Any?>()
        if (enforceLimits && rawShared.size > AppState.Limits.SHARED) throw BackupException("Muitas despesas compartilhadas no backup")
        rawShared.forEach { x -> normalizeShared(obj(x), ::idFor)?.let { shared.add(it) } ?: run { d = d.copy(shared = d.shared + 1) } }

        // limites
        val limits = LinkedHashMap<String, Cents>()
        obj(r["limits"])?.forEach { (k, v) -> val c = k.trim().take(40); val n = cents(v); if (c.isNotEmpty() && n > 0) limits[c] = n }

        val autoLock = intIn(r["autoLock"], 0, 30, 0).takeIf { it in AppState.AUTOLOCK_OPTIONS } ?: 0
        // transferência com um lado só ou lados que não combinam vira lançamento comum (Transfers.sanitize)
        val state = AppState(Transfers.sanitize(txs), goals, accounts, cards, recurring, cats, limits, r["privacy"] == true, autoLock, ThemeId.of(r["theme"]), shared)
        if (!state.hasSafeMoneyTotals()) throw BackupException("O total dos valores excede o limite seguro")
        val version = num(r["backupVersion"])?.takeIf { it >= 0 && it <= Int.MAX_VALUE.toDouble() }?.roundToInt()
        return Normalized(state, d, version)
    }

    /**
     * Uma despesa compartilhada. Inválida (null): sem nome, total, data, participantes ou pagador, ou partes
     * que não somam o total. Pagamentos inválidos, de quem não deve ou acima da parte são descartados.
     */
    private fun normalizeShared(o: Map<String, Any?>?, idFor: (Any?) -> String): SharedExpense? {
        if (o == null) return null
        val name = str(o["name"], Shared.NAME_MAX); val total = cents(o["total"]); val date = parseDate(o["date"])
        if (name.isEmpty() || total <= 0 || date == null) return null
        val rawPeople = (o["people"] as? List<*>)?.take(Shared.PEOPLE_MAX) ?: return null
        val idMap = HashMap<String, String>()
        val people = ArrayList<SharedPerson>()
        for (x in rawPeople) {
            val p = obj(x) ?: return null
            val pn = str(p["name"], Shared.PERSON_MAX); val share = cents(p["share"])
            if (pn.isEmpty() || share < 0) return null
            val id = idFor(p["id"]); idMap.putIfAbsent(str(p["id"], 48), id)
            people.add(SharedPerson(id, pn, share))
        }
        if (people.size < 2 || people.fold(0L) { a, p -> a + p.share } != total) return null
        val payerId = idMap[str(o["payer"], 48)] ?: return null
        val e = SharedExpense(
            id = idFor(o["id"]), name = name, total = total, date = date, payerId = payerId, people = people,
            split = SplitMode.of(o["split"]), note = str(o["note"], Shared.NOTE_MAX),
            receipt = safeId(o["receipt"]), receiptType = if (safeId(o["receipt"]).isEmpty()) "" else str(o["receiptType"], 60),
        )
        // pagamentos: só de quem deve, e no máximo até a parte de cada um
        val left = Shared.summary(e).people.associate { it.person.id to it.owed }.toMutableMap()
        val payments = ArrayList<SharedPayment>()
        for (x in (o["payments"] as? List<*>)?.take(Shared.PAYMENTS_MAX) ?: emptyList<Any?>()) {
            val p = obj(x) ?: continue
            val pid = idMap[str(p["person"], 48)] ?: continue
            val v = cents(p["value"]); val pd = parseDate(p["date"]) ?: continue
            val max = left[pid] ?: 0L
            if (v <= 0 || v > max) continue
            left[pid] = max - v
            payments.add(SharedPayment(idFor(p["id"]), pid, v, pd))
        }
        return e.copy(payments = payments)
    }

    /** O BOM do início (arquivos salvos no Bloco de Notas) é ignorado, como no Finan+ web. */
    fun parse(text: String, enforceLimits: Boolean = true): Normalized = try { normalize(Json.parse(text.removePrefix("\uFEFF")), enforceLimits) } catch (e: Json.ParseException) { throw BackupException("JSON inválido: ${e.message}") }

    /** Serializa no formato do PWA (valores em reais). [meta] = bloco _backup para arquivos exportados. */
    fun toJson(s: AppState, meta: Map<String, Any?>? = null): String {
        val m = LinkedHashMap<String, Any?>()
        m["txs"] = s.txs.map { t ->
            linkedMapOf<String, Any?>(
                "id" to t.id, "kind" to t.kind.json, "value" to Money.toReaisJson(t.value), "date" to t.date.toString(),
                "desc" to t.desc, "category" to t.category, "paid" to t.paid, "accountId" to t.accountId, "cardId" to t.cardId,
            ).apply {
                if (t.cardPayment.isNotEmpty()) put("cardPayment", t.cardPayment)
                if (t.recurringId.isNotEmpty()) put("recurringId", t.recurringId)
                if (t.groupId.isNotEmpty()) put("groupId", t.groupId)
                if (t.parcelTotal > 0) put("parcel", linkedMapOf("n" to t.parcelN, "total" to t.parcelTotal))
                if (t.scheduledDate != null) put("scheduledDate", t.scheduledDate.toString())
                if (t.goalId.isNotEmpty()) put("goalId", t.goalId)
                if (t.transferId.isNotEmpty()) put("transferId", t.transferId)
            }
        }
        m["goals"] = s.goals.map { linkedMapOf("id" to it.id, "name" to it.name, "target" to Money.toReaisJson(it.target), "saved" to Money.toReaisJson(it.saved), "deadline" to (it.deadline?.toString() ?: ""), "monthly" to Money.toReaisJson(it.monthly)) }
        m["accounts"] = s.accounts.map { linkedMapOf("id" to it.id, "name" to it.name, "initial" to Money.toReaisJson(it.initial)) }
        m["cards"] = s.cards.map { linkedMapOf("id" to it.id, "name" to it.name, "limit" to Money.toReaisJson(it.limit), "close" to it.close, "due" to it.due) }
        m["recurring"] = s.recurring.map {
            linkedMapOf("id" to it.id, "kind" to it.kind.json, "desc" to it.desc, "value" to Money.toReaisJson(it.value), "category" to it.category,
                "accountId" to it.accountId, "cardId" to it.cardId, "day" to it.day, "active" to it.active,
                "start" to (it.start?.toString() ?: ""), "last" to (it.last?.toString() ?: ""))
        }
        m["cats"] = linkedMapOf("expense" to s.cats.expense, "income" to s.cats.income)
        m["limits"] = s.limits.mapValues { Money.toReaisJson(it.value) }
        m["privacy"] = s.privacy
        m["autoLock"] = s.autoLock
        m["theme"] = s.theme.json
        // só aparece quando há despesas compartilhadas (o Finan+ web ignora o campo)
        if (s.shared.isNotEmpty()) m["shared"] = s.shared.map { e ->
            linkedMapOf<String, Any?>(
                "id" to e.id, "name" to e.name, "total" to Money.toReaisJson(e.total), "date" to e.date.toString(),
                "payer" to e.payerId, "split" to e.split.json, "note" to e.note,
                "people" to e.people.map { linkedMapOf("id" to it.id, "name" to it.name, "share" to Money.toReaisJson(it.share)) },
                "payments" to e.payments.map { linkedMapOf("id" to it.id, "person" to it.personId, "value" to Money.toReaisJson(it.value), "date" to it.date.toString()) },
            ).apply { if (e.receipt.isNotEmpty()) { put("receipt", e.receipt); put("receiptType", e.receiptType) } }
        }
        m["backupVersion"] = VERSION
        if (meta != null) m["_backup"] = meta
        return Json.stringify(m)
    }
}
