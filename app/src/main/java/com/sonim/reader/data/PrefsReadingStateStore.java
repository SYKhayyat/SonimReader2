package com.sonim.reader.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** {@link SharedPreferences}-backed {@link ReadingStateStore}. */
public final class PrefsReadingStateStore implements ReadingStateStore {

    private static final String PREF_NAME = "ReaderPrefs";
    private static final String KEY_RECENTS = "recent_files";
    private static final String RECENTS_SEP = "\n";
    private static final int MAX_RECENTS = 15;

    private final SharedPreferences prefs;

    public PrefsReadingStateStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public int getPosition(String fileKey, int fallback) {
        return prefs.getInt(fileKey + "_pos", fallback);
    }

    @Override
    public void savePosition(String fileKey, int line) {
        prefs.edit().putInt(fileKey + "_pos", line).apply();
    }

    @Override
    public int getEncodingIndex(String fileKey, int fallback) {
        return prefs.getInt(fileKey + "_enc", fallback);
    }

    @Override
    public void saveEncodingIndex(String fileKey, int index) {
        prefs.edit().putInt(fileKey + "_enc", index).apply();
    }

    @Override
    public boolean getRtl(String fileKey, boolean fallback) {
        return prefs.getBoolean(fileKey + "_rtl", fallback);
    }

    @Override
    public void saveRtl(String fileKey, boolean rtl) {
        prefs.edit().putBoolean(fileKey + "_rtl", rtl).apply();
    }

    @Override
    public List<String> getRecentFiles() {
        String raw = prefs.getString(KEY_RECENTS, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(RECENTS_SEP)));
    }

    @Override
    public void addRecentFile(String uri) {
        // LinkedHashSet keeps insertion order and de-dupes; newest goes first.
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(uri);
        ordered.addAll(getRecentFiles());
        List<String> trimmed = new ArrayList<>(ordered);
        if (trimmed.size() > MAX_RECENTS) {
            trimmed = trimmed.subList(0, MAX_RECENTS);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.size(); i++) {
            if (i > 0) sb.append(RECENTS_SEP);
            sb.append(trimmed.get(i));
        }
        prefs.edit().putString(KEY_RECENTS, sb.toString()).apply();
    }
}
