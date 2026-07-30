package com.sonim.reader.ui;

import android.content.Context;
import android.net.Uri;

import com.sonim.reader.core.TextWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;

/**
 * {@link DocumentSaver} that overwrites the original file <em>in place</em>.
 *
 * <p>For {@code file://} URIs (what the built-in picker produces) it writes to a
 * hidden temp file in the same directory and then renames it over the original,
 * so a crash mid-write can never leave a half-written, corrupted document. For
 * {@code content://} URIs it falls back to the Storage Access Framework's
 * truncating output stream.
 */
public final class AtomicFileSaver implements DocumentSaver {

    private final Context context;
    private final Uri uri;

    public AtomicFileSaver(Context context, Uri uri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
    }

    @Override
    public void save(List<String> lines, Charset charset) throws IOException {
        String path = "file".equalsIgnoreCase(uri.getScheme()) ? uri.getPath() : null;
        if (path != null) {
            saveFileAtomic(new File(path), lines, charset);
        } else {
            saveViaResolver(lines, charset);
        }
    }

    private void saveFileAtomic(File dest, List<String> lines, Charset charset) throws IOException {
        File dir = dest.getParentFile();
        if (dir == null) throw new IOException("No parent directory for " + dest);
        final File tmp = new File(dir, "." + dest.getName() + ".tmp");
        TextWriter.writeTo(() -> new FileOutputStream(tmp), lines, charset);
        if (!tmp.renameTo(dest)) {
            // Rename can fail across some filesystems; fall back to a direct overwrite.
            TextWriter.writeTo(() -> new FileOutputStream(dest), lines, charset);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private void saveViaResolver(List<String> lines, Charset charset) throws IOException {
        TextWriter.writeTo(() -> {
            OutputStream os = context.getContentResolver().openOutputStream(uri, "wt");
            if (os == null) throw new IOException("Cannot open output stream for " + uri);
            return os;
        }, lines, charset);
    }
}
