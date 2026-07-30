package com.sonim.reader.core;

/**
 * Heuristic right-to-left detection. Scans the opening lines for Hebrew and
 * Arabic letters rather than sampling a single line, so short front-matter
 * (title, blank lines) doesn't cause a mis-guess. The user can always override
 * with a long-press on 0. Ranges are given as code points to keep the source
 * pure ASCII.
 */
public final class RtlDetector {

    private static final int LINES_TO_SCAN = 40;

    private RtlDetector() {}

    public static boolean isProbablyRtl(LineProvider lines) {
        if (lines == null) return false;
        int scan = Math.min(LINES_TO_SCAN, lines.size());
        for (int i = 0; i < scan; i++) {
            if (containsRtlLetter(lines.getLine(i))) return true;
        }
        return false;
    }

    static boolean containsRtlLetter(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            int c = s.charAt(i);
            if (c >= 0x0590 && c <= 0x05FF) return true; // Hebrew
            if (c >= 0x0600 && c <= 0x06FF) return true; // Arabic
            if (c >= 0x0750 && c <= 0x077F) return true; // Arabic Supplement
            if (c >= 0xFB1D && c <= 0xFDFF) return true; // Hebrew + Arabic Presentation Forms-A
            if (c >= 0xFE70 && c <= 0xFEFF) return true; // Arabic Presentation Forms-B
        }
        return false;
    }
}
