package com.example.BookMate;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * BookAdapter.java — RecyclerView Adapter + ViewHolder
 * ============================================================
 * Week 9: Custom Adapter with a reusable ViewHolder pattern.
 *         Used by both MainActivity (offline list) and
 *         SearchActivity (online search results).
 *
 * The adapter supports a click listener interface so that
 * Activities can handle item clicks to open BookDetailActivity.
 * ============================================================
 */
public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    // ── Data & Listener ───────────────────────────────────────
    private List<Book> bookList;
    private final OnBookClickListener listener;

    /**
     * Callback interface — implemented by the hosting Activity
     * to handle item click events (Week 8: Explicit Intent).
     */
    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    // ── Constructor ───────────────────────────────────────────
    public BookAdapter(List<Book> bookList, OnBookClickListener listener) {
        this.bookList = (bookList != null) ? bookList : new ArrayList<>();
        this.listener = listener;
    }

    // ── Adapter Methods ───────────────────────────────────────

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom item layout (item_book.xml)
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = bookList.get(position);

        // Bind data to views
        holder.tvTitle.setText(book.getTitle());
        holder.tvAuthor.setText("by " + book.getAuthor());
        holder.tvYear.setText("Published: " + book.getPublishYear());

        // Set status badge text and color
        String status = book.getStatus();
        if ("WANT_TO_READ".equals(status)) {
            holder.tvStatus.setText("Want to Read");
            setBadgeColor(holder.tvStatus, 0xFF1976D2); // Blue
        } else if ("FINISHED".equals(status)) {
            holder.tvStatus.setText("Finished");
            setBadgeColor(holder.tvStatus, 0xFF388E3C); // Green
        } else {
            holder.tvStatus.setText("Online Result");
            setBadgeColor(holder.tvStatus, 0xFF757575); // Grey
        }

        // Set click listener — passes the book to the Activity
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBookClick(book);
            }
        });
    }

    @Override
    public int getItemCount() {
        return bookList.size();
    }

    // ── Helper: Update the data set ───────────────────────────
    public void updateBooks(List<Book> newBooks) {
        this.bookList = (newBooks != null) ? newBooks : new ArrayList<>();
        notifyDataSetChanged();
    }

    // ── Helper: Set badge background color programmatically ───
    private void setBadgeColor(TextView badge, int color) {
        GradientDrawable bg = (GradientDrawable) badge.getBackground().mutate();
        bg.setColor(color);
    }

    // ═══════════════════════════════════════════════════════════
    // BookViewHolder — Week 9: RecyclerView.ViewHolder
    // ═══════════════════════════════════════════════════════════
    static class BookViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvAuthor, tvYear, tvStatus;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvBookTitle);
            tvAuthor = itemView.findViewById(R.id.tvBookAuthor);
            tvYear = itemView.findViewById(R.id.tvBookYear);
            tvStatus = itemView.findViewById(R.id.tvBookStatus);
        }
    }
}
