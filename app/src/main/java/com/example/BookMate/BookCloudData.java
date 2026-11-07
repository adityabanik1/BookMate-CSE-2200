package com.example.BookMate;

import java.util.HashMap;
import java.util.Map;

/** Book backup schema, independent of Firestore so round trips can be tested locally. */
final class BookCloudData {
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
