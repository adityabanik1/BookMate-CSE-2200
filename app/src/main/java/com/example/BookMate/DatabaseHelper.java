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
 * Database : bookmate.db (Version 2)
 * Table    : books
 * Columns  : id (PK, AUTO), title, author, publish_year,
 *            publisher, status ("WANT_TO_READ" | "FINISHED"),
 *            last_read_position (INTEGER), read_url (TEXT)
 * ============================================================
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    // ── Constants ─────────────────────────────────────────────
    private static final String DATABASE_NAME = "bookmate.db";
    private static final int DATABASE_VERSION = 2; // Incremented for reader columns

    private static final String TABLE_BOOKS = "books";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_AUTHOR = "author";
    private static final String COL_PUBLISH_YEAR = "publish_year";
    private static final String COL_PUBLISHER = "publisher";
    private static final String COL_STATUS = "status";
    private static final String COL_LAST_READ_POSITION = "last_read_position";
    private static final String COL_READ_URL = "read_url";

    // ── Constructor ───────────────────────────────────────────
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // ── onCreate: Create the books table ──────────────────────
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
    }

    // ── onUpgrade: Migrate schema gracefully ──────────────────
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add reader support columns without wiping existing saved books
            db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_LAST_READ_POSITION + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_BOOKS + " ADD COLUMN " + COL_READ_URL + " TEXT");
        }
    }

    // ── CRUD Operations ───────────────────────────────────────

    /**
     * Inserts a new book or updates an existing one (matched by title).
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

        // Check if book already exists by title
        Cursor cursor = db.query(TABLE_BOOKS, new String[]{COL_ID},
                COL_TITLE + " = ?", new String[]{book.getTitle()},
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
     * Updates only the reading progress (scroll position in pixels) of a saved book.
     */
    public void updateReadingProgress(int bookId, int position) {
        if (bookId <= 0) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_LAST_READ_POSITION, position);
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

                Book book = new Book(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_PUBLISH_YEAR)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_PUBLISHER)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
                        lastPos,
                        readUrl
                );
                books.add(book);
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
     * Checks whether a book with the given title is already saved.
     * Returns the Book if found, or null otherwise.
     */
    public Book isBookSaved(String title) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BOOKS, null,
                COL_TITLE + " = ?", new String[]{title},
                null, null, null);

        Book book = null;
        if (cursor != null && cursor.moveToFirst()) {
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

            book = new Book(
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

        if (cursor != null) cursor.close();
        return book;
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
}
