package com.sonim.reader.core;

/**
 * Translates between two ways of numbering the same document:
 * <ul>
 *   <li><b>source line</b> &mdash; the real line number in the file. Stable; used
 *       for bookmarks, saved position and search so they survive folding.</li>
 *   <li><b>display line</b> &mdash; the position in what is currently shown on
 *       screen, which shrinks when sections are closed.</li>
 * </ul>
 * For plain text the two are identical, so {@link #IDENTITY} is used.
 */
public interface IndexMap {

    int toSource(int display);

    int toDisplay(int source);

    IndexMap IDENTITY = new IndexMap() {
        @Override public int toSource(int display) { return display; }
        @Override public int toDisplay(int source) { return source; }
    };
}
