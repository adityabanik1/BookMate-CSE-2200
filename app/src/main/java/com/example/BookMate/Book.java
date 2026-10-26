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
 * Reader : Stores lastReadPosition (scroll position) and
 *          readUrl (for in-app online reading).
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

    // Reading progress & online reader link
    private int lastReadPosition; // Scroll offset (in pixels)
    private String readUrl;       // Online reader URL (e.g. Internet Archive/Open Library)

    // ── Constructors ──────────────────────────────────────────

    /** Full constructor (used when loading from SQLite). */
    public Book(int id, String title, String author, String publishYear,
                String publisher, String status, int lastReadPosition, String readUrl) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.publishYear = publishYear;
        this.publisher = publisher;
        this.status = status;
        this.lastReadPosition = lastReadPosition;
        this.readUrl = readUrl;
    }

    /** Legacy constructor for backward compatibility. */
    public Book(int id, String title, String author, String publishYear,
                String publisher, String status) {
        this(id, title, author, publishYear, publisher, status, 0, null);
    }

    /** Convenience constructor for API results (no id, no status yet). */
    public Book(String title, String author, String publishYear, String publisher) {
        this(-1, title, author, publishYear, publisher, null, 0, null);
    }

    /** Convenience constructor for API results with readUrl. */
    public Book(String title, String author, String publishYear, String publisher, String readUrl) {
        this(-1, title, author, publishYear, publisher, null, 0, readUrl);
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

    public int getLastReadPosition() { return lastReadPosition; }
    public void setLastReadPosition(int lastReadPosition) { this.lastReadPosition = lastReadPosition; }

    public String getReadUrl() { return readUrl; }
    public void setReadUrl(String readUrl) { this.readUrl = readUrl; }

    /** Returns true if this book has been saved to the local database. */
    public boolean isSaved() {
        return status != null && !status.isEmpty();
    }
}
