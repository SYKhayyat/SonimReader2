package com.sonim.reader.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.sonim.reader.core.Bookmarks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@link SharedPreferences}-backed {@link BookmarkRepository}. */
public final class PrefsBookmarkRepository implements BookmarkRepository {

    private static final String PREF_NAME = "ReaderBookmarks";

    private final SharedPreferences prefs;

    public PrefsBookmarkRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String fileKey, int slot) {
        return fileKey + "_slot_" + slot;
    }

    @Override
    public void add(String fileKey, int slot, int line) {
        String k = key(fileKey, slot);
        // Copy the returned set before mutating: the instance from SharedPreferences must not be edited in place.
        Set<String> set = new HashSet<>(prefs.getStringSet(k, new HashSet<>()));
        set.add(String.valueOf(line));
        prefs.edit().putStringSet(k, set).apply();
    }

    @Override
    public List<Integer> get(String fileKey, int slot) {
        Set<String> raw = prefs.getStringSet(key(fileKey, slot), new HashSet<>());
        List<Integer> lines = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                lines.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // skip corrupt entry
            }
        }
        Collections.sort(lines);
        return lines;
    }

    @Override
    public void clear(String fileKey) {
        SharedPreferences.Editor editor = prefs.edit();
        for (int slot = Bookmarks.MIN_SLOT; slot <= Bookmarks.MAX_SLOT; slot++) {
            editor.remove(key(fileKey, slot));
        }
        editor.apply();
    }
}
