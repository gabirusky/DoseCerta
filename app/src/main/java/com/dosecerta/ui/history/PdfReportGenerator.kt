package com.dosecerta.ui.history

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dosecerta.data.local.dao.MedicationLogWithDetails
import com.dosecerta.data.model.MedicationStatus
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap

/**
 * Generates a doctor-ready PDF report of medication history.
 * Uses Android's built-in PdfDocument API — no third-party dependencies.
 *
 * Report structure:
 *   Page 1  — Summary (stats + adherence + medication list)
 *   Page 2+ — Day-by-day detail (grouped, most recent first)
 */
class PdfReportGenerator(private val context: Context) {

    // ─── Colour palette ───────────────────────────────────────────────────────
    private val teal       = Color.parseColor("#00897B")
    private val tealLight  = Color.parseColor("#E0F2F1")
    private val tealDark   = Color.parseColor("#00695C")
    private val taken      = Color.parseColor("#43A047")
    private val missed     = Color.parseColor("#E53935")
    private val skipped    = Color.parseColor("#FB8C00")
    private val textPrimary   = Color.parseColor("#212121")
    private val textSecondary = Color.parseColor("#757575")
    private val divider       = Color.parseColor("#EEEEEE")
    private val white      = Color.WHITE

    // ─── A4 page dimensions @ 72 dpi ──────────────────────────────────────────
    private val pageWidth  = 595
    private val pageHeight = 842
    private val margin     = 40f
    private val contentWidth = pageWidth - margin * 2

    // ─── Paint helpers ────────────────────────────────────────────────────────
    private fun paint(
        color: Int = textPrimary,
        size: Float = 11f,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = align
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int, width: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    // ─── Public entry point ───────────────────────────────────────────────────

    /**
     * Builds the PDF and saves it to Downloads.
     * @return URI of the saved file.
     */
    fun generate(
        logs: List<MedicationLogWithDetails>,
        periodLabel: String
    ): Uri {
        val document = PdfDocument()
        val generatedAt = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
            .format(Date())

        // ── Page 1: Summary ──────────────────────────────────────────────────
        val page1 = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        var y = drawSummaryPage(page1.canvas, logs, periodLabel, generatedAt)
        document.finishPage(page1)

        // ── Pages 2+: Day-by-day detail ───────────────────────────────────────
        val byDay = groupByDay(logs)
        var pageNum = 2
        val dayIter = byDay.iterator()
        var canvas: Canvas? = null
        var currentPage: PdfDocument.Page? = null

        // Start first detail page
        currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        canvas = currentPage.canvas
        y = drawDetailHeader(canvas, periodLabel, generatedAt)

        for ((dayLabel, dayLogs) in dayIter) {
            // Day header block height estimate
            val blockHeight = 22f + dayLogs.size * 52f + 8f
            if (y + blockHeight > pageHeight - margin) {
                document.finishPage(currentPage!!)
                pageNum++
                currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = currentPage.canvas
                y = drawDetailHeader(canvas, periodLabel, generatedAt)
            }
            y = drawDayBlock(canvas!!, dayLabel, dayLogs, y)
        }
        document.finishPage(currentPage!!)

        // ── Save ──────────────────────────────────────────────────────────────
        val fileName = "DoseCerta_Relatorio_${
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        }.pdf"

        val uri = savePdf(document, fileName)
        document.close()
        return uri
    }

    // ─── Summary Page ─────────────────────────────────────────────────────────

