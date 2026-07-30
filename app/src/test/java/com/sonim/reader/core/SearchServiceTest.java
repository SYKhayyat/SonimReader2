package com.sonim.reader.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class SearchServiceTest {

    private LineProvider doc(String... lines) {
        return new InMemoryLineProvider(Arrays.asList(lines));
    }

    @Test
    public void findNextIsCaseInsensitive() {
        LineProvider d = doc("The Quick", "brown FOX", "jumps");
        assertEquals(1, SearchService.findNext(d, "fox", 0));
    }

    @Test
    public void findNextWrapsAround() {
        LineProvider d = doc("alpha", "beta", "gamma");
        assertEquals(0, SearchService.findNext(d, "alpha", 1));
    }

    @Test
    public void findPreviousWrapsAround() {
        LineProvider d = doc("alpha", "beta", "gamma");
        assertEquals(2, SearchService.findPrevious(d, "gamma", 1));
    }

    @Test
    public void missingTermReturnsNotFound() {
        LineProvider d = doc("alpha", "beta");
        assertEquals(SearchService.NOT_FOUND, SearchService.findNext(d, "zzz", 0));
    }

    @Test
    public void blankQueryReturnsNotFound() {
        LineProvider d = doc("alpha");
        assertEquals(SearchService.NOT_FOUND, SearchService.findNext(d, "   ", 0));
    }

    @Test
    public void countCountsMatchingLines() {
        LineProvider d = doc("cat", "dog", "cat and dog", "bird");
        assertEquals(2, SearchService.count(d, "cat"));
    }
}
