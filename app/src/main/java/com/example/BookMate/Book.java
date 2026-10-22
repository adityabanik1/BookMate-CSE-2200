package com.example.BookMate;

import java.io.Serializable;

/**
 * ============================================================
 * Book.java — Model / Data Class
 * ============================================================
 * Week 8 : Passed between Activities via Intent.putExtra()
 *           (implements Serializable so it can be serialized
 *           into an Intent Bundle).
 * Week 9 : Used by BookAdapter to populate RecyclerView items.
 * Week 11: Mapped from JSON (API) and stored in SQLite.
 * ============================================================
 */
public class Book implements Serializable {

    // SQLite primary key (-1 means not yet saved to database)
    private int id;

    private String title;
    private String author;
    private String publishYear;
    private String publisher;

    // Status: "WANT_TO_READ", "FINISHED", or null (online result, not saved)
    private String status;

    // ── Constructors ──────────────────────────────────────────

    /** Full constructor (used when loading from SQLite). */
    public Book(int id, String title, String author, String publishYear,
                String publisher, String status) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.publishYear = publishYear;
        this.publisher = publisher;
        this.status = status;
    }

    /** Convenience constructor for API results (no id, no status yet). */
    public Book(String title, String author, String publishYear, String publisher) {
        this(-1, title, author, publishYear, publisher, null);
    }

    // ── Getters & Setters ─────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getPublishYear() { return publishYear; }
    public void setPublishYear(String publishYear) { this.publishYear = publishYear; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** Returns true if this book has been saved to the local database. */
    public boolean isSaved() {
        return status != null && !status.isEmpty();
    }
}
