package com.example.BookMate;

import java.util.HashMap;
import java.util.Map;

/** Book backup schema, independent of Firestore so round trips can be tested locally. */
final class BookCloudData {
    static String documentId(Book book) {
        // Length prefixes distinguish names containing separators. Local IDs vary by device.
        String title = String.valueOf(book.getTitle());
        String author = String.valueOf(book.getAuthor());
        String identity = title.length() + ":" + title + author.length() + ":" + author;
        return "book_" + java.util.UUID.nameUUIDFromBytes(
                identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static java.util.List<Book> restoredBooks(Map<String, Map<String, Object>> documents) {
        Map<String, Book> books = new java.util.LinkedHashMap<>();
        // Stable documents override older random-ID backups, including deletion markers.
        for (Map<String, Object> data : documents.values()) {
            Book book = fromMap(data);
            if (book.getTitle() != null && book.getAuthor() != null) {
                books.put(documentId(book), book);
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : documents.entrySet()) {
            Book book = fromMap(entry.getValue());
            if (entry.getKey().equals(documentId(book))) {
                if (Boolean.TRUE.equals(entry.getValue().get("deleted"))) books.remove(entry.getKey());
                else books.put(entry.getKey(), book);
            }
        }
        return new java.util.ArrayList<>(books.values());
    }

    static Map<String, Object> toMap(Book book) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", book.getTitle());
        data.put("author", book.getAuthor());
        data.put("publishYear", book.getPublishYear());
        data.put("publisher", book.getPublisher());
        data.put("status", book.getStatus());
        data.put("lastReadPosition", book.getLastReadPosition());
        data.put("readUrl", book.getReadUrl());
        data.put("formatLabel", book.getFormatLabel());
        return data;
    }

    static Book fromMap(Map<String, Object> data) {
        Object position = data.get("lastReadPosition");
        long progress = position instanceof Number ? ((Number) position).longValue() : 0;
        Book book = new Book(-1, text(data, "title"), text(data, "author"),
                text(data, "publishYear"), text(data, "publisher"), text(data, "status"),
                (int) Math.max(0, Math.min(100, progress)), text(data, "readUrl"));
        book.setFormatLabel(text(data, "formatLabel"));
        return book;
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : null;
    }
}
