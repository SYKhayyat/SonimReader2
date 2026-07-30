package com.sonim.reader.data;

/**
 * Persists lightweight per-file reading state: last line position, and the
 * remembered encoding / direction overrides so reopening a book restores
 * exactly how you left it.
 */
public interface ReadingStateStore {

    int getPosition(String fileKey, int fallback);

    void savePosition(String fileKey, int line);

    /** Remembered encoding index, or {@code fallback} if the user never set one. */
    int getEncodingIndex(String fileKey, int fallback);

    void saveEncodingIndex(String fileKey, int index);

    boolean getRtl(String fileKey, boolean fallback);

    void saveRtl(String fileKey, boolean rtl);

    /** Most-recently opened file URIs, newest first. */
    java.util.List<String> getRecentFiles();

    void addRecentFile(String uri);
}
