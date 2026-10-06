package com.dosecerta.ui.history

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.domain.AdherenceSummary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real ContentResolver/Binder/file descriptor failures from a provider shipped only in the test APK. */
@RunWith(AndroidJUnit4::class)
class ReportProviderInstrumentedTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private fun uri(prefix: String) = DocumentsContract.buildDocumentUri(FaultReportProvider.AUTHORITY, "$prefix-${System.nanoTime()}")
    private fun snapshot(): ReportSnapshot {
        val now = System.currentTimeMillis()
        val rows = (1..1000).map { id -> ReportRow(id.toLong(), "provider:$id", 1L, "Synthetic report medication $id", "1", "mg", "TABLET", "DAILY",
            "Synthetic provider test; no patient data.", now, now + 1, MedicationStatus.TAKEN, false, "SYNTHETIC") }
        return ReportSnapshot(ReportRequest(now - 1000, now + 1000, "America/Sao_Paulo", "pt-BR", null, now),
            rows, rows, AdherenceSummary(rows.size, 0, 0, 0), now, "test")
    }
    private fun state(uri: Uri, key: String): Int = resolver.query(uri, arrayOf(key), null, null, null)!!.use {
        assertTrue(it.moveToFirst()); it.getInt(0)
    }
    private fun assertWriteFails(uri: Uri) {
        val result = runCatching { ReportDocuments.write(resolver, uri, snapshot()) }
        assertTrue("A failed provider must never return export success", result.isFailure)
        assertEquals("Cleanup must reach the same document provider", 1, state(uri, "deleted"))
        assertEquals("Partial document must be removed", 0, state(uri, android.provider.OpenableColumns.SIZE))
    }
    @Test fun unavailableStorageDeletesTheCreatedDocument() = assertWriteFails(uri("open-fail"))
    @Test fun partialWriteFailsClosesAndCleansUpThenRetryWorks() {
        val failed = uri("write-fail")
        assertWriteFails(failed)
        assertTrue("The provider accepted a real partial write", state(failed, "acceptedBytes") > 0)
        assertEquals(1, state(failed, "closed"))
        val retry = uri("retry")
        try {
            val result = ReportDocuments.write(resolver, retry, snapshot())
            assertEquals(1000, result.rowCount)
            resolver.openFileDescriptor(retry, "r")!!.use { fd -> PdfRenderer(fd).use { pdf -> assertEquals(result.pageCount, pdf.pageCount) } }
        } finally { DocumentsContract.deleteDocument(resolver, retry) }
    }
}
