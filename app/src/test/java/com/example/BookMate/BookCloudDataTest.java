package com.example.BookMate;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class BookCloudDataTest {
    @Test public void stableIdentityIgnoresDeviceIdsAndProgress() {
        Book first = new Book(1, "A/B", "Author", "", "", "WANT_TO_READ", 2, "url");
        Book second = new Book(99, "A/B", "Author", "", "", "FINISHED", 100, "url");
        assertEquals(BookCloudData.documentId(first), BookCloudData.documentId(second));
        assertFalse(BookCloudData.documentId(first).contains("/"));
    }

    @Test public void canonicalProgressOverridesLegacyBackupAndDeletionPreventsResurrection() {
        Book book = new Book(1, "Title", "Author", "", "", "WANT_TO_READ", 12, "url");
        Map<String, Map<String, Object>> documents = new java.util.LinkedHashMap<>();
        book.setLastReadPosition(65);
        Map<String, Object> current = BookCloudData.toMap(book);
        documents.put(BookCloudData.documentId(book), current);
        book.setLastReadPosition(12);
        documents.put("old-random-id", BookCloudData.toMap(book));
        assertEquals(1, BookCloudData.restoredBooks(documents).size());
        assertEquals(65, BookCloudData.restoredBooks(documents).get(0).getLastReadPosition());
        current.put("deleted", true);
        assertTrue(BookCloudData.restoredBooks(documents).isEmpty());
        current.put("deleted", false);
        assertEquals(1, BookCloudData.restoredBooks(documents).size());
    }

    @Test public void backupRoundTripPreservesEveryBookField() {
        Book original = new Book(12, "Alice", "Lewis Carroll", "1865", "Publisher",
                "WANT_TO_READ", 35, "https://www.gutenberg.org/ebooks/11");
        original.setFormatLabel("Text and audio");

        Book restored = BookCloudData.fromMap(BookCloudData.toMap(original));

        assertEquals(-1, restored.getId()); // Local IDs must not move between devices.
        assertEquals(original.getTitle(), restored.getTitle());
        assertEquals(original.getAuthor(), restored.getAuthor());
        assertEquals(original.getPublishYear(), restored.getPublishYear());
        assertEquals(original.getPublisher(), restored.getPublisher());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getLastReadPosition(), restored.getLastReadPosition());
        assertEquals(original.getReadUrl(), restored.getReadUrl());
        assertEquals(original.getFormatLabel(), restored.getFormatLabel());
    }

    @Test public void legacyBackupWithoutReaderMetadataRestoresSafely() {
        Map<String, Object> oldBackup = new HashMap<>();
        oldBackup.put("title", "Legacy book");
        oldBackup.put("author", "Author");
        oldBackup.put("status", "FINISHED");
        Book restored = BookCloudData.fromMap(oldBackup);
        assertEquals("Legacy book", restored.getTitle());
        assertEquals("FINISHED", restored.getStatus());
        assertEquals(0, restored.getLastReadPosition());
        assertNull(restored.getReadUrl());
        assertEquals("Formats unverified", restored.getFormatLabel());
    }

    @Test public void firestoreLongProgressCannotOverflowOrExceedPercentageRange() {
        Map<String, Object> data = new HashMap<>();
        data.put("lastReadPosition", Long.MAX_VALUE);
        assertEquals(100, BookCloudData.fromMap(data).getLastReadPosition());
        data.put("lastReadPosition", -25L);
        assertEquals(0, BookCloudData.fromMap(data).getLastReadPosition());
        data.put("lastReadPosition", 67L);
        assertEquals(67, BookCloudData.fromMap(data).getLastReadPosition());
    }

    @Test public void malformedOptionalMetadataDoesNotCrashRestore() {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Book");
        data.put("formatLabel", 123L);
        data.put("lastReadPosition", "unknown");
        Book restored = BookCloudData.fromMap(data);
        assertEquals("Formats unverified", restored.getFormatLabel());
        assertEquals(0, restored.getLastReadPosition());
    }
}
