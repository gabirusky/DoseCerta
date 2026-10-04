package com.dosecerta.ui.history

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.IOException
import java.io.OutputStream

/** Renderer has no database, Context, storage choice or Intent dependency. */
class PdfReportGenerator {
    data class Result(val pageCount: Int, val rowCount: Int)
    private data class Line(val text: String, val bold: Boolean = false, val day: String? = null)

    fun write(snapshot: ReportSnapshot, output: OutputStream): Result {
        val labels = ReportText(snapshot.request)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
        val lines = mutableListOf<Line>()
        fun add(text: String, bold: Boolean = false, day: String? = null) {
            paint.isFakeBoldText = bold
            wrap(text, paint, WIDTH - 2 * MARGIN).forEach { lines += Line(it, bold, day) }
        }
        add(labels.text("Resumo do período (todos os registros)", "Period summary (all records)"), true)
        val s = snapshot.summary
        add(labels.text("Tomadas: ${s.taken} • Não confirmadas: ${s.missed} • Puladas: ${s.skipped}",
            "Taken: ${s.taken} • Unconfirmed: ${s.missed} • Skipped: ${s.skipped}"))
        add(labels.text("Adesão: ", "Adherence: ") + (s.percentage?.let { "$it%" } ?: labels.text("Sem dados", "No data")))
        add(labels.text("Avulsas/conforme necessário: ${s.extra} (fora da adesão)",
            "Extra/as-needed doses: ${s.extra} (excluded from adherence)"))
        val groups = snapshot.periodRows.groupBy { it.medicationId?.toString() ?: "extra:${it.name}" }
        add(labels.text("Medicamentos registrados no período: ${groups.size}", "Medications recorded in period: ${groups.size}"), true)
        groups.values.forEach { rows ->
            rows.distinctBy { listOf(it.name, it.dosage, it.unit, it.form, it.frequency) }.forEach { row ->
                add("${row.name} • ${row.dosage} ${row.unit} • ${labels.form(row.form)} • ${labels.frequency(row.frequency)}")
            }
        }
        add("")
        val filter = snapshot.request.statusFilter?.let(labels::status) ?: labels.text("Todos", "All")
        add(labels.text("Detalhes • filtro: $filter • ${snapshot.rows.size} registros", "Details • filter: $filter • ${snapshot.rows.size} records"), true)
        if (snapshot.rows.isEmpty()) add(labels.text("Nenhum registro neste filtro.", "No records for this filter."))
        var currentDay: String? = null
        snapshot.rows.sortedWith(compareBy<ReportRow> { it.originalDueAt }.thenBy { it.id }).forEach { row ->
            val day = labels.date(row.originalDueAt)
            if (day != currentDay) { add(day, true, day); currentDay = day }
            // An ID occurs once, even when a very long row continues on another page.
            add("[${row.id}] ${row.name} • ${row.dosage} ${row.unit}", true, day)
            add("${labels.status(row.status)} • ${labels.text("Previsto", "Scheduled")}: ${labels.dateTime(row.originalDueAt)}" +
                (row.actualTime?.let { " • ${labels.text("Tomado", "Taken")}: ${labels.dateTime(it)}" } ?: "") +
                if (row.extra) labels.text(" • Avulsa/PRN", " • Extra/PRN") else "", day = day)
            add("${labels.form(row.form)} • ${labels.frequency(row.frequency)}", day = day)
            if (row.notes.isNotBlank()) add(labels.text("Notas: ", "Notes: ") + row.notes, day = day)
            add("")
        }
        val pages = paginate(lines, labels)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, pageLines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH.toInt(), HEIGHT.toInt(), index + 1).create())
                val canvas = page.canvas
                paint.textSize = 17f; paint.isFakeBoldText = true
                canvas.drawText(labels.text("Dose Certa — relatório", "Dose Certa — report"), MARGIN, 40f, paint)
                paint.textSize = 9f; paint.isFakeBoldText = false
                canvas.drawText("${labels.date(snapshot.request.startInclusive)} — ${labels.date(snapshot.request.endExclusive - 1)} • ${snapshot.request.zoneId}", MARGIN, 58f, paint)
                canvas.drawText(labels.text("Gerado", "Generated") + ": ${labels.dateTime(snapshot.capturedAt)} • v${snapshot.appVersion}", MARGIN, 72f, paint)
                var y = CONTENT_TOP
                pageLines.forEach { line ->
                    paint.textSize = 10f; paint.isFakeBoldText = line.bold
                    canvas.drawText(line.text, MARGIN, y, paint)
                    y += LINE_HEIGHT
                }
                paint.textSize = 9f; paint.isFakeBoldText = false
                canvas.drawText(labels.text("Página", "Page") + " ${index + 1}/${pages.size}", MARGIN, HEIGHT - 38f, paint)
                canvas.drawText(labels.text("Registro pessoal; siga a prescrição profissional.", "Personal record; follow your professional prescription."), MARGIN, HEIGHT - 23f, paint)
                document.finishPage(page)
            }
            // Android 8's native PDF writer can consume the Java stream exception.
            // Remember it at the stream boundary so a failed provider never reports success.
            var writeFailure: IOException? = null
            val checkedOutput = object : OutputStream() {
                override fun write(value: Int) {
                    try { output.write(value) } catch (error: IOException) {
                        writeFailure = error
                        throw error
                    }
                }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    try { output.write(bytes, offset, length) } catch (error: IOException) {
                        writeFailure = error
                        throw error
                    }
                }
            }
            document.writeTo(checkedOutput)
            writeFailure?.let { throw it }
            output.flush()
        } finally {
            document.close()
        }
        return Result(pages.size, snapshot.rows.size)
    }

    private fun paginate(lines: List<Line>, labels: ReportText): List<List<Line>> {
        val capacity = ((HEIGHT - 65f - CONTENT_TOP) / LINE_HEIGHT).toInt()
        val pages = mutableListOf<List<Line>>()
        var page = mutableListOf<Line>()
        var day: String? = null
        for (line in lines) {
            if (page.size >= capacity) {
                pages += page
                page = mutableListOf()
                day?.let { page += Line(it + labels.text(" (continuação)", " (continued)"), true, it) }
            }
            page += line
            if (line.day != null) day = line.day
        }
        if (page.isNotEmpty()) pages += page
        return pages.ifEmpty { listOf(emptyList()) }
    }

    companion object {
        private const val WIDTH = 595f
        private const val HEIGHT = 842f
        private const val MARGIN = 36f
        private const val CONTENT_TOP = 101f
        private const val LINE_HEIGHT = 15f

        /** Splits even long unbroken strings and honors paragraph breaks; never truncates. */
        fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            require(maxWidth > 0)
            val result = mutableListOf<String>()
            text.split('\n').forEach { paragraph ->
                if (paragraph.isEmpty()) { result += ""; return@forEach }
                var remaining = paragraph
                while (remaining.isNotEmpty()) {
                    val fitted = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                    var end = fitted
                    if (end < remaining.length) {
                        val space = remaining.lastIndexOf(' ', end - 1)
                        if (space > 0) end = space
                    }
                    // Keep surrogate pairs intact when an unbroken name crosses the page width.
                    if (end < remaining.length && end > 1 && Character.isHighSurrogate(remaining[end - 1])) end--
                    result += remaining.substring(0, end)
                    remaining = remaining.substring(end).trimStart(' ')
                }
            }
            return result
        }
    }
}
