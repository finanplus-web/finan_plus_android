// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.finanplus.BuildConfig
import com.finanplus.MainActivity
import com.finanplus.R
import com.finanplus.core.Backup
import com.finanplus.core.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** O que a tela de Ajustes mostra. [lastAt] em milissegundos (0 = nunca). */
data class AutoBackupState(
    val enabled: Boolean = false,
    val folder: String = "",
    val keep: Int = AutoBackup.DEFAULT_KEEP,
    val lastAt: Long = 0,
    val lastFile: String = "",
    val lastError: String = "",
    val running: Boolean = false,
)

/**
 * Backup automático: uma vez por dia, uma cópia criptografada com senha ([BackupCrypto]) numa pasta escolhida
 * pela pessoa (Storage Access Framework: pasta do aparelho, cartão SD, Google Drive, Syncthing, Nextcloud…).
 *
 * - Arquivo `finan-plus-auto-AAAA-MM-DD.json` (um por dia; no mesmo dia, o novo substitui o anterior).
 * - Guarda as últimas [AutoBackupState.keep] cópias e apaga só arquivos com esse nome; o resto da pasta não é tocado.
 * - Pertence a ESTE aparelho: pasta, senha e opções não vão para o backup. A senha fica cifrada pelo Android Keystore.
 * - Nunca copia quando os dados do app não foram lidos ([Repo.healthy]) e não apaga cópias antigas quando o app está vazio.
 * Detalhes em BACKUP-AUTOMATICO.md.
 */
object AutoBackup {
    const val DEFAULT_KEEP = 14
    val KEEP_OPTIONS = listOf(7, 14, 30, 60)
    private const val WORK = "finanplus_backup_auto"
    private const val CHANNEL = "backup"
    private const val NOTIF_ID = 1002
    private const val PREFIX = "finan-plus-auto-"
    private val NAME_RE = Regex("^finan-plus-auto-\\d{4}-\\d{2}-\\d{2}\\.json$")
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "finanplus_autobackup_v1"

    private val _state = MutableStateFlow(AutoBackupState())
    val state: StateFlow<AutoBackupState> = _state
    private val lock = Mutex()
    @Volatile private var loaded = false

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("finanplus_autobackup", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        val p = sp(ctx)
        _state.value = AutoBackupState(
            enabled = p.getBoolean("enabled", false),
            folder = p.getString("folder", "") ?: "",
            keep = p.getInt("keep", DEFAULT_KEEP).takeIf { it in KEEP_OPTIONS } ?: DEFAULT_KEEP,
            lastAt = p.getLong("lastAt", 0),
            lastFile = p.getString("lastFile", "") ?: "",
            lastError = p.getString("lastError", "") ?: "",
        )
        loaded = true
    }

    private fun save(ctx: Context, f: (AutoBackupState) -> AutoBackupState) {
        val n = f(_state.value)
        sp(ctx).edit(commit = true) {
            putBoolean("enabled", n.enabled); putString("folder", n.folder); putInt("keep", n.keep)
            putLong("lastAt", n.lastAt); putString("lastFile", n.lastFile); putString("lastError", n.lastError)
        }
        _state.value = n
    }

    private fun tree(ctx: Context): Uri? = sp(ctx).getString("tree", null)?.let(Uri::parse)

    // ------------------------------------------------------------------ configuração

    /** Liga o backup com a pasta [tree] (vinda de OpenDocumentTree) e a [password]; faz a primeira cópia em seguida. */
    fun enable(ctx: Context, tree: Uri, password: String) {
        load(ctx)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        ctx.contentResolver.takePersistableUriPermission(tree, flags)
        releaseOld(ctx, keep = tree)
        sp(ctx).edit(commit = true) { putString("tree", tree.toString()) }
        setPassword(ctx, password)
        save(ctx) { it.copy(enabled = true, folder = folderName(ctx, tree), lastError = "") }
        schedule(ctx)
    }

    /** Troca só a pasta (a senha continua). */
    fun changeFolder(ctx: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        ctx.contentResolver.takePersistableUriPermission(tree, flags)
        releaseOld(ctx, keep = tree)
        sp(ctx).edit(commit = true) { putString("tree", tree.toString()) }
        save(ctx) { it.copy(folder = folderName(ctx, tree), lastError = "") }
    }

    fun setKeep(ctx: Context, keep: Int) = save(ctx) { it.copy(keep = keep.takeIf { k -> k in KEEP_OPTIONS } ?: DEFAULT_KEEP) }

    /** Desliga: para o agendamento e esquece pasta e senha. Os arquivos já gravados na pasta ficam. */
    fun disable(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        releaseOld(ctx, keep = null)
        sp(ctx).edit(commit = true) { remove("tree"); remove("pw") }
        deleteKey()
        save(ctx) { AutoBackupState(keep = it.keep) }
    }

    /** "Apagar todos os dados". */
    fun wipe(ctx: Context) {
        try { disable(ctx) } catch (_: Exception) { }
        sp(ctx).edit(commit = true) { clear() }
        _state.value = AutoBackupState()
    }

