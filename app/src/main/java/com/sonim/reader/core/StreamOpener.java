package com.sonim.reader.core;

import java.io.IOException;
import java.io.InputStream;

/**
 * Supplies a fresh {@link InputStream} over some byte source.
 *
 * <p>Decouples {@link TextLoader} and {@link EncodingDetector} from Android's
 * {@code ContentResolver}: production code passes a resolver-backed opener,
 * unit tests pass a {@code byte[]}-backed one. Each call must return a new,
 * independent stream positioned at the start.
 */
public interface StreamOpener {
    InputStream open() throws IOException;
}
