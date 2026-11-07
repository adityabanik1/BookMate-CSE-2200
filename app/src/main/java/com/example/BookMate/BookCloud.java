package com.example.BookMate;

import android.content.Context;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Map;

/** Saves individual book changes without replacing the account library. */
final class BookCloud {
    private static long revision;

    static synchronized long revision() { return revision; }

    private static android.content.SharedPreferences demoPreferences(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return context.getSharedPreferences("bookmate_demo_restore_" +
                (user == null ? "signed_out" : java.util.UUID.nameUUIDFromBytes(
                        user.getUid().getBytes(java.nio.charset.StandardCharsets.UTF_8))), Context.MODE_PRIVATE);
    }

    static void markDemo(Context context, Book book) {
        demoPreferences(context).edit().putBoolean(BookCloudData.documentId(book), true).apply();
    }

    static boolean isUntouchedDemo(Context context, Book book) {
        return demoPreferences(context).getBoolean(BookCloudData.documentId(book), false);
    }

    static void clearDemo(Context context, Book book) {
        demoPreferences(context).edit().remove(BookCloudData.documentId(book)).apply();
    }
    static CollectionReference collection() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid()).collection("books");
    }

    static void save(Context context, Book book) {
        write(context, book, false);
    }

    static void delete(Context context, Book book) {
        write(context, book, true);
    }

    private static void write(Context context, Book book, boolean deleted) {
        synchronized (BookCloud.class) { revision++; }
        clearDemo(context, book);
        CollectionReference ref = collection();
        if (ref == null) return;
        Context app = context.getApplicationContext();
        Map<String, Object> data = BookCloudData.toMap(book);
        // Keep a deletion marker so legacy backups cannot bring a removed book back.
        data.put("deleted", deleted);
        ref.document(BookCloudData.documentId(book)).set(data)
                .addOnSuccessListener(unused -> {
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (current != null && current.getUid().equals(ref.getParent().getId())) {
                        Toast.makeText(app, deleted ? "Removed from account" : "Book saved to account",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(error -> Toast.makeText(app,
                        "Account sync failed. Your device copy is unchanged.", Toast.LENGTH_LONG).show());
    }
}
