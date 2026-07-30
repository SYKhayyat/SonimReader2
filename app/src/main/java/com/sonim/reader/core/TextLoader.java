package com.sonim.reader.core;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a text document into an {@link InMemoryLineProvider}.
 *
 * <p>Pure and Android-free: it reads whatever {@link StreamOpener} hands it,
 * which is what makes both encoding detection and line loading unit-testable
 * without a device. Always call this off the main thread.
 */
public final class TextLoader {

    private static final int SAMPLE_BYTES = 8192;

    private TextLoader() {}

    /** Reads the leading bytes used for encoding detection. */
    public static byte[] readSample(StreamOpener opener) throws IOException {
        byte[] buffer = new byte[SAMPLE_BYTES];
        int total = 0;
        try (InputStream in = new BufferedInputStream(opener.open())) {
            int read;
            while (total < buffer.length
                    && (read = in.read(buffer, total, buffer.length - total)) != -1) {
                total += read;
            }
        }
        if (total == buffer.length) return buffer;
        byte[] trimmed = new byte[total];
        System.arraycopy(buffer, 0, trimmed, 0, total);
        return trimmed;
    }

    /** Detects the encoding, then loads every line with it. */
    public static LineProvider loadAutoDetect(StreamOpener opener) throws IOException {
        Charset charset = EncodingDetector.detect(readSample(opener));
        return load(opener, charset);
    }

    /** Loads every line using an explicit {@code charset}. */
    public static LineProvider load(StreamOpener opener, Charset charset) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(opener.open(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) lines.add(""); // never expose an empty document
        return new InMemoryLineProvider(lines);
    }
}
