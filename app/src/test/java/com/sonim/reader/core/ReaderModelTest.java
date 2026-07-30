package com.sonim.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ReaderModelTest {

    private LineProvider doc(int n) {
        String[] lines = new String[n];
        for (int i = 0; i < n; i++) lines[i] = "line " + i;
        return new InMemoryLineProvider(Arrays.asList(lines));
    }

    @Test
    public void percentageMapsToLineAndBack() {
        LineProvider d = doc(100);
        assertEquals(50, d.lineForPercentage(50));
        assertEquals(50, d.percentageForLine(50));
    }

    @Test
    public void percentageIsClamped() {
        LineProvider d = doc(10);
        assertEquals(0, d.lineForPercentage(-20));
        assertEquals(9, d.lineForPercentage(200));
    }

    @Test
    public void fontSizeIsClamped() {
        ReaderSettings s = new ReaderSettings();
        for (int i = 0; i < 100; i++) s.increaseFont();
        assertEquals(ReaderSettings.MAX_FONT, s.getFontSize(), 0.001f);
        for (int i = 0; i < 100; i++) s.decreaseFont();
        assertEquals(ReaderSettings.MIN_FONT, s.getFontSize(), 0.001f);
    }

    @Test
    public void encodingCyclesAndWraps() {
        ReaderSettings s = new ReaderSettings();
        int size = EncodingDetector.CYCLE.size();
        int start = s.getEncodingIndex();
        for (int i = 0; i < size; i++) s.cycleEncoding();
        assertEquals(start, s.getEncodingIndex());
    }

    @Test
    public void rtlTogglesAndPersistsCharsetSelection() {
        ReaderSettings s = new ReaderSettings();
        assertFalse(s.isRtl());
        s.toggleRtl();
        assertTrue(s.isRtl());
    }
}
