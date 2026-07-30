package com.sonim.reader.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class TextWriterTest {

    @Test
    public void serializeJoinsWithNewlineNoTrailing() {
        assertEquals("a\nb\nc", TextWriter.serialize(Arrays.asList("a", "b", "c")));
    }

    @Test
    public void serializeSingleLine() {
        assertEquals("only", TextWriter.serialize(Arrays.asList("only")));
    }

    @Test
    public void splitTolueratesCrlfAndLoneCr() {
        assertEquals(Arrays.asList("a", "b"), TextWriter.split("a\r\nb"));
        assertEquals(Arrays.asList("a", "b"), TextWriter.split("a\rb"));
    }

    @Test
    public void splitKeepsTrailingBlankLine() {
        assertEquals(Arrays.asList("a", "b", ""), TextWriter.split("a\nb\n"));
    }

    @Test
    public void serializeSplitRoundTrip() {
        List<String> lines = Arrays.asList("first", "", "third");
        assertEquals(lines, TextWriter.split(TextWriter.serialize(lines)));
    }

    @Test
    public void writeToProducesCharsetBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TextWriter.writeTo(() -> out, Arrays.asList("héllo", "x"), StandardCharsets.UTF_8);
        assertEquals("héllo\nx", new String(out.toByteArray(), StandardCharsets.UTF_8));
    }
}
