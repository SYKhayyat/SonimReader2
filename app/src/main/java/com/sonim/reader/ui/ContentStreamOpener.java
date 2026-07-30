package com.sonim.reader.ui;

import android.content.Context;
import android.net.Uri;

import com.sonim.reader.core.StreamOpener;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * {@link StreamOpener} backed by Android's {@code ContentResolver}. Works for
 * both {@code file://} URIs (from the built-in picker) and {@code content://}
 * URIs (from the Storage Access Framework), so the loading core never has to
 * know which one it got.
 */
public final class ContentStreamOpener implements StreamOpener {

    private final Context context;
    private final Uri uri;

    public ContentStreamOpener(Context context, Uri uri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
    }

    @Override
    public InputStream open() throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new FileNotFoundException("Could not open stream for " + uri);
        }
        return in;
    }
}
