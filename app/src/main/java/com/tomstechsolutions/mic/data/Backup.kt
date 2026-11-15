package com.tomstechsolutions.mic.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.audio.MediaSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Sao Lưu / Khôi Phục: one zip with the list of recordings, their text, settings and (optionally) the sound files. */
object Backup {
    fun name() = "TomsMic-SaoLuu-" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".zip"

    fun write(ctx: Context, out: OutputStream, withAudio: Boolean) {
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("settings.txt")); z.write(Prefs.export().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("recordings.json")); z.write(Store.exportJson().toByteArray()); z.closeEntry()
            if (withAudio) for (r in Store.recs) {
                try {
                    ctx.contentResolver.openInputStream(Uri.parse(r.uri))?.use { ins ->
                        z.putNextEntry(ZipEntry("audio/" + r.id + "." + MediaSave.ext(r.format))); ins.copyTo(z, 1 shl 16); z.closeEntry()
                    }
                } catch (_: Exception) { }
            }
        }
        Prefs.lastBackup.value = System.currentTimeMillis()
    }

    /** Restores from a zip made by write(). Sound files that are missing on this phone are put back in the Music folder. */
    fun restore(ctx: Context, uri: Uri): Int {
        var json = ""; var settings = ""
        val audio = HashMap<String, java.io.File>()
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    when {
                        e.name == "settings.txt" -> settings = z.readBytes().decodeToString()
                        e.name == "recordings.json" -> json = z.readBytes().decodeToString()
                        e.name.startsWith("audio/") -> {
                            val f = java.io.File(ctx.cacheDir, "restore_" + e.name.substringAfter("audio/"))
                            f.outputStream().use { z.copyTo(it) }
                            audio[e.name.substringAfter("audio/").substringBeforeLast('.')] = f
                        }
                    }
                }
            }
        }
        if (settings.isNotEmpty()) Prefs.import(settings)
        var n = 0
        if (json.isNotEmpty()) {
            Store.importJson(json)
            for (r in Store.recs) {
                val f = audio[r.id] ?: continue
                val readable = try { ctx.contentResolver.openInputStream(Uri.parse(r.uri))?.use { true } ?: false } catch (_: Exception) { false }
                if (!readable) { r.uri = MediaSave.save(ctx, f, r.group, r.title, r.format).toString(); n++ }
            }
            Store.save()
            n = maxOf(n, org.json.JSONArray(json).length())
        }
        audio.values.forEach { it.delete() }
        return n
    }

    /** Tự sao lưu mỗi tuần into the folder Tom chose; keeps the 5 newest. */
    fun autoToFolder(ctx: Context): Boolean {
        val tree = Prefs.backupDir.value.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) } ?: return false
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/zip", name()) ?: return false
            ctx.contentResolver.openOutputStream(doc)!!.use { write(ctx, it, false) }
            // keep 5
            val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val found = mutableListOf<Pair<String, String>>()
            ctx.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) { val nm = c.getString(1) ?: ""; if (nm.startsWith("TomsMic-SaoLuu-")) found += c.getString(0) to nm }
            }
            found.sortedByDescending { it.second }.drop(5).forEach { (id, _) ->
                try { DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } catch (_: Exception) { }
            }
            true
        } catch (_: Exception) { false }
    }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (Prefs.autoBackup.value && Prefs.backupDir.value.isNotEmpty())
            wm.enqueueUniquePeriodicWork("weekly_backup", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupWork>(7, TimeUnit.DAYS).build())
        else wm.cancelUniqueWork("weekly_backup")
    }

    /** Days since the last backup (for the reminder). */
    fun daysSince(): Long = if (Prefs.lastBackup.value == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - Prefs.lastBackup.value) / 86_400_000L
}

class BackupWork(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { if (Backup.autoToFolder(applicationContext)) Result.success() else Result.retry() }
}
