package com.sonim.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class BookmarksTest {

    @Test
    public void nextAfterReturnsFirstGreater() {
        assertEquals(50, Bookmarks.nextAfter(Arrays.asList(10, 50, 90), 30));
    }

    @Test
    public void nextAfterWrapsToFirst() {
        assertEquals(10, Bookmarks.nextAfter(Arrays.asList(10, 50, 90), 90));
    }

    @Test
    public void emptySlotReturnsNone() {
        assertEquals(Bookmarks.NONE, Bookmarks.nextAfter(Collections.emptyList(), 0));
    }

    @Test
    public void slotValidation() {
        assertTrue(Bookmarks.isValidSlot(1));
        assertTrue(Bookmarks.isValidSlot(9));
        assertFalse(Bookmarks.isValidSlot(0));
        assertFalse(Bookmarks.isValidSlot(10));
    }
}
