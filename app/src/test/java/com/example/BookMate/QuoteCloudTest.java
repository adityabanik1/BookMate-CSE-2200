package com.example.BookMate;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class QuoteCloudTest {
    @Test public void stableIdentityMatchesLocalDeduplicationAcrossDevices() {
        Quote first = new Quote(1, "Same passage", null, "Book", "Author", 10L);
        Quote otherDevice = new Quote(99, "Same passage", "Note", "Book", "Author", 10L);
        assertEquals(QuoteCloud.documentId(first), QuoteCloud.documentId(otherDevice));
        assertNotEquals(QuoteCloud.documentId(first), QuoteCloud.documentId(
                new Quote(1, "Same passage", null, null, null, 11L)));
    }

    @Test public void deletionOverridesLegacyCopiesRegardlessOfDocumentOrder() {
        Quote quote = new Quote(1, "Passage", null, null, null, 10L);
        Map<String, Map<String, Object>> docs = new java.util.LinkedHashMap<>();
        Map<String, Object> deleted = QuoteCloud.toMap(quote);
        deleted.put("deleted", true);
        docs.put(QuoteCloud.documentId(quote), deleted);
        docs.put("legacy-random-id", QuoteCloud.toMap(quote));
        assertTrue(QuoteCloud.restoredQuotes(docs).isEmpty());
    }

    @Test public void mergingNewRecordsKeepsExistingAccountQuotesAndDeduplicatesLegacy() {
        Quote first = new Quote(1, "Existing", null, null, null, 10L);
        Quote second = new Quote(2, "New", null, null, null, 20L);
        Map<String, Map<String, Object>> docs = new java.util.LinkedHashMap<>();
        docs.put("legacy", QuoteCloud.toMap(first));
        docs.put(QuoteCloud.documentId(first), QuoteCloud.toMap(first));
        docs.put(QuoteCloud.documentId(second), QuoteCloud.toMap(second));
        assertEquals(2, QuoteCloud.restoredQuotes(docs).size());
        docs.put("broken", new java.util.HashMap<>());
        assertEquals(2, QuoteCloud.restoredQuotes(docs).size());
    }

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
