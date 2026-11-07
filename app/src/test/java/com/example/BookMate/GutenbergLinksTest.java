package com.example.BookMate;

import org.junit.Test;
import static org.junit.Assert.*;

public class GutenbergLinksTest {
    @Test public void legacyReaderLinksOpenAvailableFormatsForExactEdition() {
        assertEquals("https://www.gutenberg.org/ebooks/9010",
                GutenbergLinks.catalogUrl("https://www.gutenberg.org/ebooks/9010.html.images"));
        assertEquals("https://www.gutenberg.org/ebooks/2572",
                GutenbergLinks.catalogUrl("https://www.gutenberg.org/ebooks/2572.html.images"));
    }
    @Test public void otherProvidersAndLookalikeHostsAreUnchanged() {
        for (String url : new String[]{"https://archive.org/details/book",
                "https://gutenberg.org.evil.test/ebooks/9010.html.images",
                "https://www.gutenberg.org/cache/epub/2572/pg2572.html"}) {
            assertEquals(url, GutenbergLinks.catalogUrl(url));
        }
        assertNull(GutenbergLinks.catalogUrl(null));
    }
}
