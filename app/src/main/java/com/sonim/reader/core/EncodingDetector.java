package com.sonim.reader.core;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

/**
 * Detects the character encoding of a text sample.
 *
 * <p>Replaces the old byte-range guess, which flagged any byte in
 * {@code 0xE0..0xFA} as Hebrew Windows-1255 &mdash; but {@code 0xE0..0xEF} are
 * exactly the UTF-8 three-byte lead bytes, so every UTF-8 file containing CJK,
 * symbols or emoji was mis-decoded into mojibake.
 *
 * <p>The reliable signal is UTF-8's self-validating structure: real UTF-8 has a
 * rigid multi-byte grammar that arbitrary Windows-1255 / ISO-8859-1 text almost
 * never satisfies by accident. So we:
 * <ol>
 *   <li>honour a BOM if present (UTF-8 / UTF-16 LE / UTF-16 BE);</li>
 *   <li>otherwise strictly validate the sample as UTF-8 (tolerating a truncated
 *       trailing sequence at the sample boundary) and accept UTF-8 if it holds;</li>
 *   <li>otherwise fall back to Windows-1255 when Hebrew-range bytes are present,
 *       else ISO-8859-1 (Latin-1), which round-trips every byte.</li>
 * </ol>
 * The class is pure and Android-free so it is exhaustively unit-testable.
 */
public final class EncodingDetector {

    public static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final Charset WINDOWS_1255 = Charset.forName("windows-1255");
    public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    public static final Charset UTF_16LE = Charset.forName("UTF-16LE");
    public static final Charset UTF_16BE = Charset.forName("UTF-16BE");

    /** The encodings the user can manually cycle through, in order. */
    public static final List<Charset> CYCLE = Arrays.asList(UTF_8, WINDOWS_1255, ISO_8859_1);

    private EncodingDetector() {}

    public static Charset detect(byte[] sample, int length) {
        if (sample == null || length <= 0) return UTF_8;

        Charset bom = detectBom(sample, length);
        if (bom != null) return bom;

        if (isValidUtf8(sample, length)) return UTF_8;

        return containsHebrewRange(sample, length) ? WINDOWS_1255 : ISO_8859_1;
    }

    public static Charset detect(byte[] sample) {
        return detect(sample, sample == null ? 0 : sample.length);
    }

    private static Charset detectBom(byte[] b, int len) {
        if (len >= 3 && u(b[0]) == 0xEF && u(b[1]) == 0xBB && u(b[2]) == 0xBF) return UTF_8;
        if (len >= 2 && u(b[0]) == 0xFF && u(b[1]) == 0xFE) return UTF_16LE;
        if (len >= 2 && u(b[0]) == 0xFE && u(b[1]) == 0xFF) return UTF_16BE;
        return null;
    }

    /**
     * Strict UTF-8 structural validation. A sequence cut off at the very end of
     * the sample (because we only read the first few KB) is treated as valid, so
     * a legitimate UTF-8 file is never rejected on a buffer boundary.
     */
    static boolean isValidUtf8(byte[] b, int len) {
        int i = 0;
        while (i < len) {
            int c = u(b[i]);
            int trailing;
            if (c <= 0x7F) {                    // 0xxxxxxx  ASCII
                trailing = 0;
            } else if (c >= 0xC2 && c <= 0xDF) { // 110xxxxx  2-byte (0xC0/0xC1 are illegal overlongs)
                trailing = 1;
            } else if (c >= 0xE0 && c <= 0xEF) { // 1110xxxx  3-byte
                trailing = 2;
            } else if (c >= 0xF0 && c <= 0xF4) { // 11110xxx  4-byte (max U+10FFFF)
                trailing = 3;
            } else {
                return false;                    // stray continuation or illegal lead
            }
            for (int j = 1; j <= trailing; j++) {
                if (i + j >= len) return true;   // truncated at sample end -> assume ok
                if ((u(b[i + j]) & 0xC0) != 0x80) return false; // must be 10xxxxxx
            }
            i += trailing + 1;
        }
        return true;
    }

    private static boolean containsHebrewRange(byte[] b, int len) {
        for (int i = 0; i < len; i++) {
            int c = u(b[i]);
            if (c >= 0xE0 && c <= 0xFA) return true; // Windows-1255 Hebrew letters
        }
        return false;
    }

    private static int u(byte x) {
        return x & 0xFF;
    }
}
