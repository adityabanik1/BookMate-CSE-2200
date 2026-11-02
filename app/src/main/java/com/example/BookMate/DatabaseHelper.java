package com.example.BookMate;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * DatabaseHelper.java — SQLite CRUD Helper
 * ============================================================
 * Week 11: Offline storage using SQLiteOpenHelper.
 *
 * Database : bookmate.db (Version 3)
 * Table    : books
 * Columns  : id (PK, AUTO), title, author, publish_year,
 *            publisher, status ("WANT_TO_READ" | "FINISHED"),
 *            last_read_position (INTEGER), read_url (TEXT)
 * Table    : quotes (added in Version 3 — Quotes & Notes)
 * Columns  : id (PK, AUTO), quote_text, note, book_title,
 *            book_author, created_at (INTEGER, millis)
 * ============================================================
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // ── Constants ─────────────────────────────────────────────
    private static final String DATABASE_NAME = "bookmate.db";
    private static final int DATABASE_VERSION = 3; // 2: reader columns, 3: quotes table

    private static final String TABLE_BOOKS = "books";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_AUTHOR = "author";
    private static final String COL_PUBLISH_YEAR = "publish_year";
    private static final String COL_PUBLISHER = "publisher";
    private static final String COL_STATUS = "status";
    private static final String COL_LAST_READ_POSITION = "last_read_position";
    private static final String COL_READ_URL = "read_url";

    // Quotes table (Version 3) — also uses COL_ID as its primary key
    private static final String TABLE_QUOTES = "quotes";
    private static final String COL_QUOTE_TEXT = "quote_text";
    private static final String COL_QUOTE_NOTE = "note";
    private static final String COL_QUOTE_BOOK_TITLE = "book_title";
    private static final String COL_QUOTE_BOOK_AUTHOR = "book_author";
    private static final String COL_QUOTE_CREATED_AT = "created_at";

    // ── Constructor ───────────────────────────────────────────
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // ── onCreate: Create the books and quotes tables ──────────
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_BOOKS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT, "
                + COL_AUTHOR + " TEXT, "
                + COL_PUBLISH_YEAR + " TEXT, "
                + COL_PUBLISHER + " TEXT, "
                + COL_STATUS + " TEXT, "
                + COL_LAST_READ_POSITION + " INTEGER DEFAULT 0, "
                + COL_READ_URL + " TEXT)";
        db.execSQL(createTable);
        createQuotesTable(db);
    }

    // ── onUpgrade: Migrate schema gracefully ──────────────────
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add reader support columns without wiping existing saved books
            db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_LAST_READ_POSITION + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_READ_URL + " TEXT");
        }
        if (oldVersion < 3) {
            // Add the quotes table — existing saved books are not touched
            createQuotesTable(db);
        }
    }

    /**
     * Creates the quotes table. Called from onCreate (new installs)
     * and onUpgrade (phones that already have Version 1 or 2).
     */
    private void createQuotesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_QUOTES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_QUOTE_TEXT + " TEXT NOT NULL, "
                + COL_QUOTE_NOTE + " TEXT, "
                + COL_QUOTE_BOOK_TITLE + " TEXT, "
                + COL_QUOTE_BOOK_AUTHOR + " TEXT, "
                + COL_QUOTE_CREATED_AT + " INTEGER)");
    }

    // ── CRUD Operations ───────────────────────────────────────

    /**
     * Inserts a new book or updates an existing one (matched by title + author).
     * This lets the user change a book's status without creating duplicates.
     */
    public long addOrUpdateBook(Book book) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TITLE, book.getTitle());
        values.put(COL_AUTHOR, book.getAuthor());
        values.put(COL_PUBLISH_YEAR, book.getPublishYear());
        values.put(COL_PUBLISHER, book.getPublisher());
        values.put(COL_STATUS, book.getStatus());
        values.put(COL_LAST_READ_POSITION, book.getLastReadPosition());
        values.put(COL_READ_URL, book.getReadUrl());

        // Check if book already exists (same title AND same author)
        Cursor cursor = db.query(TABLE_BOOKS, new String[]{COL_ID},
                COL_TITLE + " = ? AND " + COL_AUTHOR + " = ?",
                new String[]{book.getTitle(), book.getAuthor()},
                null, null, null);

        long result;
        if (cursor != null && cursor.moveToFirst()) {
            // Book exists → update it
            int existingId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            result = db.update(TABLE_BOOKS, values,
                    COL_ID + " = ?", new String[]{String.valueOf(existingId)});
            book.setId(existingId);
        } else {
            // New book → insert
            result = db.insert(TABLE_BOOKS, null, values);
            book.setId((int) result);
        }

        if (cursor != null) cursor.close();
        return result;
    }

    /**
     * Updates only the reading progress (percent read, 0-100) of a saved book.
     */
    public void updateReadingProgress(int bookId, int percent) {
        if (bookId <= 0) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_LAST_READ_POSITION, percent);
        db.update(TABLE_BOOKS, values, COL_ID + " = ?", new String[]{String.valueOf(bookId)});
    }

    /**
     * Updates the status of a saved book (e.g., mark as "FINISHED").
     */
    public void updateBookStatus(int bookId, String status) {
        if (bookId <= 0) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_STATUS, status);
        db.update(TABLE_BOOKS, values, COL_ID + " = ?", new String[]{String.valueOf(bookId)});
    }

    /**
     * Returns all books with the given status ("WANT_TO_READ" or "FINISHED").
     * If status is null, returns ALL saved books.
     */
    public List<Book> getBooksByStatus(String status) {
        List<Book> books = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor;
        if (status == null) {
            cursor = db.query(TABLE_BOOKS, null, null, null,
                    null, null, COL_TITLE + " ASC");
        } else {
            cursor = db.query(TABLE_BOOKS, null,
                    COL_STATUS + " = ?", new String[]{status},
                    null, null, COL_TITLE + " ASC");
        }

        if (cursor != null && cursor.moveToFirst()) {
            do {
                books.add(cursorToBook(cursor));
            } while (cursor.moveToNext());
        }

        if (cursor != null) cursor.close();
        return books;
    }

    /**
     * Deletes a book from the database by its ID.
     */
    public void deleteBook(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_BOOKS, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    /**
     * Checks whether a book with the given title and author is already saved.
     * Returns the Book if found, or null otherwise.
     */
    public Book isBookSaved(String title, String author) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKS, null,
                COL_TITLE + " = ? AND " + COL_AUTHOR + " = ?",
                new String[]{title, author},
                null, null, null);

        Book book = null;
        if (cursor != null && cursor.moveToFirst()) {
            book = cursorToBook(cursor);
        }

        if (cursor != null) cursor.close();
        return book;
    }

    /**
     * Builds a Book from the cursor's current row.
     * Shared by getBooksByStatus() and isBookSaved().
     */
    private Book cursorToBook(Cursor cursor) {
        int lastPos = 0;
        int posColIndex = cursor.getColumnIndex(COL_LAST_READ_POSITION);
        if (posColIndex != -1 && !cursor.isNull(posColIndex)) {
            lastPos = cursor.getInt(posColIndex);
        }

        String readUrl = null;
        int urlColIndex = cursor.getColumnIndex(COL_READ_URL);
        if (urlColIndex != -1 && !cursor.isNull(urlColIndex)) {
            readUrl = cursor.getString(urlColIndex);
        }

        return new Book(
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_PUBLISH_YEAR)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_PUBLISHER)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
                lastPos,
                readUrl
        );
    }

    /**
     * Returns the total count of saved books (used by BookService).
     */
    public int getBookCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_BOOKS, null);
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        if (cursor != null) cursor.close();
        return count;
    }

    // ── Quotes & Notes (Version 3) ────────────────────────────

    /**
     * Saves a new quote. Returns the new row ID, or -1 on error.
     */
    public long addQuote(Quote quote) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_QUOTE_TEXT, quote.getText());
        values.put(COL_QUOTE_NOTE, quote.getNote());
        values.put(COL_QUOTE_BOOK_TITLE, quote.getBookTitle());
        values.put(COL_QUOTE_BOOK_AUTHOR, quote.getBookAuthor());
        values.put(COL_QUOTE_CREATED_AT, quote.getCreatedAt());
        return db.insert(TABLE_QUOTES, null, values);
    }

    /**
     * Saves the quote only if it isn't on the phone yet (same text AND same
     * saved time). Used by cloud Restore, so restoring twice doesn't create
     * duplicates. Returns true if the quote was added.
     */
    public boolean addQuoteIfNew(Quote quote) {
        if (quote.getText() == null) return false; // nothing to save

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_QUOTES, new String[]{COL_ID},
                COL_QUOTE_TEXT + " = ? AND " + COL_QUOTE_CREATED_AT + " = ?",
                new String[]{quote.getText(), String.valueOf(quote.getCreatedAt())},
                null, null, null);
        boolean alreadySaved = (cursor != null && cursor.moveToFirst());
        if (cursor != null) cursor.close();

        if (alreadySaved) return false;
        addQuote(quote);
        return true;
    }

    /**
     * Returns all saved quotes, newest first.
     */
    public List<Quote> getAllQuotes() {
        List<Quote> quotes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_QUOTES, null, null, null,
                null, null, COL_QUOTE_CREATED_AT + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                quotes.add(cursorToQuote(cursor));
            } while (cursor.moveToNext());
        }

        if (cursor != null) cursor.close();
        return quotes;
    }

    /**
     * Returns one random saved quote (for the daily reminder),
     * or null if no quotes are saved yet.
     */
    public Quote getRandomQuote() {
        SQLiteDatabase db = this.getReadableDatabase();
        // Same as: SELECT * FROM quotes ORDER BY RANDOM() LIMIT 1
        Cursor cursor = db.query(TABLE_QUOTES, null, null, null,
                null, null, "RANDOM()", "1");

        Quote quote = null;
        if (cursor != null && cursor.moveToFirst()) {
            quote = cursorToQuote(cursor);
        }

        if (cursor != null) cursor.close();
        return quote;
    }

    /**
     * Deletes a quote from the database by its ID.
     */
    public void deleteQuote(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_QUOTES, COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    /**
     * Builds a Quote from the cursor's current row.
     * Shared by getAllQuotes() and getRandomQuote().
     */
    private Quote cursorToQuote(Cursor cursor) {
        return new Quote(
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_QUOTE_TEXT)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_QUOTE_NOTE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_QUOTE_BOOK_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_QUOTE_BOOK_AUTHOR)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_QUOTE_CREATED_AT))
        );
    }
}
