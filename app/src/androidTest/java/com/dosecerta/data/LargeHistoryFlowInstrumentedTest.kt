package com.dosecerta.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.qa.SyntheticQaDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Large observable queries remain consistent across real concurrent deletions. */
@RunWith(AndroidJUnit4::class)
class LargeHistoryFlowInstrumentedTest {
    @Test fun historyFlowsSpanCursorWindowsWithoutMixedSnapshotsOrCrashes() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(SyntheticQaDevice.isDedicated(UiDevice.getInstance(instrumentation)))
        val context = instrumentation.targetContext
        val name = "large-history-${System.nanoTime()}.db"
        val db = Room.databaseBuilder(context, DoseCertaDatabase::class.java, name).build()
        val dao = db.medicationLogDao()
        val records = mutableListOf<MedicationLog>()
        val first = List(2) { CompletableDeferred<Unit>() }
        val final = List(2) { CompletableDeferred<Unit>() }
        val observed = List(2) { mutableListOf<Int>() }
        try {
            db.withTransaction {
                repeat(10_000) { index ->
                    val row = MedicationLog(medicationId = null, scheduleId = null, scheduledTime = index.toLong(),
                        status = MedicationStatus.TAKEN, actualTime = index + 1L, isExtraDose = true,
                        occurrenceId = "large-qa:$index", snapshotName = "Synthetic large history $index",
                        snapshotNotes = "x".repeat(512))
                    records += row.copy(id = dao.insert(row))
                }
            }
            val jobs = listOf(
                launch(Dispatchers.Default) {
                    dao.getAllLogs().collect { rows ->
                        verify(rows, observed[0]); first[0].complete(Unit)
                        if (rows.size == 9500) final[0].complete(Unit)
                    }
                },
                launch(Dispatchers.Default) {
                    dao.getAllLogsWithDetails().collect { details ->
                        assertTrue(details.all { it.medicationName == it.log.snapshotName })
                        val rows = details.map { it.log }
                        verify(rows, observed[1]); first[1].complete(Unit)
                        if (rows.size == 9500) final[1].complete(Unit)
                    }
                }
            )
            try {
                withTimeout(30_000) { first.forEach { it.await() } }
                // Each atomic deletion removes fifty rows while both real
                // observers refill a result far larger than one CursorWindow.
                repeat(10) { batch ->
                    db.withTransaction {
                        records.subList(batch * 50, (batch + 1) * 50).forEach { dao.delete(it) }
                    }
                }
                withTimeout(30_000) { final.forEach { it.await() } }
            } finally { jobs.forEach { it.cancelAndJoin() } }
            val report = JSONObject().put("syntheticOnly", true).put("initialRows", 10_000)
                .put("finalRows", 9500).put("notesCharsPerRow", 512)
                .put("rawFlowSizes", JSONArray(observed[0])).put("detailsFlowSizes", JSONArray(observed[1]))
            File(context.filesDir, "qa-history-flow").apply { mkdirs() }.resolve("result.json").writeText(report.toString(2))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    private fun verify(rows: List<MedicationLog>, observed: MutableList<Int>) {
        assertTrue(rows.size in 9500..10_000 && (10_000 - rows.size) % 50 == 0)
        assertEquals(rows.size, rows.map { it.id }.distinct().size)
        assertTrue(rows.all { it.snapshotNotes?.length == 512 && it.snapshotName == "Synthetic large history ${it.scheduledTime}" })
        assertTrue(rows.zipWithNext().all { (a, b) -> a.scheduledTime > b.scheduledTime })
        assertTrue(observed.isEmpty() || rows.size <= observed.last())
        observed += rows.size
    }
}