    private fun drawSummaryPage(
        canvas: Canvas,
        logs: List<MedicationLogWithDetails>,
        periodLabel: String,
        generatedAt: String
    ): Float {
        var y = drawPageHeader(canvas, periodLabel, generatedAt)

        // ── Stats cards ───────────────────────────────────────────────────────
        y += 20f
        val takenCount   = logs.count { it.log.status == MedicationStatus.TAKEN }
        val missedCount  = logs.count { it.log.status == MedicationStatus.MISSED }
        val skippedCount = logs.count { it.log.status == MedicationStatus.SKIPPED }
        val total = logs.size
        val adherencePct = if (total > 0) (takenCount * 100 / total) else 100

        val cardW = (contentWidth - 12f) / 3f
        val cardH = 60f
        val cards = listOf(
            Triple("Tomado",  takenCount.toString(),   taken),
            Triple("Perdido", missedCount.toString(),  missed),
            Triple("Pulado",  skippedCount.toString(), skipped)
        )
        cards.forEachIndexed { i, (label, count, color) ->
            val cx = margin + i * (cardW + 6f)
            drawStatCard(canvas, cx, y, cardW, cardH, label, count, color)
        }
        y += cardH + 12f

        // Adherence bar
        drawAdherenceBar(canvas, y, adherencePct)
        y += 44f

        // ── Section title ─────────────────────────────────────────────────────
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, fillPaint(divider).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
        y += 12f
        canvas.drawText("Medicamentos Ativos", margin, y, paint(tealDark, 13f, bold = true))
        y += 18f

        // ── Medication list (unique meds in logs) ─────────────────────────────
        val meds = logs
            .filter { it.medicationName != null }
            .distinctBy { it.log.medicationId }
            .sortedBy { it.medicationName }

        if (meds.isEmpty()) {
            canvas.drawText("Nenhum medicamento registrado neste período.", margin, y, paint(textSecondary, 10f))
            y += 16f
        } else {
            // Table header
            canvas.drawRect(RectF(margin, y - 12f, pageWidth - margin, y + 4f), fillPaint(tealLight))
            canvas.drawText("Medicamento", margin + 4f, y, paint(teal, 9f, bold = true))
            canvas.drawText("Dosagem", margin + 180f, y, paint(teal, 9f, bold = true))
            canvas.drawText("Forma", margin + 280f, y, paint(teal, 9f, bold = true))
            canvas.drawText("Frequência", margin + 370f, y, paint(teal, 9f, bold = true))
            y += 6f

            meds.forEach { med ->
                y += 16f
                canvas.drawLine(margin, y - 12f, pageWidth - margin, y - 12f, fillPaint(divider).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f })
                canvas.drawText(med.medicationName ?: "-", margin + 4f, y, paint(textPrimary, 9f, bold = true))
                canvas.drawText("${med.dosage ?: "-"} ${med.unit ?: ""}".trim(), margin + 180f, y, paint(textPrimary, 9f))
                canvas.drawText(formLabel(med), margin + 280f, y, paint(textSecondary, 9f))
                canvas.drawText(freqLabel(med), margin + 370f, y, paint(textSecondary, 9f))

                if (y > pageHeight - margin - 20f) return y // overflow guard
            }
        }

