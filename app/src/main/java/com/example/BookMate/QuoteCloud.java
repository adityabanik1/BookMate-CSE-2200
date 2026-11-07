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
        Map<String, Object> data = new HashMap<>();
        data.put("text", quote.getText());
        data.put("note", quote.getNote());
        data.put("bookTitle", quote.getBookTitle());
        data.put("bookAuthor", quote.getBookAuthor());
        data.put("createdAt", quote.getCreatedAt());
        ref.document().set(data).addOnSuccessListener(unused -> success.run())
                .addOnFailureListener(error -> failure.run());
    }
}
