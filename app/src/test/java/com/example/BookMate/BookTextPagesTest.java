package com.example.BookMate;

import org.junit.Test;
import static org.junit.Assert.*;

public class BookTextPagesTest {
    @Test
    public void longBookKeepsEveryCharacterIncludingMiddleAndEnding() {
        StringBuilder original = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            original.append("Paragraph ").append(i).append(": a complete book passage.\n\n");
        }
        BookTextPages pages = new BookTextPages(original.toString());
        StringBuilder reconstructed = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            assertTrue(pages.content(i).length() <= 12000);
            reconstructed.append(pages.content(i));
        }
        assertTrue(pages.size() > 1);
        assertEquals(original.toString(), reconstructed.toString());
    }

    @Test
    public void offsetsAtBoundariesAndEndSelectCorrectSection() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40000; i++) text.append('x');
        BookTextPages pages = new BookTextPages(text.toString());
        for (int i = 0; i < pages.size(); i++) {
            assertEquals(i, pages.pageForOffset(pages.start(i)));
            assertEquals(i, pages.pageForOffset(pages.end(i) - 1));
        }
        assertEquals(pages.size() - 1, pages.pageForOffset(text.length()));
    }

    @Test
    public void sectionBoundaryDoesNotSplitEmoji() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 11999; i++) text.append('x');
        text.append("\uD83D\uDE00the end");
        BookTextPages pages = new BookTextPages(text.toString());
        assertEquals(11999, pages.end(0));
        assertTrue(pages.content(1).startsWith("\uD83D\uDE00"));
        assertEquals(text.toString(), pages.content(0) + pages.content(1));
    }

    @Test
    public void emptyAndShortTextsHaveOneSection() {
        assertEquals("", new BookTextPages("").content(0));
        BookTextPages pages = new BookTextPages("A short passage\n\nSecond paragraph");
        assertEquals(1, pages.size());
        assertEquals("A short passage\n\nSecond paragraph", pages.content(0));
    }

    @Test
    public void chapterExtractionFindsDistinctChaptersAndFiltersToc() {
        String book = "Table of Contents\n\n"
                + "CHAPTER I. Down the Hole\n"
                + "CHAPTER II. The Pool\n"
                + "CHAPTER III. A Race\n\n"
                + "CHAPTER I.\n"
                + "Down the Rabbit-Hole\n\n"
                + "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book.\n\n"
                + "CHAPTER II.\n"
                + "The Pool of Tears\n\n"
                + "Curiouser and curiouser cried Alice!\n\n";

        BookTextPages pages = new BookTextPages(book);
        java.util.List<BookTextPages.Chapter> chs = pages.getChapters();
        assertTrue(chs.size() >= 2);
        assertTrue(chs.get(0).getTitle().contains("CHAPTER I"));
        assertTrue(chs.get(0).getTitle().contains("Down the Rabbit-Hole"));
        assertEquals("CHAPTER I — Down the Rabbit-Hole", pages.getChapterTitleForPage(0));
    }
}
