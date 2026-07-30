package com.sonim.reader.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Supplies a fresh {@link OutputStream} to some byte sink &mdash; the write-side
 * mirror of {@link StreamOpener}.
 *
 * <p>Decouples {@link TextWriter} from Android's {@code ContentResolver} and the
 * filesystem: production code passes a file- or resolver-backed sink, unit tests
 * pass a {@code ByteArrayOutputStream}-backed one. Each call must return a new
 * stream positioned at the start (truncating any existing content).
 */
public interface StreamWriter {
    OutputStream open() throws IOException;
}
