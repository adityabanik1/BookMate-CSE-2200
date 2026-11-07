package com.example.BookMate;

import org.junit.Test;
import static org.junit.Assert.*;

public class BookFormatsTest {
    @Test public void catalogMetadataIsAppliedOnlyToMatchingEdition() {
        Book catalog = new Book("Essay", "Twain", "", "", "https://www.gutenberg.org/ebooks/2572.html.images");
        Book other = new Book("Essay", "Twain", "", "", "https://www.gutenberg.org/ebooks/9010.html.images");
        Book metadata = parse("{\"text/plain\":\"http://www.gutenberg.org/book.txt\"}");
        NetworkUtils.applyGutenbergFormats(java.util.Arrays.asList(catalog, other), java.util.Arrays.asList(metadata));
        assertEquals("Text ebook", catalog.getFormatLabel());
        assertEquals("Formats unverified", other.getFormatLabel());
    }
    private Book parse(String formats) {
        return NetworkUtils.parseGutendexJsonResponse("{\"results\":[{\"id\":2572,\"title\":\"Essay\",\"formats\":"
                + formats + "}]}").get(0);
    }
    @Test public void audioIndexHtmlIsNotMistakenForTextEbook() {
        assertEquals("Audiobook", parse("{\"text/html\":\"https://www.gutenberg.org/index.htm\",\"audio/mpeg\":\"https://www.gutenberg.org/audio.mp3\"}").getFormatLabel());
    }
    @Test public void plainTextAndUnknownAreDistinguished() {
        assertEquals("Text ebook", parse("{\"text/plain; charset=utf-8\":\"https://www.gutenberg.org/book.txt\"}").getFormatLabel());
        assertEquals("Formats unverified", parse("{}").getFormatLabel());
    }
}
