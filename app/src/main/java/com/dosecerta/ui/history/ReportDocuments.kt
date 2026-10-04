package com.dosecerta.ui.history

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

object ReportDocuments {
    fun destinationLabel(context: android.content.Context, uri: Uri): String = runCatching {
        uri.authority?.let { context.packageManager.resolveContentProvider(it, 0) }
            ?.loadLabel(context.packageManager)?.toString()?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: context.getString(com.dosecerta.R.string.report_selected_destination)

    fun write(resolver: ContentResolver, uri: Uri, snapshot: ReportSnapshot): PdfReportGenerator.Result {
        require(uri.scheme == "content") { "A document provider destination is required" }
        try {
            return resolver.openOutputStream(uri, "wt")?.use { PdfReportGenerator().write(snapshot, it) }
                ?: error("The provider did not open the document")
        } catch (error: Exception) {
            // Best effort deletion of a partial document. Some providers deny this operation.
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw error
        }
    }
    fun open(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("Dose Certa report", uri)
    }
    fun share(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("Dose Certa report", uri)
    }
}
