package com.example.BookMate;

import org.junit.Test;
import static org.junit.Assert.*;

public class BookTextFormatterTest {

    @Test
    public void illustrationAndTranscriberTagsAreStripped() {
        String raw = "Before illustration.\n\n"
                + "[Illustration: Portrait of Alice]\n\n"
                + "[Transcriber's Note: Original typos corrected]\n\n"
                + "After illustration.";

        String formatted = BookTextFormatter.format(raw);
        assertFalse(formatted.contains("[Illustration"));
        assertFalse(formatted.contains("[Transcriber's Note"));
        assertTrue(formatted.contains("Before illustration."));
        assertTrue(formatted.contains("After illustration."));
    }

    @Test
    public void hardLineWrapsAreUnwrappedIntoFluidParagraphs() {
        String raw = "To Sherlock Holmes she is always _the_ woman. I have seldom heard him\n"
                + "mention her under any other name. In his eyes she eclipses and\n"
                + "predominates the whole of her sex.\n\n"
                + "It was not that he felt any emotion\n"
                + "akin to love for Irene Adler.";

        String formatted = BookTextFormatter.format(raw);
        String expectedPara1 = "To Sherlock Holmes she is always _the_ woman. I have seldom heard him mention her under any other name. In his eyes she eclipses and predominates the whole of her sex.";
        String expectedPara2 = "It was not that he felt any emotion akin to love for Irene Adler.";

        assertTrue(formatted.contains(expectedPara1));
        assertTrue(formatted.contains(expectedPara2));
        assertFalse(formatted.contains("him\nmention"));
    }

    @Test
    public void nullAndEmptyInputsReturnSafeValues() {
        assertNull(BookTextFormatter.format(null));
        assertEquals("", BookTextFormatter.format(""));
        assertEquals("", BookTextFormatter.format("   \n\n  "));
    }

    @Test
    public void poetryAndIndentedBlocksPreserveLineBreaks() {
        String poem = "   Twinkle, twinkle, little bat!\n"
                + "   How I wonder what you're at!\n"
                + "   Up above the world you fly,\n"
                + "   Like a tea-tray in the sky.";

        String formatted = BookTextFormatter.format(poem);
        assertTrue(formatted.contains("\n"));
        assertTrue(formatted.contains("Twinkle"));
    }
}
