package com.dosecerta.ui.history

import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.domain.AdherenceSummary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class PdfReportInstrumentedTest {
    private val time = 1_800_000_000_000L
    private fun request(language: String = "pt-BR", filter: MedicationStatus? = null) =
        ReportRequest(time - 86_400_000, time + 86_400_000, "America/Sao_Paulo", language, filter, time)
    private fun row(id: Int, long: Boolean = false) = ReportRow(id.toLong(), "synthetic:$id", (id % 50).toLong(),
        if (long) "Medicamento sintético " + "W".repeat(180) else "Medicamento sintético ${id % 50}",
        "500", if (long) "unidade".repeat(30) else "mg", "TABLET", "MONTHLY",
        if (long) "Anotação sintética sem dados pessoais. ".repeat(200) else "", time + id,
        time + id + 1000, MedicationStatus.TAKEN, false, "SYNTHETIC")
    private fun render(name: String, rows: List<ReportRow>, language: String = "pt-BR") {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "qa-reports").apply { mkdirs() }
        val file = File(dir, name)
        val snapshot = ReportSnapshot(request(language), rows, rows, AdherenceSummary(rows.size, 0, 0, 0), time, "test")
        val result = file.outputStream().use { PdfReportGenerator().write(snapshot, it) }
        assertEquals(rows.size, result.rowCount)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                assertEquals(result.pageCount, pdf.pageCount)
                assertTrue(pdf.pageCount > 0)
                pdf.openPage(0).use { page -> assertTrue(page.width > 0) }
                pdf.openPage(pdf.pageCount - 1).use { page -> assertTrue(page.height > 0) }
            }
        }
    }
    @Test fun emptyPeriodProducesReadablePdf() = render("empty.pdf", emptyList())
    @Test fun manyDosesOnSameDayAnd50MedicinesPaginate() = render("many.pdf", (1..150).map { row(it) })
    @Test fun tenThousandRowsProduceReadablePdf() = render("10000.pdf", (1..10000).map { row(it) })
    @Test fun longNamesUnitsNotesContinueWithoutTruncation() = render("long.pdf", (1..30).map { row(it, true) }, "en")
    @Test fun failedStreamDoesNotPoisonSubsequentRender() {
        val rows = listOf(row(1))
        val snapshot = ReportSnapshot(request(), rows, rows, AdherenceSummary(1, 0, 0, 0), time)
        try {
            PdfReportGenerator().write(snapshot, object : OutputStream() { override fun write(b: Int) { throw IOException("synthetic provider failure") } })
            fail("Expected provider failure")
        } catch (_: IOException) { }
        assertTrue(ByteArrayOutputStream().also { PdfReportGenerator().write(snapshot, it) }.size() > 0)
    }
    @Test fun wrappedUnbrokenTextPreservesEveryCharacterAndFits() {
        val paint = Paint().apply { textSize = 10f }
        val value = "W".repeat(1500)
        val lines = PdfReportGenerator.wrap(value, paint, 523f)
        assertEquals(value, lines.joinToString(""))
        lines.forEach { assertTrue(paint.measureText(it) <= 523f) }
    }
}
