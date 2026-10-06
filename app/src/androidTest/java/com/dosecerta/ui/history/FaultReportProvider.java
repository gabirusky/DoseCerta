package com.dosecerta.ui.history;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Separate test-APK process: uses Android/Java only, without dependencies supplied by the target APK. */
public class FaultReportProvider extends ContentProvider {
    public static final String AUTHORITY = "com.dosecerta.test.qa.reports";
    private static final class State {
        volatile int bytes;
        volatile boolean closed;
        volatile boolean deleted;
        final java.util.concurrent.atomic.AtomicInteger writeOpens = new java.util.concurrent.atomic.AtomicInteger();
    }
    private final ConcurrentHashMap<String, State> documents = new ConcurrentHashMap<>();
    @Override public boolean onCreate() { return true; }
    private String id(Uri uri) {
        String value = DocumentsContract.getDocumentId(uri);
        if (!value.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid synthetic document");
        return value;
    }
    private File file(String id) { return new File(getContext().getCacheDir(), "qa-" + id + ".pdf"); }
    private State state(String id) { return documents.computeIfAbsent(id, key -> new State()); }
    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String id = id(uri);
        State state = state(id);
        if (mode.contains("w")) state.writeOpens.incrementAndGet();
        if (mode.contains("w") && id.startsWith("slow-success-")) android.os.SystemClock.sleep(1400);
        if (id.startsWith("open-fail-")) throw new FileNotFoundException("ENOSPC: synthetic provider open failure");
        if (id.startsWith("write-fail-")) {
            try {
                ParcelFileDescriptor[] pair = ParcelFileDescriptor.createReliableSocketPair();
                new Thread(() -> {
                    try {
                        FileInputStream input = new FileInputStream(pair[0].getFileDescriptor());
                        byte[] bytes = new byte[512];
                        int read = input.read(bytes);
                        if (read > 0) {
                            state.bytes = read;
                            try (java.io.FileOutputStream partial = new java.io.FileOutputStream(file(id))) { partial.write(bytes, 0, read); }
                        }
                        pair[0].closeWithError("ENOSPC: synthetic provider failed after a partial write");
                    } catch (IOException error) {
                        android.util.Log.e("QAReportProvider", "Synthetic pipe failure", error);
                    } finally {
                        state.closed = true;
                        try { pair[0].close(); } catch (IOException ignored) { }
                    }
                }, "QA-report-fault").start();
                return pair[1];
            } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        try {
            return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode), new Handler(Looper.getMainLooper()), error -> {
                state.bytes = (int) file(id).length();
                state.closed = true;
            });
        } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String id = id(uri);
        State state = state(id);
        String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, "acceptedBytes", "closed", "deleted"};
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            switch (columns[index]) {
                case OpenableColumns.DISPLAY_NAME: row[index] = id + ".pdf"; break;
                case OpenableColumns.SIZE: row[index] = file(id).length(); break;
                case "acceptedBytes": row[index] = state.bytes; break;
                case "closed": row[index] = state.closed ? 1 : 0; break;
                case "deleted": row[index] = state.deleted ? 1 : 0; break;
                case "writeOpens": row[index] = state.writeOpens.get(); break;
            }
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (method.equals("android:deleteDocument")) {
            Uri uri = extras.getParcelable("uri");
            String id = id(uri);
            state(id).deleted = true;
            file(id).delete();
            return new Bundle();
        }
        return super.call(method, arg, extras);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
}
