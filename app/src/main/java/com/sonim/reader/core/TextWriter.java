package com.sonim.reader.core;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Serialises editor text back to bytes &mdash; the write-side mirror of
 * {@link TextLoader}. Pure and Android-free so the whole round trip
 * (load &rarr; edit &rarr; save) is unit-testable without a device.
 *
 * <p>The mapping is deliberately WYSIWYG: the text shown in the editor is
 * exactly what lands on disk. Lines are joined by {@code '\n'} with no forced
 * trailing newline, so what the user sees is what is saved.
 */
public final class TextWriter {

    private TextWriter() {}

    /** Joins {@code lines} with {@code '\n'} into the exact text shown in the editor. */
    public static String serialize(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /**
     * Splits editor text back into lines, tolerant of {@code \n}, {@code \r\n}
     * and lone {@code \r}. A trailing newline yields a final empty line, so
     * {@code split(serialize(x)).equals(x)} for any {@code x}.
     */
    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            out.add("");
            return out;
        }
        int start = 0;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                out.add(text.substring(start, i));
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                start = i + 1;
            }
        }
        out.add(text.substring(start));
        return out;
    }

    /** Writes {@code lines} to {@code target} using {@code charset}. Always off the main thread. */
    public static void writeTo(StreamWriter target, List<String> lines, Charset charset)
            throws IOException {
        try (Writer w = new OutputStreamWriter(
                new BufferedOutputStream(target.open()), charset)) {
            w.write(serialize(lines));
        }
    }
}
