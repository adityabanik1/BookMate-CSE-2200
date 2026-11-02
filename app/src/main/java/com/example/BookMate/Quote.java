package com.example.BookMate;

/**
 * ============================================================
 * Quote.java — Model / Data Class
 * ============================================================
 * Week 9 : Used by QuoteAdapter to populate RecyclerView items.
 * Week 11: Stored in the SQLite "quotes" table.
 *
 * A quote is a passage the user saved while reading (or typed
 * in by hand), with an optional personal note / lesson.
 * The book is remembered by title + author — the same way
 * DatabaseHelper matches books — so the quote still makes
 * sense even if the book is later removed from the list.
 * ============================================================
 */
public class Quote {

    // SQLite primary key (-1 means not yet saved to database)
    private int id;

    private String text;       // the quote / passage itself
    private String note;       // optional "what I learned" note (may be null)
    private String bookTitle;  // may be null for quotes typed in by hand
    private String bookAuthor; // may be null
    private long createdAt;    // when it was saved (System.currentTimeMillis())

    // ── Constructors ──────────────────────────────────────────

    /** Full constructor (used when loading from SQLite). */
    public Quote(int id, String text, String note, String bookTitle,
                 String bookAuthor, long createdAt) {
        this.id = id;
        this.text = text;
        this.note = note;
        this.bookTitle = bookTitle;
        this.bookAuthor = bookAuthor;
        this.createdAt = createdAt;
    }

    /** Convenience constructor for a new quote (no id yet, saved "now"). */
    public Quote(String text, String note, String bookTitle, String bookAuthor) {
        this(-1, text, note, bookTitle, bookAuthor, System.currentTimeMillis());
    }

    // ── Getters ───────────────────────────────────────────────

    public int getId() { return id; }
    public String getText() { return text; }
    public String getNote() { return note; }
    public String getBookTitle() { return bookTitle; }
    public String getBookAuthor() { return bookAuthor; }
    public long getCreatedAt() { return createdAt; }

    // ── Helpers ───────────────────────────────────────────────

    /** Returns true if we know which book this quote is from. */
    public boolean hasBook() {
        return bookTitle != null && !bookTitle.isEmpty();
    }

    /** e.g. "— Pride and Prejudice, Jane Austen" (or null if there's no book). */
    public String getSourceLine() {
        if (!hasBook()) return null;
        String line = "— " + bookTitle;
        if (bookAuthor != null && !bookAuthor.isEmpty()) {
            line += ", " + bookAuthor;
        }
        return line;
    }

    /** The quote in “curly quotes” + the book line. Used by the daily reminder notification. */
    public String getFormattedText() {
        String result = "“" + text + "”";
        if (hasBook()) {
            result += "\n" + getSourceLine();
        }
        return result;
    }
}
