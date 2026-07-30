package com.sonim.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EncodingDetectorTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return b;
    }

    @Test
    public void utf8BomIsDetected() {
        assertEquals(EncodingDetector.UTF_8, EncodingDetector.detect(bytes(0xEF, 0xBB, 0xBF, 'h')));
    }

    @Test
    public void utf16BomsAreDetected() {
        assertEquals(EncodingDetector.UTF_16LE, EncodingDetector.detect(bytes(0xFF, 0xFE, 'h', 0)));
        assertEquals(EncodingDetector.UTF_16BE, EncodingDetector.detect(bytes(0xFE, 0xFF, 0, 'h')));
    }

    @Test
    public void plainAsciiIsUtf8() {
        assertEquals(EncodingDetector.UTF_8, EncodingDetector.detect("hello".getBytes()));
    }

    @Test
    public void hebrewInUtf8IsUtf8() {
        // Aleph U+05D0 in UTF-8 = D7 90 -- valid multi-byte sequence.
        assertEquals(EncodingDetector.UTF_8, EncodingDetector.detect(bytes(0xD7, 0x90, 0xD7, 0x91)));
    }

    @Test
    public void hebrewInWindows1255IsDetected() {
        // Aleph in windows-1255 = single byte E0, which is NOT valid UTF-8 here.
        assertEquals(EncodingDetector.WINDOWS_1255, EncodingDetector.detect(bytes(0xE0, 0x20, 0xE1)));
    }

    @Test
    public void nonHebrewHighBytesFallBackToLatin1() {
        // 0xA9 (copyright) alone is invalid UTF-8 and outside the Hebrew range.
        assertEquals(EncodingDetector.ISO_8859_1, EncodingDetector.detect(bytes('a', 0xA9, 'b')));
    }

    @Test
    public void cjkEmojiUtf8IsNotMisreadAsHebrew() {
        // This is the exact case the old byte-range guess got wrong:
        // U+6587 in UTF-8 = E6 96 87 (lead byte in 0xE0..0xEF).
        assertEquals(EncodingDetector.UTF_8, EncodingDetector.detect(bytes(0xE6, 0x96, 0x87)));
    }

    @Test
    public void truncatedTrailingSequenceIsStillValidUtf8() {
        // Two-byte lead with no continuation because the sample ended.
        assertTrue(EncodingDetector.isValidUtf8(bytes('a', 0xD7), 2));
    }

    @Test
    public void strayContinuationByteIsInvalidUtf8() {
        assertFalse(EncodingDetector.isValidUtf8(bytes(0x80), 1));
    }
}
