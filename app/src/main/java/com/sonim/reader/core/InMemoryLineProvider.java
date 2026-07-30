package com.sonim.reader.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simplest {@link LineProvider}: every line materialised in a list.
 *
 * <p>Adequate for the XP5s (2&nbsp;GB RAM) and any human-sized text file. The
 * important property is that it is created off the main thread by
 * {@link TextLoader}; the old {@code FileBuffer} loaded on the UI thread and
 * (despite its name) also held the whole file, so this is no worse on memory
 * and strictly better on responsiveness. Swap for a paged provider if very
 * large files ever become a requirement.
 */
public final class InMemoryLineProvider implements LineProvider {

    private final List<String> lines;

    public InMemoryLineProvider(List<String> lines) {
        this.lines = new ArrayList<>(lines);
    }

    @Override
    public int size() {
        return lines.size();
    }

    @Override
    public String getLine(int index) {
        return lines.get(index);
    }

    /** Unmodifiable view, for adapters that want to bind directly to the backing list. */
    public List<String> asList() {
        return Collections.unmodifiableList(lines);
    }
}
