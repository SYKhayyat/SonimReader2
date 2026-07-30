package com.sonim.reader.core;

import java.nio.charset.Charset;

/**
 * Mutable, framework-free bundle of display preferences. The adapter reads this
 * live, so appearance changes (font, theme, direction) re-render in place
 * instead of rebuilding the list &mdash; which is what used to throw the reader
 * back to the top of the book.
 */
public final class ReaderSettings {

    public static final float MIN_FONT = 10f;
    public static final float MAX_FONT = 40f;
    public static final float DEFAULT_FONT = 18f;
    private static final float FONT_STEP = 2f;

    private float fontSize = DEFAULT_FONT;
    private boolean nightMode = true;
    private boolean rtl = false;
    private int encodingIndex = 0; // index into EncodingDetector.CYCLE

    public float getFontSize() { return fontSize; }

    public void increaseFont() { fontSize = Math.min(MAX_FONT, fontSize + FONT_STEP); }

    public void decreaseFont() { fontSize = Math.max(MIN_FONT, fontSize - FONT_STEP); }

    public void setFontSize(float size) {
        fontSize = Math.max(MIN_FONT, Math.min(MAX_FONT, size));
    }

    public boolean isNightMode() { return nightMode; }

    public void toggleNightMode() { nightMode = !nightMode; }

    public void setNightMode(boolean night) { this.nightMode = night; }

    public boolean isRtl() { return rtl; }

    public void setRtl(boolean rtl) { this.rtl = rtl; }

    public void toggleRtl() { rtl = !rtl; }

    public int getEncodingIndex() { return encodingIndex; }

    public void setEncodingIndex(int index) {
        int n = EncodingDetector.CYCLE.size();
        this.encodingIndex = ((index % n) + n) % n;
    }

    public void cycleEncoding() { setEncodingIndex(encodingIndex + 1); }

    public Charset getCharset() { return EncodingDetector.CYCLE.get(encodingIndex); }

    public String getEncodingName() { return getCharset().name(); }
}
