package com.sonim.reader.core;

/**
 * Read-only, index-addressable view over the lines of a text document.
 *
 * <p>This is the seam that lets the UI stay ignorant of <em>how</em> text is
 * stored. Today the only implementation is {@link InMemoryLineProvider}, which
 * holds every line in a list. A future {@code PagedLineProvider} can index line
 * byte-offsets and decode windows on demand without touching any caller.
 */
public interface LineProvider {

    /** Total number of lines. Always {@code >= 1} for a successfully loaded document. */
    int size();

    /** The line at {@code index}. Callers must pass {@code 0 <= index < size()}. */
    String getLine(int index);

    /** Clamps {@code percentage} (0-100) to a valid line index. */
    default int lineForPercentage(int percentage) {
        if (size() == 0) return 0;
        float ratio = Math.max(0, Math.min(100, percentage)) / 100f;
        int target = (int) (size() * ratio);
        return Math.max(0, Math.min(target, size() - 1));
    }

    /** The reading progress (0-100) represented by {@code lineIndex}. */
    default int percentageForLine(int lineIndex) {
        if (size() == 0) return 0;
        return (int) (((float) lineIndex / size()) * 100);
    }
}
