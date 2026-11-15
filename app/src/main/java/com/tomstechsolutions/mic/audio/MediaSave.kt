package com.tomstechsolutions.mic.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tomstechsolutions.mic.data.Groups
import java.io.File

/** Puts finished recordings in Music/Tom's Mic/Recordings/<group> so they open in other apps and stay after uninstalling. */
object MediaSave {
    fun mime(format: String) = when (format) {
        "wav" -> "audio/x-wav"; "flac" -> "audio/flac"; "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"; "amr", "3gp" -> "audio/3gpp"; "mp3" -> "audio/mpeg"
        else -> "audio/*"
    }

    fun ext(format: String) = when (format) { "amr" -> "3gp"; else -> format }

    /** Copies src into the shared Music folder; returns the uri. */
    fun save(ctx: Context, src: File, group: String, name: String, format: String): Uri {
        val display = safe(name) + "." + ext(format)
        val rel = Environment.DIRECTORY_MUSIC + "/Tom's Mic/Recordings/" + Groups.folder(group)
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val cv = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, display)
                    put(MediaStore.Audio.Media.MIME_TYPE, mime(format))
                    put(MediaStore.Audio.Media.RELATIVE_PATH, rel)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                    put(MediaStore.Audio.Media.IS_MUSIC, 0)
                }
                val coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = ctx.contentResolver.insert(coll, cv) ?: throw IllegalStateException("insert failed")
                ctx.contentResolver.openOutputStream(uri)!!.use { o -> src.inputStream().use { it.copyTo(o, 1 shl 16) } }
                ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                return uri
            } catch (_: Exception) { }
        } else {
            try {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Tom's Mic/Recordings/" + Groups.folder(group))
                dir.mkdirs()
                val f = unique(File(dir, display))
                src.copyTo(f, true)
                MediaScannerConnection.scanFile(ctx, arrayOf(f.path), arrayOf(mime(format)), null)
                return Uri.fromFile(f)
            } catch (_: Exception) { }
        }
        // last resort: the app's own Music folder (still visible over USB)
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir, Groups.folder(group)).apply { mkdirs() }
        val f = unique(File(dir, display))
        src.copyTo(f, true)
        return Uri.fromFile(f)
    }

    fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().take(80).ifEmpty { "Tom's Mic" }

    private fun unique(f: File): File {
        if (!f.exists()) return f
        var i = 2
        while (true) { val g = File(f.parentFile, f.nameWithoutExtension + " ($i)." + f.extension); if (!g.exists()) return g; i++ }
    }

    fun size(ctx: Context, uri: Uri): Long = try {
        if (uri.scheme == "file") File(uri.path!!).length()
        else ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
    } catch (_: Exception) { 0L }
}
