package com.sonim.reader.core;

import java.util.Locale;

/**
 * Case-insensitive line search over a {@link LineProvider}, with wrap-around.
 *
 * <p>Pure and stateless (the caller owns the "current match" cursor), so it is
 * trivially unit-testable and reusable by any UI.
 */
public final class SearchService {

    /** Sentinel for "no match". */
    public static final int NOT_FOUND = -1;

    private SearchService() {}

    /** First match at or after {@code fromLine}, wrapping to the top. */
    public static int findNext(LineProvider lines, String query, int fromLine) {
        if (isBlank(query) || lines.size() == 0) return NOT_FOUND;
        String needle = query.toLowerCase(Locale.ROOT);
        int start = clamp(fromLine, lines.size());
        for (int i = start; i < lines.size(); i++) {
            if (contains(lines.getLine(i), needle)) return i;
        }
        for (int i = 0; i < start; i++) {
            if (contains(lines.getLine(i), needle)) return i;
        }
        return NOT_FOUND;
    }

    /** First match strictly before {@code fromLine}, wrapping to the bottom. */
    public static int findPrevious(LineProvider lines, String query, int fromLine) {
        if (isBlank(query) || lines.size() == 0) return NOT_FOUND;
        String needle = query.toLowerCase(Locale.ROOT);
        int start = clamp(fromLine, lines.size());
        for (int i = start - 1; i >= 0; i--) {
            if (contains(lines.getLine(i), needle)) return i;
        }
        for (int i = lines.size() - 1; i >= start; i--) {
            if (contains(lines.getLine(i), needle)) return i;
        }
        return NOT_FOUND;
    }

    /** Total number of lines containing {@code query}. */
    public static int count(LineProvider lines, String query) {
        if (isBlank(query)) return 0;
        String needle = query.toLowerCase(Locale.ROOT);
        int n = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (contains(lines.getLine(i), needle)) n++;
        }
        return n;
    }

    private static boolean contains(String line, String lowerNeedle) {
        return line != null && line.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static int clamp(int i, int size) {
        if (i < 0) return 0;
        if (i > size) return size;
        return i;
    }
}
