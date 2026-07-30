package com.sonim.reader.ui;

import android.os.Handler;
import android.os.Looper;

/** {@link MainThread} backed by a main-looper {@link Handler}. */
public final class AndroidMainThread implements MainThread {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void post(Runnable action) {
        handler.post(action);
    }

    @Override
    public void postDelayed(Runnable action, long delayMillis) {
        handler.postDelayed(action, delayMillis);
    }

    @Override
    public void cancel(Runnable action) {
        handler.removeCallbacks(action);
    }
}
