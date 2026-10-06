package com.dosecerta.ui.history

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.qa.SyntheticQaDevice
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Binder/IO and SavedStateHandle contract; actual picker recreation is tested by SystemUi. */
@RunWith(AndroidJUnit4::class)
class ReportExportStateInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun uri(kind: String) = DocumentsContract.buildDocumentUri(FaultReportProvider.AUTHORITY,
        "$kind-${java.util.UUID.randomUUID()}")
    private fun await(model: HistoryViewModel, expected: Class<*>) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (!expected.isInstance(model.exportState.value) && SystemClock.elapsedRealtime() < end) SystemClock.sleep(25)
        assertTrue("Expected ${expected.simpleName}, got ${model.exportState.value}", expected.isInstance(model.exportState.value))
    }
    private fun column(uri: Uri, name: String) = context.contentResolver.query(uri, arrayOf(name), null, null, null)!!.use {
        assertTrue(it.moveToFirst()); it.getInt(0)
    }
    private fun fixture(block: (MedicationRepository, ViewModelStore) -> Unit) {
        assumeTrue(SyntheticQaDevice.isDedicated(UiDevice.getInstance(instrumentation)))
        val name = "qa-export-state-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, DoseCertaDatabase::class.java, name).build()
        val store = ViewModelStore()
        try { block(MedicationRepository(database), store) }
        finally { instrumentation.runOnMainSync { store.clear() }; database.close(); context.deleteDatabase(name) }
    }
    private fun model(repo: MedicationRepository, saved: SavedStateHandle, store: ViewModelStore, key: String): HistoryViewModel {
        lateinit var model: HistoryViewModel
        instrumentation.runOnMainSync { model = HistoryViewModel(repo, saved, context); store.put(key, model) }
        return model
    }
    private fun restored(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })

    @Test fun singleExportSurvivesStateRestorationAndKeepsIoOffMainThread() = fixture { repo, store ->
        val saved = SavedStateHandle()
        val first = model(repo, saved, store, "first")
        val destination = uri("slow-success")
        try {
            instrumentation.runOnMainSync {
                assertTrue(first.beginExport("pt-BR", "America/Sao_Paulo"))
                assertFalse(first.beginExport("en", "UTC"))
            }
            val choosing = model(repo, restored(saved), store, "choosing")
            assertTrue(choosing.exportState.value is HistoryViewModel.ExportState.Choosing)
            instrumentation.runOnMainSync { assertFalse(choosing.beginExport("en", "UTC")) }
            instrumentation.runOnMainSync {
                first.destinationSelected(destination, context)
                assertTrue(first.exportState.value is HistoryViewModel.ExportState.Loading)
                assertFalse(first.beginExport("en", "UTC"))
                first.destinationSelected(destination, context)
            }
            val start = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { assertTrue(first.exportState.value is HistoryViewModel.ExportState.Loading) }
            assertTrue("Provider latency must not block the main thread", SystemClock.elapsedRealtime() - start < 2000)
            val interrupted = model(repo, restored(saved), store, "interrupted")
            assertTrue(interrupted.exportState.value is HistoryViewModel.ExportState.Interrupted)
            await(first, HistoryViewModel.ExportState.Success::class.java)
            assertEquals(1, column(destination, "writeOpens"))
            val success = first.exportState.value as HistoryViewModel.ExportState.Success
            assertEquals(destination, success.uri)
            assertEquals(DocumentsContract.getDocumentId(destination) + ".pdf", success.fileName)
            context.contentResolver.openFileDescriptor(destination, "r")!!.use { fd ->
                PdfRenderer(fd).use { assertTrue(it.pageCount > 0) }
            }
            val restoredSuccess = model(repo, restored(saved), store, "success")
            assertEquals(success, restoredSuccess.exportState.value)
            assertEquals(1, column(destination, "writeOpens"))
        } finally { DocumentsContract.deleteDocument(context.contentResolver, destination) }
    }

    @Test fun cancellationAndProviderFailureAllowAnExplicitRetry() = fixture { repo, store ->
        val model = model(repo, SavedStateHandle(), store, "failure")
        val failed = uri("write-fail")
        val retry = uri("success")
        try {
            instrumentation.runOnMainSync {
                assertTrue(model.beginExport("en", "UTC"))
                model.destinationSelected(null, context)
                assertTrue(model.exportState.value is HistoryViewModel.ExportState.Cancelled)
                assertTrue(model.beginExport("en", "UTC"))
                model.destinationSelected(failed, context)
            }
            await(model, HistoryViewModel.ExportState.Error::class.java)
            assertEquals(1, column(failed, "deleted"))
            instrumentation.runOnMainSync {
                assertTrue(model.beginExport("pt-BR", "America/Sao_Paulo"))
                model.destinationSelected(retry, context)
            }
            await(model, HistoryViewModel.ExportState.Success::class.java)
            assertEquals(1, column(retry, "writeOpens"))
        } finally {
            DocumentsContract.deleteDocument(context.contentResolver, failed)
            DocumentsContract.deleteDocument(context.contentResolver, retry)
        }
    }
}