    private fun releaseOld(ctx: Context, keep: Uri?) {
        val old = tree(ctx) ?: return
        if (old == keep) return
        try { ctx.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) { }
    }

    private fun folderName(ctx: Context, tree: Uri): String = try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Pasta escolhida"
    } catch (_: Exception) { "Pasta escolhida" }

    // ------------------------------------------------------------------ senha (cifrada pelo Keystore)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    private fun deleteKey() { try { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) } catch (_: Exception) { } }

    fun setPassword(ctx: Context, password: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(password.toByteArray(Charsets.UTF_8))
        sp(ctx).edit(commit = true) { putString("pw", Base64.encodeToString(out, Base64.NO_WRAP)) }
    }

    private fun password(ctx: Context): String? {
        val blob = sp(ctx).getString("pw", null) ?: return null
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }

    // ------------------------------------------------------------------ agendamento

    /** Uma vez por dia, de madrugada (por volta das 3h), sem bateria fraca. Chamado ao abrir o app e ao ligar. */
    fun schedule(ctx: Context) {
        load(ctx)
        if (!_state.value.enabled) return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(3, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    // ------------------------------------------------------------------ a cópia

    /** Faz a cópia agora. Devolve null (deu certo) ou a mensagem de erro, que também fica em [state]. */
    suspend fun backupNow(ctx: Context, today: LocalDate = LocalDate.now()): String? = withContext(Dispatchers.IO) {
        lock.withLock {
            load(ctx)
            if (!_state.value.enabled) return@withLock "O backup automático está desligado."
            _state.value = _state.value.copy(running = true)
            val err = try { copy(ctx, today) } catch (e: SecurityException) {
                "Sem acesso à pasta. Escolha a pasta de novo."
            } catch (e: Exception) {
                "Não foi possível gravar a cópia (${e.javaClass.simpleName}). Confira a pasta e o espaço livre."
            }
            if (err == null) save(ctx) { it.copy(lastAt = System.currentTimeMillis(), lastError = "", running = false) }
            else { save(ctx) { it.copy(lastError = err, running = false) }; notifyFailure(ctx, err) }
            err
        }
    }

    private suspend fun copy(ctx: Context, today: LocalDate): String? {
        val tree = tree(ctx) ?: return "Escolha a pasta do backup."
        val pw = password(ctx) ?: return "A senha do backup não pôde ser lida neste aparelho. Defina a senha de novo."
        Repo.init(ctx)
        if (!Repo.healthy) return "Os dados do app não foram lidos corretamente; nenhuma cópia foi feita para não substituir as anteriores."
        Repo.flush()
        val s = Repo.state.value
        val meta = linkedMapOf<String, Any?>("app" to "Finan+", "version" to Backup.VERSION, "appVersion" to BuildConfig.VERSION_NAME,
            "createdAt" to Instant.now().toString(), "auto" to true)
        val bytes = BackupCrypto.encrypt(Backup.toJson(s, meta), pw).toByteArray(Charsets.UTF_8)

        val cr = ctx.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val children = list(ctx, tree, rootId)
        val name = "$PREFIX$today.json"
        val target = children.firstOrNull { it.second == name }?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.first) }
            ?: DocumentsContract.createDocument(cr, parent, "application/json", name)
            ?: return "Não foi possível criar o arquivo na pasta."
        (cr.openOutputStream(target, "wt") ?: return "Não foi possível gravar na pasta.").use { it.write(bytes) }
        save(ctx) { it.copy(lastFile = name) }

        // apaga as cópias mais antigas (só os arquivos do backup automático); com o app vazio, não apaga nada
        if (s.txs.isNotEmpty()) {
            val keep = _state.value.keep
            list(ctx, tree, rootId).filter { NAME_RE.matches(it.second) }.sortedByDescending { it.second }.drop(keep).forEach { (id, _) ->
                try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } catch (_: Exception) { }
            }
        }
        return null
    }

    /** (id do documento, nome) dos arquivos da pasta. */
    private fun list(ctx: Context, tree: Uri, rootId: String): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val out = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to (c.getString(1) ?: ""))
        }
        return out
    }

    @SuppressLint("MissingPermission") // verificado em canNotify()
    private fun notifyFailure(ctx: Context, msg: String) {
        if (!com.finanplus.notify.Reminders.canNotify(ctx) || !DevicePrefs.get(ctx).value.notifications) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Backup automático", NotificationManager.IMPORTANCE_LOW).apply { description = "Avisa quando o backup automático não conseguiu gravar" })
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_finan)
            .setContentTitle("Backup automático não foi feito").setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$msg Abra Ajustes › Dados."))
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (_: SecurityException) { }
    }
}

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        AutoBackup.backupNow(applicationContext)
        return Result.success() // o erro fica registrado e aparece em Ajustes › Dados; tenta de novo amanhã
    }
}
