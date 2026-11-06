package com.example.BookMate;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * ============================================================
 * QuoteDialog.java — "Save quote" Dialog (shared helper)
 * ============================================================
 * Week 11: Saves a quote / note into the SQLite "quotes" table.
 *
 * Used in two places, so the code lives here once:
 *  - ReaderActivity : the text the user selected in the book.
 *                     Book title + author are already known,
 *                     so those fields are hidden.
 *  - QuotesActivity : the + button, to type a quote or lesson
 *                     by hand (book fields shown, optional).
 * ============================================================
 */
public class QuoteDialog {

    // Longest quote we accept (dialog_quote.xml uses the same maxLength)
    public static final int MAX_QUOTE_LENGTH = 2000;

    /**
     * Shows the dialog.
     *
     * @param activity   the screen that shows the dialog
     * @param text       text to pre-fill (the selected passage), or "" to start empty
     * @param bookTitle  the book's title, or null to let the user type it
     * @param bookAuthor the book's author, or null to let the user type it
     * @param onSaved    runs after the quote is saved (e.g. to refresh a list), or null
     */
    public static void show(Activity activity, String text, String bookTitle,
                            String bookAuthor, Runnable onSaved) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_quote, null);
        EditText etText = view.findViewById(R.id.etQuoteText);
        EditText etBook = view.findViewById(R.id.etQuoteBook);
        EditText etAuthor = view.findViewById(R.id.etQuoteAuthor);
        EditText etNote = view.findViewById(R.id.etQuoteNote);

        etText.setText(text);

        // Book already known (saving from the reader) → no need to type it
        boolean bookKnown = (bookTitle != null);
        if (bookKnown) {
            etBook.setVisibility(View.GONE);
            etAuthor.setVisibility(View.GONE);
        }

        new AlertDialog.Builder(activity)
                .setTitle(bookKnown ? "Save Quote" : "Add Quote or Note")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String quoteText = etText.getText().toString().trim();
                    if (quoteText.isEmpty()) {
                        Toast.makeText(activity, "Nothing saved — the quote was empty",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String title = bookKnown ? bookTitle : emptyToNull(etBook);
                    String author = bookKnown ? bookAuthor : emptyToNull(etAuthor);
                    Quote quote = new Quote(quoteText, emptyToNull(etNote), title, author);

                    // Week 11: Insert into SQLite
                    DatabaseHelper dbHelper = new DatabaseHelper(activity);
                    dbHelper.addQuote(quote);
                    dbHelper.close();
                    activity.getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);

                    Toast.makeText(activity, "Quote saved", Toast.LENGTH_SHORT).show();
                    if (onSaved != null) onSaved.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Returns the field's trimmed text, or null if it's empty (so SQLite stores NULL). */
    private static String emptyToNull(EditText editText) {
        String value = editText.getText().toString().trim();
        return value.isEmpty() ? null : value;
    }
}
