package com.example.BookMate;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class QuoteCloudTest {
    @Test public void backupKeepsQuoteIdentityAndAllMetadata() {
        Quote quote = new Quote(7, "A passage — café", "My note", "Book", "Author", 123456L);
        Map<String, Object> data = QuoteCloud.toMap(quote);
        assertEquals(5, data.size());
        assertEquals(quote.getText(), data.get("text"));
        assertEquals(quote.getNote(), data.get("note"));
        assertEquals(quote.getBookTitle(), data.get("bookTitle"));
        assertEquals(quote.getBookAuthor(), data.get("bookAuthor"));
        assertEquals(123456L, data.get("createdAt"));
        assertFalse(data.containsKey("id")); // SQLite IDs belong only to this device.
    }

    @Test public void manualQuotesKeepTheirOptionalNullFields() {
        Map<String, Object> data = QuoteCloud.toMap(new Quote(-1, "Thought", null, null, null, 9L));
        for (String field : new String[]{"note", "bookTitle", "bookAuthor"}) {
            assertTrue(data.containsKey(field));
            assertNull(data.get(field));
        }
        assertEquals(9L, data.get("createdAt"));
    }
}
