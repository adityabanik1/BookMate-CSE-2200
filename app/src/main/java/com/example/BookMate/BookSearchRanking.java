package com.example.BookMate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provider-independent relevance ordering; retain different authors' same-title works. */
final class BookSearchRanking {
    static List<Book> rank(String query, List<Book> books) {
        Map<String, Book> unique = new LinkedHashMap<>();
        for (Book book : books) {
            String key = normalize(book.getTitle()) + "|" + normalize(book.getAuthor());
            unique.putIfAbsent(key, book);
        }
        List<Book> ranked = new ArrayList<>(unique.values());
        ranked.sort((first, second) -> {
            // Higher scores come first. Equal scores use alphabetical title, then author.
            int scoreOrder = Integer.compare(score(query, second), score(query, first));
            if (scoreOrder != 0) return scoreOrder;
            int titleOrder = normalize(first.getTitle()).compareTo(normalize(second.getTitle()));
            if (titleOrder != 0) return titleOrder;
            return normalize(first.getAuthor()).compareTo(normalize(second.getAuthor()));
        });
        return ranked;
    }

    private static int score(String query, Book book) {
        String q = normalize(query);
        String title = normalize(book.getTitle());
        String author = normalize(book.getAuthor());
        if (q.isEmpty()) return 0;
        int score = 0;
        if (title.equals(q)) score += 10000;
        if ((" " + title + " ").contains(" " + q + " ")) score += 4000;
        if (author.equals(q)) score += 8000;
        if ((" " + author + " ").contains(" " + q + " ")) score += 3000;
        int matched = 0;
        int total = 0;
        for (String token : q.split(" ")) {
            if (token.equals("the") || token.equals("of") || token.equals("a") || token.equals("an")) continue;
            total++;
            if ((" " + title + " ").contains(" " + token + " ")) {
                score += 200;
                matched++;
            } else if ((" " + author + " ").contains(" " + token + " ")) {
                score += 150;
                matched++;
            }
        }
        if (total > 0 && matched == total) score += 1500;
        return score;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
