package com.tomstechsolutions.mic.stt

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tomstechsolutions.mic.Prefs
import com.tomstechsolutions.mic.data.Rec
import com.tomstechsolutions.mic.data.Seg
import com.tomstechsolutions.mic.t
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Saves the text of a recording as TXT, SRT (subtitles), PDF or Word. which: orig / tr / both */
object Export {
    fun speaker(s: Seg) = when (s.speaker) { 1 -> Prefs.ch1Name.value; 2 -> Prefs.ch2Name.value; else -> "" }

    private fun line(s: Seg, which: String): String {
        val who = speaker(s).let { if (it.isNotEmpty()) "$it: " else "" }
        return when (which) {
            "tr" -> who + s.tr.ifEmpty { s.text }
            "both" -> who + s.text + if (s.tr.isNotEmpty()) "\n    " + s.tr else ""
            else -> who + s.text
        }
    }

    fun clock(ms: Long, srt: Boolean = false): String {
        val h = ms / 3600000; val m = ms / 60000 % 60; val s = ms / 1000 % 60; val x = ms % 1000
        return if (srt) "%02d:%02d:%02d,%03d".format(h, m, s, x) else if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun txt(r: Rec, which: String): String = buildString {
        append(r.title).append("\n\n")
        r.segs.forEach { append("[").append(clock(it.t0)).append("] ").append(line(it, which)).append("\n") }
        append("\n— Tom's Mic · Tom's Technology Solutions\n")
    }

    fun srt(r: Rec, which: String): String = buildString {
        r.segs.forEachIndexed { i, s ->
            append(i + 1).append("\n").append(clock(s.t0, true)).append(" --> ").append(clock(s.t1, true)).append("\n")
            append(line(s, which)).append("\n\n")
        }
    }

    fun write(ctx: Context, r: Rec, kind: String, which: String): File {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val base = com.tomstechsolutions.mic.audio.MediaSave.safe(r.title)
        val f = File(dir, "$base.$kind")
        when (kind) {
            "txt" -> f.writeText(txt(r, which))
            "srt" -> f.writeText(srt(r, which))
            "pdf" -> pdf(r, which, f)
            "docx" -> docx(r, which, f)
        }
        return f
    }

    private fun pdf(r: Rec, which: String, f: File) {
        val doc = PdfDocument()
        val w = 595; val h = 842; val m = 48
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true; color = 0xFF0D2C40.toInt() }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF0F2A38.toInt() }
        val time = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF0E88AA.toInt() }
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF4F6B79.toInt() }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { it.canvas.drawText("Tom's Mic · Tom's Technology Solutions · $pageNo", m.toFloat(), (h - 24).toFloat(), foot); doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            y = m.toFloat()
        }
        newPage()
        val tl = StaticLayout.Builder.obtain(r.title, 0, r.title.length, title, w - 2 * m).build()
        page!!.canvas.save(); page!!.canvas.translate(m.toFloat(), y); tl.draw(page!!.canvas); page!!.canvas.restore(); y += tl.height + 16
        for (s in r.segs) {
            val text = line(s, which)
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, body, w - 2 * m - 54).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (y + sl.height > h - 48) newPage()
            val c = page!!.canvas
            c.drawText(clock(s.t0), m.toFloat(), y + 10, time)
            c.save(); c.translate((m + 54).toFloat(), y); sl.draw(c); c.restore()
            y += sl.height + 6
        }
        page?.let { it.canvas.drawText("Tom's Mic · Tom's Technology Solutions · $pageNo", m.toFloat(), (h - 24).toFloat(), foot); doc.finishPage(it) }
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun docx(r: Rec, which: String, f: File) {
        val paras = StringBuilder()
        fun p(text: String, bold: Boolean = false, size: Int = 22, colour: String = "0F2A38") {
            paras.append("<w:p><w:r><w:rPr>").append(if (bold) "<w:b/>" else "").append("<w:color w:val=\"$colour\"/><w:sz w:val=\"$size\"/></w:rPr><w:t xml:space=\"preserve\">")
                .append(esc(text)).append("</w:t></w:r></w:p>")
        }
        p(r.title, true, 32, "0D2C40")
        for (s in r.segs) p("[" + clock(s.t0) + "] " + line(s, which).replace("\n    ", " — "))
        p("Tom's Mic · Tom's Technology Solutions", false, 16, "4F6B79")
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            put("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$paras<w:sectPr/></w:body></w:document>")
        }
    }

    fun label(kind: String) = when (kind) { "txt" -> "TXT"; "srt" -> t("SRT (subtitles)", "SRT (phụ đề)"); "pdf" -> "PDF"; else -> "Word" }
}