        y += 20f
        drawFooter(canvas, 1)
        return y
    }

    // ─── Detail Page Elements ─────────────────────────────────────────────────

    private fun drawDetailHeader(canvas: Canvas, periodLabel: String, generatedAt: String): Float {
        var y = drawPageHeader(canvas, periodLabel, generatedAt)
        y += 8f
        canvas.drawText("Detalhamento por Dia", margin, y, paint(tealDark, 13f, bold = true))
        return y + 18f
    }

    private fun drawDayBlock(
        canvas: Canvas,
        dayLabel: String,
        dayLogs: List<MedicationLogWithDetails>,
        startY: Float
    ): Float {
        var y = startY

        // Day pill header
        val pillPaint = fillPaint(teal)
        canvas.drawRoundRect(RectF(margin, y - 14f, margin + 180f, y + 4f), 8f, 8f, pillPaint)
        canvas.drawText(dayLabel, margin + 8f, y - 1f, paint(white, 10f, bold = true))
        y += 10f

        dayLogs.forEach { entry ->
            y += 4f
            val rowH = 44f
            val isExtra = entry.log.isExtraDose
            val status = entry.log.status
            val statusColor = when (status) {
                MedicationStatus.TAKEN   -> taken
                MedicationStatus.MISSED  -> missed
                MedicationStatus.SKIPPED -> skipped
                else -> textSecondary
            }

            // Row background
            val rowBg = if (isExtra) Color.parseColor("#FFF8E1") else white
            canvas.drawRoundRect(
                RectF(margin, y, pageWidth - margin, y + rowH),
                6f, 6f, fillPaint(rowBg)
            )
            canvas.drawRoundRect(
                RectF(margin, y, pageWidth - margin, y + rowH),
                6f, 6f, strokePaint(divider, 0.8f)
            )

            // Status dot
            canvas.drawCircle(margin + 12f, y + rowH / 2f, 5f, fillPaint(statusColor))

            // Med name
            val medName = entry.log.customMedicationName
                ?: entry.medicationName
                ?: "Medicamento desconhecido"
            val extraTag = if (isExtra) " ★" else ""
            canvas.drawText(
                medName + extraTag,
                margin + 24f, y + 16f,
                paint(textPrimary, 10f, bold = true)
            )

            // Dosage
            val dosage = if (entry.dosage != null) "${entry.dosage} ${entry.unit ?: ""}".trim() else ""
            if (dosage.isNotEmpty()) {
                canvas.drawText(dosage, margin + 24f, y + 29f, paint(textSecondary, 9f))
            }

            // Times (right side)
            val scheduledStr = "Programado: ${formatTs(entry.log.scheduledTime)}"
            canvas.drawText(scheduledStr, margin + 220f, y + 16f, paint(textSecondary, 9f))

            val actualStr = if (entry.log.actualTime != null)
                "Tomado: ${formatTs(entry.log.actualTime)}"
            else ""
            if (actualStr.isNotEmpty()) {
                canvas.drawText(actualStr, margin + 220f, y + 29f, paint(textSecondary, 9f))
            }

            // Status badge (far right)
            val statusLabel = when (status) {
                MedicationStatus.TAKEN   -> "Tomado"
                MedicationStatus.MISSED  -> "Perdido"
                MedicationStatus.SKIPPED -> "Pulado"
                else -> "Pendente"
            }
            val badgeX = pageWidth - margin - 52f
            canvas.drawRoundRect(
                RectF(badgeX, y + 12f, pageWidth - margin - 4f, y + 32f),
                10f, 10f, fillPaint(statusColor)
            )
            canvas.drawText(
                statusLabel,
                badgeX + 24f, y + 26f,
                paint(white, 8f, align = Paint.Align.CENTER)
            )

            // Notes
            if (!entry.log.notes.isNullOrBlank()) {
                y += rowH + 2f
                canvas.drawText("📝 ${entry.log.notes}", margin + 24f, y, paint(textSecondary, 8f))
                y += 6f
            } else {
                y += rowH + 4f
            }
        }
        return y + 8f
    }

    // ─── Shared Helpers ───────────────────────────────────────────────────────

    private fun drawPageHeader(canvas: Canvas, periodLabel: String, generatedAt: String): Float {
        // Teal header bar
        canvas.drawRect(RectF(0f, 0f, pageWidth.toFloat(), 56f), fillPaint(teal))

        // App title
        canvas.drawText("Dose Certa", margin, 28f, paint(white, 18f, bold = true))
        canvas.drawText("Relatório de Medicamentos", margin, 44f, paint(white, 9f))

        // Period + date (right aligned)
        canvas.drawText(periodLabel, pageWidth - margin, 28f, paint(white, 9f, align = Paint.Align.RIGHT))
        canvas.drawText("Gerado em $generatedAt", pageWidth - margin, 44f, paint(white, 8f, align = Paint.Align.RIGHT))

        return 74f
    }

    private fun drawStatCard(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        label: String, value: String, color: Int
    ) {
        // Shadow effect
        canvas.drawRoundRect(RectF(x + 2f, y + 2f, x + w + 2f, y + h + 2f), 8f, 8f, fillPaint(Color.parseColor("#22000000")))
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 8f, 8f, fillPaint(color))
        canvas.drawText(value, x + w / 2f, y + 30f, paint(white, 22f, bold = true, align = Paint.Align.CENTER))
        canvas.drawText(label, x + w / 2f, y + 48f, paint(white, 9f, align = Paint.Align.CENTER))
    }

    private fun drawAdherenceBar(canvas: Canvas, y: Float, pct: Int) {
        val barW = contentWidth
        val barH = 22f
        val filled = barW * pct / 100f

        // Background track
        canvas.drawRoundRect(RectF(margin, y, margin + barW, y + barH), 11f, 11f, fillPaint(tealLight))

        // Filled portion
        if (filled > 0f) {
            canvas.drawRoundRect(RectF(margin, y, margin + filled, y + barH), 11f, 11f, fillPaint(teal))
        }

        // Label
        canvas.drawText("Adesão: $pct%", margin + 8f, y + 15f, paint(white, 9f, bold = true))
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int) {
        val y = pageHeight - 16f
        canvas.drawLine(margin, y - 10f, pageWidth - margin, y - 10f,
            fillPaint(divider).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f })
        canvas.drawText(
            "Dose Certa  •  Dados armazenados localmente no dispositivo  •  Página $pageNum",
            pageWidth / 2f, y,
            paint(textSecondary, 7f, align = Paint.Align.CENTER)
        )
    }

    // ─── Formatting ───────────────────────────────────────────────────────────

    private fun groupByDay(logs: List<MedicationLogWithDetails>): Map<String, List<MedicationLogWithDetails>> {
        val fmt = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
        // TreeMap reversed so most recent day comes first
        val map = TreeMap<Long, Pair<String, MutableList<MedicationLogWithDetails>>>(reverseOrder())
        for (entry in logs) {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = entry.log.scheduledTime
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayKey = cal.timeInMillis
            val dayLabel = fmt.format(Date(entry.log.scheduledTime))
                .replaceFirstChar { it.uppercase() }
            map.getOrPut(dayKey) { Pair(dayLabel, mutableListOf()) }.second.add(entry)
        }
        return LinkedHashMap<String, List<MedicationLogWithDetails>>().also { result ->
            map.values.forEach { (label, list) -> result[label] = list }
        }
    }

    private fun formatTs(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    private fun formLabel(med: MedicationLogWithDetails): String {
        // We don't have form in the joined result, so just return empty
        return ""
    }

    private fun freqLabel(med: MedicationLogWithDetails): String {
        // We don't have frequency in the joined result, so return empty
        return ""
    }

    // ─── File Saving ──────────────────────────────────────────────────────────

    private fun savePdf(document: PdfDocument, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ : MediaStore (no permission needed)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)!!
            resolver.openOutputStream(uri)!!.use { document.writeTo(it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            // API 26–28 : direct file write (requires WRITE_EXTERNAL_STORAGE permission)
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { document.writeTo(it) }
            Uri.fromFile(file)
        }
    }

    /**
     * Returns an Intent to open the PDF with any available viewer.
     */
    fun openIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
}
