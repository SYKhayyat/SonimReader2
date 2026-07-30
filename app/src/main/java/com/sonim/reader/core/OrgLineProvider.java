package com.sonim.reader.core;

import java.util.List;
import java.util.Set;

/**
 * Shows an Org document with closed sections hidden. It wraps the full document
 * and a list of the currently visible source-line numbers, so the reader can
 * keep rendering "a list of lines" without knowing anything about folding.
 *
 * <p>It also translates between screen position and real file line
 * ({@link IndexMap}) so bookmarks, saved position and search stay correct when
 * sections open and close.
 */
public final class OrgLineProvider implements LineProvider, IndexMap {

    private static final String CLOSED_MARK = "  ...";

    private final LineProvider full;
    private final List<Integer> visible;      // display index -> source line
    private final Set<Integer> closedHeadings; // source lines that are closed titles

    public OrgLineProvider(LineProvider full, List<Integer> visible, Set<Integer> closedHeadings) {
        this.full = full;
        this.visible = visible;
        this.closedHeadings = closedHeadings;
    }

    @Override
    public int size() {
        return visible.size();
    }

    @Override
    public String getLine(int display) {
        int source = visible.get(display);
        String text = full.getLine(source);
        return closedHeadings.contains(source) ? text + CLOSED_MARK : text;
    }

    @Override
    public int toSource(int display) {
        if (visible.isEmpty()) return 0;
        int clamped = Math.max(0, Math.min(display, visible.size() - 1));
        return visible.get(clamped);
    }

    /** Nearest visible screen position at or above {@code source}. */
    @Override
    public int toDisplay(int source) {
        int best = 0;
        for (int d = 0; d < visible.size(); d++) {
            int line = visible.get(d);
            if (line == source) return d;
            if (line < source) best = d;
            else break;
        }
        return best;
    }
}
