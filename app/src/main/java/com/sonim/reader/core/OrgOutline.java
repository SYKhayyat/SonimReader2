package com.sonim.reader.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The list of titles in an Org file and how they nest.
 *
 * <p>A title is any line that starts with one or more stars followed by a
 * space, e.g. {@code * Chapter} or {@code ** Section}. The number of stars is
 * the depth. Everything after a title, up to the next title of the same depth
 * or shallower, is that title's section.
 *
 * <p>Pure Java so it can be unit-tested without a device.
 */
public final class OrgOutline {

    private final List<OrgHeading> headings = new ArrayList<>();
    private final Map<Integer, Integer> lineToIndex = new HashMap<>();
    private final int totalLines;

    private OrgOutline(int totalLines) {
        this.totalLines = totalLines;
    }

    public static OrgOutline parse(LineProvider lines) {
        OrgOutline outline = new OrgOutline(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.getLine(i);
            int level = headingLevel(line);
            if (level > 0) {
                outline.lineToIndex.put(i, outline.headings.size());
                outline.headings.add(new OrgHeading(i, level, titleText(line, level)));
            }
        }
        return outline;
    }

    /** Number of leading stars if {@code line} is a title, else 0. */
    static int headingLevel(String line) {
        if (line == null || line.isEmpty() || line.charAt(0) != '*') return 0;
        int stars = 0;
        while (stars < line.length() && line.charAt(stars) == '*') stars++;
        // A real title needs a space after the stars.
        if (stars < line.length() && line.charAt(stars) == ' ') return stars;
        return 0;
    }

    private static String titleText(String line, int level) {
        return line.substring(level).trim();
    }

    public int count() {
        return headings.size();
    }

    /** Total number of lines in the whole document. */
    public int totalLines() {
        return totalLines;
    }

    public boolean hasHeadings() {
        return !headings.isEmpty();
    }

    public List<OrgHeading> headings() {
        return headings;
    }

    public boolean isHeadingLine(int line) {
        return lineToIndex.containsKey(line);
    }

    /** Heading index of the title exactly on {@code line}, or -1. */
    public int headingIndexAtLine(int line) {
        Integer idx = lineToIndex.get(line);
        return idx == null ? -1 : idx;
    }

    /** The section {@code line} belongs to: the last title at or above it, or -1. */
    public int sectionOf(int line) {
        int result = -1;
        for (int i = 0; i < headings.size(); i++) {
            if (headings.get(i).line <= line) result = i;
            else break;
        }
        return result;
    }

    /** First line NOT part of heading {@code index}'s section (exclusive end). */
    public int sectionEnd(int index) {
        int level = headings.get(index).level;
        for (int i = index + 1; i < headings.size(); i++) {
            if (headings.get(i).level <= level) return headings.get(i).line;
        }
        return totalLines;
    }

    /** First title strictly after {@code line}, or -1. */
    public int nextHeadingLine(int line) {
        for (OrgHeading h : headings) {
            if (h.line > line) return h.line;
        }
        return -1;
    }

    /** Last title strictly before {@code line}, or -1. */
    public int previousHeadingLine(int line) {
        int result = -1;
        for (OrgHeading h : headings) {
            if (h.line < line) result = h.line;
            else break;
        }
        return result;
    }
}
