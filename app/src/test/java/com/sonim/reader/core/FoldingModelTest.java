package com.sonim.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class FoldingModelTest {

    // Lines: 0:* One  1:a  2:b  3:** Two  4:c  5:* Three  6:d
    private OrgOutline sample() {
        return OrgOutline.parse(new InMemoryLineProvider(
                Arrays.asList("* One", "a", "b", "** Two", "c", "* Three", "d")));
    }

    @Test
    public void everythingVisibleWhenNothingClosed() {
        FoldingModel f = new FoldingModel(sample());
        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6), f.visibleLines());
    }

    @Test
    public void closingTopSectionHidesItsBodyAndChildren() {
        OrgOutline o = sample();
        FoldingModel f = new FoldingModel(o);
        f.toggle(0); // close "* One" (index 0)
        // The One title stays; lines 1..4 (body + the whole Two subsection) hide.
        assertEquals(Arrays.asList(0, 5, 6), f.visibleLines());
        assertTrue(f.closedHeadingLines().contains(0));
    }

    @Test
    public void closingChildHidesOnlyItsOwnBody() {
        OrgOutline o = sample();
        FoldingModel f = new FoldingModel(o);
        f.toggle(1); // close "** Two" (index 1)
        assertEquals(Arrays.asList(0, 1, 2, 3, 5, 6), f.visibleLines());
    }

    @Test
    public void revealReopensEnclosingClosedSection() {
        OrgOutline o = sample();
        FoldingModel f = new FoldingModel(o);
        f.toggle(0); // close One, hiding line 4
        assertFalse(f.visibleLines().contains(4));
        boolean changed = f.reveal(4); // ask to show a hidden line
        assertTrue(changed);
        assertTrue(f.visibleLines().contains(4));
    }

    @Test
    public void closeAllThenOpenAll() {
        OrgOutline o = sample();
        FoldingModel f = new FoldingModel(o);
        f.closeAll();
        List<Integer> visible = f.visibleLines();
        // A closed top section also hides its subsection's title, so closing
        // everything collapses down to just the top-level titles (0 and 5).
        assertEquals(Arrays.asList(0, 5), visible);
        assertFalse(f.anyOpen());
        f.openAll();
        assertTrue(f.anyOpen());
        assertEquals(7, f.visibleLines().size());
    }
}
