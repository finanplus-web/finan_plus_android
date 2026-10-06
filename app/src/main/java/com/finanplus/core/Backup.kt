// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

class BackupException(msg: String) : Exception(msg)

data class Dropped(val txs: Int = 0, val goals: Int = 0, val accounts: Int = 0, val cards: Int = 0, val recurring: Int = 0) {
    val total get() = txs + goals + accounts + cards + recurring
}

data class Normalized(val state: AppState, val dropped: Dropped)

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
        is Double -> numToString(v)
        is Number -> v.toString()
        else -> ""
    }.trim().take(max)

    private fun numToString(d: Double): String =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString()
        else java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble().takeIf { it.isFinite() }
        is String -> v.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        is Boolean -> if (v) 1.0 else 0.0
        else -> null
    }
    private fun cents(v: Any?): Cents = num(v)?.let { Money.fromReais(it) } ?: 0L
    private fun intIn(v: Any?, a: Int, b: Int, def: Int): Int {
        val n = num(v)?.roundToInt() ?: return def
        return if (n in a..b) n else def
    }
    private fun safeId(v: Any?): String {
        val s = when (v) { is Double -> numToString(v); is String -> v; else -> "" }
        return if (ID_RE.matches(s)) s else ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun obj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    /** Lança [BackupException] quando não há uma lista de lançamentos. */
    fun normalize(raw: Any?): Normalized {
        val r = obj(raw) ?: throw BackupException("Formato inválido")
        val rawTxs = r["txs"] as? List<*> ?: throw BackupException("Backup sem lista de lançamentos")
        var d = Dropped()
        val used = HashSet<String>()
        fun idFor(v: Any?): String { var id = safeId(v); if (id.isEmpty() || id in used) id = Ids.new(); used.add(id); return id }

        // categorias
        var cats = Categories(AppState.DEFAULT_EXPENSE, AppState.DEFAULT_INCOME)
        obj(r["cats"])?.let { c ->
            for (k in Kind.entries) {
                val list = c[k.json] as? List<*> ?: continue
                val seen = HashSet<String>(); val out = ArrayList<String>()
                for (x in list) { val n = str(x, 40); if (n.isNotEmpty() && seen.add(n.lowercase())) out.add(n) }
                if (out.isNotEmpty()) cats = cats.with(k, out)
            }
        }

        // contas
        val accounts = ArrayList<Account>()
        (r["accounts"] as? List<*>)?.forEach { x ->
            val a = obj(x)
            if (a == null || str(a["name"], 40).isEmpty()) { d = d.copy(accounts = d.accounts + 1); return@forEach }
            accounts.add(Account(idFor(a["id"]), str(a["name"], 40), cents(a["initial"])))
        }
        if (accounts.isEmpty()) { used.add(AppState.MAIN_ACCOUNT); accounts.add(Account(AppState.MAIN_ACCOUNT, "Conta principal", 0)) }
        val accIds = accounts.map { it.id }.toSet()
        val firstAcc = accounts[0].id
        fun accOf(v: Any?) = str(v, 48).let { if (it in accIds) it else firstAcc }

        // cartões
        val cards = ArrayList<Card>()
        (r["cards"] as? List<*>)?.forEach { x ->
            val c = obj(x)
            if (c == null || str(c["name"], 40).isEmpty()) { d = d.copy(cards = d.cards + 1); return@forEach }
            cards.add(Card(idFor(c["id"]), str(c["name"], 40), maxOf(0, cents(c["limit"])), intIn(c["close"], 1, 31, 5), intIn(c["due"], 1, 31, 12)))
        }
        val cardIds = cards.map { it.id }.toSet()
        fun cardOf(v: Any?) = str(v, 48).let { if (it in cardIds) it else "" }

        // recorrências
        val recurring = ArrayList<Recurring>()
        (r["recurring"] as? List<*>)?.forEach { x ->
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
            val parcel = obj(o["parcel"])
            val pTotal = parcel?.let { intIn(it["total"], 1, 120, 0) } ?: 0
            val pN = parcel?.let { intIn(it["n"], 1, 120, 0) } ?: 0
            val okParcel = pTotal > 0 && pN in 1..pTotal
            txs.add(Tx(
                id = idFor(o["id"]), kind = kind, value = value, date = date,
                desc = str(o["desc"], 200).ifEmpty { "Sem descrição" },
                category = str(o["category"], 40).ifEmpty { if (cardPayment.isNotEmpty()) AppState.CARD_PAYMENT_CAT else "Outros" },
                paid = if (cardId.isNotEmpty()) true else o["paid"] != false,
                accountId = accOf(o["accountId"]), cardId = cardId, cardPayment = cardPayment,
                recurringId = safeId(o["recurringId"]), groupId = safeId(o["groupId"]),
                parcelN = if (okParcel) pN else 0, parcelTotal = if (okParcel) pTotal else 0,
            ))
        }

        // metas
        val goals = ArrayList<Goal>()
        (r["goals"] as? List<*>)?.forEach { x ->
            val g = obj(x); val target = cents(g?.get("target"))
            if (g == null || str(g["name"], 60).isEmpty() || target <= 0) { d = d.copy(goals = d.goals + 1); return@forEach }
            goals.add(Goal(idFor(g["id"]), str(g["name"], 60), target, maxOf(0, cents(g["saved"])), parseDate(g["deadline"]), maxOf(0, cents(g["monthly"]))))
        }

        // limites
        val limits = LinkedHashMap<String, Cents>()
        obj(r["limits"])?.forEach { (k, v) -> val c = k.trim().take(40); val n = cents(v); if (c.isNotEmpty() && n > 0) limits[c] = n }

        val autoLock = num(r["autoLock"])?.roundToInt()?.takeIf { it in AppState.AUTOLOCK_OPTIONS } ?: 0
        return Normalized(
            AppState(txs, goals, accounts, cards, recurring, cats, limits, r["privacy"] == true, autoLock, ThemeId.of(r["theme"])),
            d,
        )
    }

    fun parse(text: String): Normalized = try { normalize(Json.parse(text)) } catch (e: Json.ParseException) { throw BackupException("JSON inválido: ${e.message}") }

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
        m["backupVersion"] = VERSION
        if (meta != null) m["_backup"] = meta
        return Json.stringify(m)
    }
}
