package com.sonim.reader.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class OrgOutlineTest {

    private OrgOutline outline(String... lines) {
        return OrgOutline.parse(new InMemoryLineProvider(Arrays.asList(lines)));
    }

    @Test
    public void headingLevelCountsStarsThenSpace() {
        assertEquals(1, OrgOutline.headingLevel("* Chapter"));
        assertEquals(3, OrgOutline.headingLevel("*** Deep"));
        assertEquals(0, OrgOutline.headingLevel("normal text"));
        assertEquals(0, OrgOutline.headingLevel("*bold*")); // no space after stars
        assertEquals(0, OrgOutline.headingLevel("  * indented")); // must start at column 0
    }

    @Test
    public void findsHeadingsAndTitles() {
        OrgOutline o = outline("* One", "body", "** Two", "* Three");
        assertEquals(3, o.count());
        assertEquals("One", o.headings().get(0).title);
        assertEquals(2, o.headings().get(1).level);
        assertEquals(3, o.headings().get(2).line);
    }

    @Test
    public void sectionEndStopsAtSameOrShallowerLevel() {
        OrgOutline o = outline("* One", "a", "** Two", "b", "* Three");
        // Section of "* One" (index 0) ends at "* Three" (line 4).
        assertEquals(4, o.sectionEnd(0));
        // Section of "** Two" (index 1) ends at "* Three" (line 4) too.
        assertEquals(4, o.sectionEnd(1));
        // Last heading runs to end of file (5 lines).
        assertEquals(5, o.sectionEnd(2));
    }

    @Test
    public void sectionOfFindsEnclosingHeading() {
        OrgOutline o = outline("* One", "a", "** Two", "b");
        assertEquals(0, o.sectionOf(1));   // "a" is under One
        assertEquals(1, o.sectionOf(3));   // "b" is under Two
        assertEquals(-1, o.sectionOf(-1)); // before any heading
    }

    @Test
    public void nextAndPreviousHeadingLines() {
        OrgOutline o = outline("* One", "a", "** Two", "* Three");
        assertEquals(2, o.nextHeadingLine(0));
        assertEquals(3, o.nextHeadingLine(2));
        assertEquals(-1, o.nextHeadingLine(3));
        assertEquals(2, o.previousHeadingLine(3));
        assertEquals(-1, o.previousHeadingLine(0));
    }

    @Test
    public void fileWithNoHeadings() {
        OrgOutline o = outline("just", "plain", "text");
        assertFalse(o.hasHeadings());
        assertTrue(o.count() == 0);
    }
}
