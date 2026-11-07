package com.example.BookMate;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * BookAdapter.java — RecyclerView Adapter + ViewHolder
 * Week 9: Custom Adapter with a reusable ViewHolder pattern.
 *         Used by both MainActivity (offline list) and
 *         SearchActivity (online search results across providers).
 *
 * The adapter supports a click listener interface so that
 * Activities can handle item clicks to open BookDetailActivity.
 * Supports distinct color-coded badges for:
 * - Saved Books ("Want to Read", "Finished")
 * - Project Gutenberg ("Free Ebook")
 * - Open Library / Internet Archive ("Open Library")
 * - Curated offline classics ("Featured")
 */
public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    // Data & Listener
    private List<Book> bookList;
    private final OnBookClickListener listener;
    private String unsavedLabel = "Online Result"; // badge text for books not saved yet

    /**
     * Callback interface — implemented by the hosting Activity
     * to handle item click events (Week 8: Explicit Intent).
     */
    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    // Constructor
    public BookAdapter(List<Book> bookList, OnBookClickListener listener) {
        this.bookList = (bookList != null) ? bookList : new ArrayList<>();
        this.listener = listener;
    }

    // Adapter Methods

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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

        String yearText = book.getPublishYear();
        String publisher = book.getPublisher();
        if (publisher != null && !publisher.isEmpty() && !"N/A".equalsIgnoreCase(publisher)) {
            holder.tvYear.setText(publisher + " • " + yearText);
        } else {
            holder.tvYear.setText("Published: " + yearText);
        }

        // Set status/source badge text and color
        String status = book.getStatus();
        if ("WANT_TO_READ".equals(status)) {
            holder.tvStatus.setText("Want to Read");
            setBadgeColor(holder.tvStatus, R.color.badge_want_to_read); // Blue
        } else if ("FINISHED".equals(status)) {
            holder.tvStatus.setText("Finished");
            setBadgeColor(holder.tvStatus, R.color.badge_finished); // Green
        } else if ("Featured".equalsIgnoreCase(unsavedLabel)) {
            holder.tvStatus.setText("Featured");
            setBadgeColor(holder.tvStatus, R.color.badge_featured); // Amber
        } else if (book.getReadUrl() != null && book.getReadUrl().contains("gutenberg.org")) {
            holder.tvStatus.setText(book.getFormatLabel());
            setBadgeColor(holder.tvStatus, R.color.badge_gutenberg); // Teal
        } else if (book.getReadUrl() != null && (book.getReadUrl().contains("archive.org") || book.getReadUrl().contains("openlibrary.org"))) {
            holder.tvStatus.setText("Open Library");
            setBadgeColor(holder.tvStatus, R.color.badge_open_library); // Purple
        } else {
            holder.tvStatus.setText(unsavedLabel);
            setBadgeColor(holder.tvStatus, R.color.badge_online); // Grey
        }

        // Set click listener — passes the book to the Activity with fluid tactile click
        holder.itemView.setOnClickListener(v -> UiUtils.animateClick(v, () -> {
            if (listener != null) listener.onBookClick(book);
        }));
    }

    @Override
    public int getItemCount() {
        return bookList.size();
    }

    // Helper: Update the data set
    public void updateBooks(List<Book> newBooks) {
        this.bookList = (newBooks != null) ? newBooks : new ArrayList<>();
        notifyDataSetChanged();
    }

    // Helper: Change the badge text for unsaved books
    public void setUnsavedLabel(String label) {
        this.unsavedLabel = label;
        notifyDataSetChanged();
    }

    // Helper: Set badge background color programmatically
    public static void setBadgeColor(TextView badge, int colorResId) {
        GradientDrawable bg = (GradientDrawable) badge.getBackground().mutate();
        bg.setColor(ContextCompat.getColor(badge.getContext(), colorResId));
    }
    // BookViewHolder — Week 9: RecyclerView.ViewHolder
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
