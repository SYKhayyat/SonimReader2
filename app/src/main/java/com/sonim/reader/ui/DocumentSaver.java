package com.sonim.reader.ui;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Persists the edited document back to its source. An Android-free seam (like
 * {@link ReaderView} and {@link MainThread}) so {@link ReaderController} can
 * save without knowing about {@code Uri}s, files, or {@code ContentResolver}.
 * The app binds an {@link AtomicFileSaver}; tests can bind a fake.
 */
public interface DocumentSaver {

    /** Overwrites the source with {@code lines} encoded as {@code charset}. */
    void save(List<String> lines, Charset charset) throws IOException;
}
