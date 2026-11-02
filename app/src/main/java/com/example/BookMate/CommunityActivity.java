package com.example.BookMate;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * CommunityActivity.java — Live Community Recommendations
 * ============================================================
 * Week 8 : Explicit Intent to BookDetailActivity on item click.
 * Week 9 : Reuses BookAdapter + item_book.xml for the list.
 * Firebase: Realtime Database. A ValueEventListener on
 *          /recommendations is called once with the current
 *          data and again every time anyone adds a book, so
 *          the list updates live.
 *          Listener is added in onStart() and removed in onStop().
 * ============================================================
 */
public class CommunityActivity extends AppCompatActivity implements BookAdapter.OnBookClickListener {

    private static final String TAG = "CommunityActivity";

    // ── UI References ─────────────────────────────────────────
    private RecyclerView recyclerCommunity;
    private ProgressBar progressCommunity;
    private TextView tvCommunityEmpty;

    // ── Data ──────────────────────────────────────────────────
    private BookAdapter adapter;

    // ── Firebase ──────────────────────────────────────────────
    private DatabaseReference recommendationsRef;
    private ValueEventListener recommendationsListener;

    // ═══════════════════════════════════════════════════════════
    // Lifecycle — Week 8
    // ═══════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        MaterialToolbar toolbar = findViewById(R.id.toolbarCommunity);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerCommunity = findViewById(R.id.recyclerCommunity);
        progressCommunity = findViewById(R.id.progressCommunity);
        tvCommunityEmpty = findViewById(R.id.tvCommunityEmpty);

        // ── Setup RecyclerView (Week 9: reuse BookAdapter) ────
        adapter = new BookAdapter(new ArrayList<>(), this);
        adapter.setUnsavedLabel("Community");
        recyclerCommunity.setLayoutManager(new LinearLayoutManager(this));
        recyclerCommunity.setItemAnimator(new DefaultItemAnimator());
        recyclerCommunity.setAdapter(adapter);

        recommendationsRef = FirebaseDatabase.getInstance().getReference("recommendations");
        recommendationsListener = createListener();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start listening for live updates while the screen is visible
        recommendationsRef.addValueEventListener(recommendationsListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop listening when the screen is hidden (saves battery and data)
        recommendationsRef.removeEventListener(recommendationsListener);
    }

    // ═══════════════════════════════════════════════════════════
    // Firebase Realtime Database — ValueEventListener
    // ═══════════════════════════════════════════════════════════

    private ValueEventListener createListener() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Called with ALL recommendations every time something changes
                List<Book> books = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String title = child.child("title").getValue(String.class);
                    String author = child.child("author").getValue(String.class);
                    String year = child.child("year").getValue(String.class);
                    if (title == null) continue; // skip broken entries
                    // ("recommendedBy" is stored too, but item_book.xml has no place to show it)

                    // add(0, ...) puts the newest recommendation at the top
                    // (push() keys are in time order, oldest first)
                    books.add(0, new Book(title,
                            author != null ? author : "Unknown Author",
                            year != null ? year : "N/A",
                            "N/A"));
                }

                Log.d(TAG, "onDataChange: " + books.size() + " recommendations");
                progressCommunity.setVisibility(View.GONE);
                tvCommunityEmpty.setVisibility(books.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.updateBooks(books);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // e.g. security rules denied access
                Log.w(TAG, "onCancelled: " + error.getMessage());
                progressCommunity.setVisibility(View.GONE);
                Toast.makeText(CommunityActivity.this,
                        "Couldn't load recommendations: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };
    }

    // ═══════════════════════════════════════════════════════════
    // Item Click — Week 8: Explicit Intent
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onBookClick(Book book) {
        Intent intent = new Intent(CommunityActivity.this, BookDetailActivity.class);
        intent.putExtra("book", book); // Book implements Serializable
        startActivity(intent);
    }
}
