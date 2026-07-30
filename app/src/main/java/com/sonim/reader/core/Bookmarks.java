package com.sonim.reader.core;

import java.util.List;

/**
 * Pure bookmark-cursor logic, split out from storage so it can be tested
 * without Android {@code SharedPreferences}.
 */
public final class Bookmarks {

    public static final int NONE = -1;

    /** Lowest slot number (mapped to key {@code 1}). */
    public static final int MIN_SLOT = 1;
    /** Highest slot number (mapped to key {@code 9}). */
    public static final int MAX_SLOT = 9;

    private Bookmarks() {}

    /**
     * The next bookmark strictly after {@code currentLine}, wrapping to the
     * first when past the end. Returns {@link #NONE} for an empty slot.
     * {@code sortedLines} must be ascending.
     */
    public static int nextAfter(List<Integer> sortedLines, int currentLine) {
        if (sortedLines == null || sortedLines.isEmpty()) return NONE;
        for (int line : sortedLines) {
            if (line > currentLine) return line;
        }
        return sortedLines.get(0);
    }

    public static boolean isValidSlot(int slot) {
        return slot >= MIN_SLOT && slot <= MAX_SLOT;
    }
}
