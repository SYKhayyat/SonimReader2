package com.sonim.reader.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers which sections are closed and works out which lines are therefore
 * visible.
 *
 * <p>The rule when walking the file top to bottom: a closed title hides every
 * line under it up to where its section ends. Because a closed parent's section
 * covers all of its children, closing a parent automatically hides children
 * too, without having to look at their state.
 */
public final class FoldingModel {

    private final OrgOutline outline;
    private final Set<Integer> closed = new HashSet<>(); // heading indices

    public FoldingModel(OrgOutline outline) {
        this.outline = outline;
    }

    public boolean isClosed(int headingIndex) {
        return closed.contains(headingIndex);
    }

    public void toggle(int headingIndex) {
        if (!closed.remove(headingIndex)) closed.add(headingIndex);
    }

    public void closeAll() {
        closed.clear();
        for (int i = 0; i < outline.count(); i++) closed.add(i);
    }

    public void openAll() {
        closed.clear();
    }

    public boolean anyOpen() {
        return closed.size() < outline.count();
    }

    /**
     * Re-opens every section that contains {@code sourceLine} so it can be
     * shown. Returns true if anything actually changed.
     */
    public boolean reveal(int sourceLine) {
        boolean changed = false;
        for (int i = 0; i < outline.count(); i++) {
            if (!closed.contains(i)) continue;
            int start = outline.headings().get(i).line;
            int end = outline.sectionEnd(i);
            if (sourceLine > start && sourceLine < end) {
                closed.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    /** The source line numbers currently visible, in order. */
    public List<Integer> visibleLines() {
        List<Integer> visible = new ArrayList<>();
        int skipUntil = -1;
        int size = outline.totalLines();
        for (int i = 0; i < size; i++) {
            if (i < skipUntil) continue;
            visible.add(i);
            int headingIndex = outline.headingIndexAtLine(i);
            if (headingIndex >= 0 && closed.contains(headingIndex)) {
                skipUntil = outline.sectionEnd(headingIndex);
            }
        }
        return visible;
    }

    /** Source lines of the titles that are currently closed and on screen. */
    public Set<Integer> closedHeadingLines() {
        Set<Integer> result = new HashSet<>();
        for (int index : closed) {
            result.add(outline.headings().get(index).line);
        }
        return result;
    }

}
