package com.example.BookMate;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ============================================================
 * QuoteAdapter.java — RecyclerView Adapter + ViewHolder
 * ============================================================
 * Week 9: Custom Adapter with a reusable ViewHolder pattern
 *         (same structure as BookAdapter), used by
 *         QuotesActivity to show saved quotes.
 *
 * Tap and long-press are passed to the Activity through the
 * OnQuoteClickListener interface.
 * ============================================================
 */
public class QuoteAdapter extends RecyclerView.Adapter<QuoteAdapter.QuoteViewHolder> {

    // ── Data & Listener ───────────────────────────────────────
    private List<Quote> quoteList;
    private final OnQuoteClickListener listener;

    /**
     * Callback interface — implemented by QuotesActivity.
     */
    public interface OnQuoteClickListener {
        void onQuoteClick(Quote quote);     // tap → open the quote in its book
        void onQuoteLongClick(Quote quote); // long-press → Delete
    }

    // ── Constructor ───────────────────────────────────────────
    public QuoteAdapter(List<Quote> quoteList, OnQuoteClickListener listener) {
        this.quoteList = (quoteList != null) ? quoteList : new ArrayList<>();
        this.listener = listener;
    }

    // ── Adapter Methods ───────────────────────────────────────

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom item layout (item_quote.xml)
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quote, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        Quote quote = quoteList.get(position);

        holder.tvQuoteText.setText("“" + quote.getText() + "”");

        // Book line — hidden for quotes typed in without a book
        String source = quote.getSourceLine();
        if (source != null) {
            holder.tvQuoteSource.setText(source);
            holder.tvQuoteSource.setVisibility(View.VISIBLE);
        } else {
            holder.tvQuoteSource.setVisibility(View.GONE);
        }

        // Personal note / lesson — hidden if empty
        if (quote.getNote() != null) {
            holder.tvQuoteNote.setText("📝 " + quote.getNote());
            holder.tvQuoteNote.setVisibility(View.VISIBLE);
        } else {
            holder.tvQuoteNote.setVisibility(View.GONE);
        }

        // Date saved, in the phone's date format (e.g. "Saved 1 Nov 2026")
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM)
                .format(new Date(quote.getCreatedAt()));
        holder.tvQuoteDate.setText("Saved " + date);

        // Click listeners — pass the quote to the Activity
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onQuoteClick(quote);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onQuoteLongClick(quote);
            }
            return true; // true = handled, so a normal click doesn't also fire
        });
    }

    @Override
    public int getItemCount() {
        return quoteList.size();
    }

    // ── Helper: Update the data set ───────────────────────────
    public void updateQuotes(List<Quote> newQuotes) {
        this.quoteList = (newQuotes != null) ? newQuotes : new ArrayList<>();
        notifyDataSetChanged();
    }

    // ═══════════════════════════════════════════════════════════
    // QuoteViewHolder — Week 9: RecyclerView.ViewHolder
    // ═══════════════════════════════════════════════════════════
    static class QuoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuoteText, tvQuoteSource, tvQuoteNote, tvQuoteDate;

        QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuoteText = itemView.findViewById(R.id.tvQuoteText);
            tvQuoteSource = itemView.findViewById(R.id.tvQuoteSource);
            tvQuoteNote = itemView.findViewById(R.id.tvQuoteNote);
            tvQuoteDate = itemView.findViewById(R.id.tvQuoteDate);
        }
    }
}
