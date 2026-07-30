package com.sonim.reader.ui;

/**
 * Minimal main-thread scheduler abstraction so {@link ReaderController} can post
 * work back to the UI thread (and drive auto-scroll) without a hard dependency
 * on {@code android.os.Handler}. Backed by {@link AndroidMainThread} in the app.
 */
public interface MainThread {

    void post(Runnable action);

    void postDelayed(Runnable action, long delayMillis);

    void cancel(Runnable action);
}
