package com.example.BookMate;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * FeaturedBooksParser.java — XML Parser (XmlPullParser)
 * ============================================================
 * Week 11: Reads assets/featured_books.xml and turns each
 *          <book> element into a Book object.
 *
 * XML format:
 *   <book gutenbergId="1342">          ← attribute
 *       <title>Pride and Prejudice</title>   ← child elements
 *       <author>Jane Austen</author>
 *       <year>1813</year>
 *       <publisher>T. Egerton</publisher>
 *   </book>
 *
 * The file is tiny, so it is fine to parse on the main thread.
 * ============================================================
 */
public class FeaturedBooksParser {

    private static final String TAG = "FeaturedBooksParser";
    private static final String FILE_NAME = "featured_books.xml";

    /**
     * Parses the featured books XML file from assets.
     * Returns an empty list if the file can't be read.
     */
    public static List<Book> parse(Context context) {
        List<Book> books = new ArrayList<>();

        try {
            InputStream is = context.getAssets().open(FILE_NAME);
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(is, "UTF-8");

            // Values for the <book> we are currently reading
            String gutenbergId = null;
            String title = null, author = null, year = null, publisher = null;
            String currentTag = null;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = parser.getName();
                        if ("book".equals(currentTag)) {
                            // New book → read its attribute and reset the fields
                            gutenbergId = parser.getAttributeValue(null, "gutenbergId");
                            title = author = year = publisher = null;
                        }
                        break;

                    case XmlPullParser.TEXT:
                        String text = parser.getText().trim();
                        if (text.isEmpty() || currentTag == null) break;

                        if ("title".equals(currentTag)) {
                            title = text;
                        } else if ("author".equals(currentTag)) {
                            author = text;
                        } else if ("year".equals(currentTag)) {
                            year = text;
                        } else if ("publisher".equals(currentTag)) {
                            publisher = text;
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("book".equals(parser.getName()) && title != null) {
                            // Finished one <book> → create the Book object
                            String readUrl = "https://www.gutenberg.org/ebooks/"
                                    + gutenbergId + ".html.images";
                            books.add(new Book(title,
                                    author != null ? author : "Unknown Author",
                                    year != null ? year : "N/A",
                                    publisher != null ? publisher : "N/A",
                                    readUrl));
                        }
                        currentTag = null;
                        break;
                }
                eventType = parser.next();
            }
            is.close();
        } catch (Exception e) {
            Log.e(TAG, "parse: Could not read " + FILE_NAME, e);
        }

        Log.d(TAG, "parse: Loaded " + books.size() + " featured books");
        return books;
    }
}
