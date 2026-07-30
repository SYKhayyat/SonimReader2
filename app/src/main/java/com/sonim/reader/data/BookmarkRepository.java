package com.sonim.reader.data;

import java.util.List;

/**
 * Persistence boundary for per-file bookmark slots. Depending on this interface
 * (not {@code SharedPreferences} directly) lets the controller be tested with a
 * fake and lets storage be swapped later (e.g. a file/db).
 */
public interface BookmarkRepository {

    /** Appends {@code line} to {@code slot} (1-9) for {@code fileKey}. */
    void add(String fileKey, int slot, int line);

    /** All lines saved in {@code slot}, ascending. Never null. */
    List<Integer> get(String fileKey, int slot);

    /** Removes every slot for {@code fileKey}. */
    void clear(String fileKey);
}
