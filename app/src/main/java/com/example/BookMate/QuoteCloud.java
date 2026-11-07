package com.example.BookMate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/** Account-owned quote persistence, compatible with existing manual backups. */
final class QuoteCloud {
    static CollectionReference collection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid()).collection("quotes");
    }

    static void save(Quote quote, Runnable success, Runnable failure) {
        CollectionReference ref = collection();
        if (ref == null) { failure.run(); return; }
        Map<String, Object> data = toMap(quote);
        data.put("deleted", false);
        ref.document(documentId(quote)).set(data).addOnSuccessListener(unused -> success.run())
                .addOnFailureListener(error -> failure.run());
    }

    static String documentId(Quote quote) {
        String text = quote.getText();
        return "quote_" + java.util.UUID.nameUUIDFromBytes(
                (text.length() + ":" + text + ":" + quote.getCreatedAt())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static android.content.SharedPreferences deletions(android.content.Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String owner = user == null ? "signed_out" : user.getUid();
        return context.getSharedPreferences("quote_deletions_" + java.util.UUID.nameUUIDFromBytes(
                owner.getBytes(java.nio.charset.StandardCharsets.UTF_8)), android.content.Context.MODE_PRIVATE);
    }

    static void delete(android.content.Context context, Quote quote) {
        // Keep the local marker too: an older in-flight restore must not undo deletion.
        deletions(context).edit().putBoolean(documentId(quote), true).apply();
        CollectionReference ref = collection();
        if (ref == null) return;
        Map<String, Object> data = toMap(quote);
        data.put("deleted", true);
        ref.document(documentId(quote)).set(data).addOnFailureListener(error ->
                android.widget.Toast.makeText(context.getApplicationContext(),
                        "Quote removed on this device. Account deletion could not sync.",
                        android.widget.Toast.LENGTH_LONG).show());
    }

    static java.util.List<Quote> restoredQuotes(Map<String, Map<String, Object>> documents) {
        Map<String, Quote> quotes = new java.util.LinkedHashMap<>();
        for (Map<String, Object> data : documents.values()) {
            Quote quote = fromMap(data);
            if (quote != null) quotes.put(documentId(quote), quote);
        }
        for (Map.Entry<String, Map<String, Object>> entry : documents.entrySet()) {
            Quote quote = fromMap(entry.getValue());
            if (quote != null && entry.getKey().equals(documentId(quote))) {
                if (Boolean.TRUE.equals(entry.getValue().get("deleted"))) quotes.remove(entry.getKey());
                else quotes.put(entry.getKey(), quote);
            }
        }
        return new java.util.ArrayList<>(quotes.values());
    }

    private static Quote fromMap(Map<String, Object> data) {
        if (data == null || !(data.get("text") instanceof String)
                || !(data.get("createdAt") instanceof Number)) return null;
        return new Quote(-1, (String) data.get("text"), optionalText(data, "note"),
                optionalText(data, "bookTitle"), optionalText(data, "bookAuthor"),
                ((Number) data.get("createdAt")).longValue());
    }

    private static String optionalText(Map<String, Object> data, String key) {
        return data.get(key) instanceof String ? (String) data.get(key) : null;
    }

    static int restore(android.content.Context context, DatabaseHelper db,
                       com.google.firebase.firestore.QuerySnapshot snapshot) {
        Map<String, Map<String, Object>> documents = new java.util.LinkedHashMap<>();
        android.content.SharedPreferences removed = deletions(context);
        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
            documents.put(doc.getId(), doc.getData());
            Quote quote = fromMap(doc.getData());
            if (quote != null && doc.getId().equals(documentId(quote))
                    && Boolean.TRUE.equals(doc.getData().get("deleted"))) {
                removed.edit().putBoolean(documentId(quote), true).apply();
            }
        }
        for (Quote local : db.getAllQuotes()) {
            if (removed.getBoolean(documentId(local), false)) db.deleteQuote(local.getId());
        }
        int added = 0;
        for (Quote quote : restoredQuotes(documents)) {
            if (!removed.getBoolean(documentId(quote), false) && db.addQuoteIfNew(quote)) added++;
        }
        return added;
    }

    static Map<String, Object> toMap(Quote quote) {
        Map<String, Object> data = new HashMap<>();
        data.put("text", quote.getText());
        data.put("note", quote.getNote());
        data.put("bookTitle", quote.getBookTitle());
        data.put("bookAuthor", quote.getBookAuthor());
        data.put("createdAt", quote.getCreatedAt());
        return data;
    }
}
