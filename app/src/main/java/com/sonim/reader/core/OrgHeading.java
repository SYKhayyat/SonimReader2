package com.sonim.reader.core;

/**
 * One Org title line: which line it is on, how deep it is (number of leading
 * stars), and the title text with the stars removed.
 */
public final class OrgHeading {

    public final int line;
    public final int level;
    public final String title;

    public OrgHeading(int line, int level, String title) {
        this.line = line;
        this.level = level;
        this.title = title;
    }
}
