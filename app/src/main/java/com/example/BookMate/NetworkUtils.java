package com.example.BookMate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * NetworkUtils.java — HTTP Networking + JSON Parser
 * ============================================================
 * Week 11: Performs HTTP GET to the Open Library Search API
 *          and parses the JSON response using org.json.
 *
 * API Endpoint : https://openlibrary.org/search.json?q={query}&limit=15
 * No API key required.
 *
 * Threading  : This class performs BLOCKING network I/O.
 *              Callers must invoke searchBooks() on a background
 *              thread (via ExecutorService) — never on the main thread.
 * ============================================================
 */
public class NetworkUtils {

    private static final String BASE_URL = "https://openlibrary.org/search.json";

    /**
     * Searches the Open Library API for books matching the query.
     * MUST be called from a background thread.
     *
     * @param query The search term (title or author).
     * @return A list of Book objects parsed from the API response.
     */
    public static List<Book> searchBooks(String query) {
        List<Book> books = new ArrayList<>();

        try {
            // Build the request URL with URL-encoded query
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String urlString = BASE_URL + "?q=" + encodedQuery + "&limit=15";
            URL url = new URL(urlString);

            // Open connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the response body
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse JSON response
                books = parseJsonResponse(response.toString());
            }

            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return books;
    }

    /**
     * Parses the Open Library JSON response into a list of Book objects.
     *
     * JSON structure:
     * {
     *   "docs": [
     *     {
     *       "title": "...",
     *       "author_name": ["Author1", "Author2"],
     *       "first_publish_year": 2001,
     *       "publisher": ["Publisher1"],
     *       "ia": ["identifier"],
     *       "key": "/works/OL..."
     *     },
     *     ...
     *   ]
     * }
     */
    private static List<Book> parseJsonResponse(String jsonString) {
        List<Book> books = new ArrayList<>();

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray docsArray = jsonObject.getJSONArray("docs");

            for (int i = 0; i < docsArray.length(); i++) {
                JSONObject bookJson = docsArray.getJSONObject(i);

                // Extract title (required field)
                String title = bookJson.optString("title", "Untitled");

                // Extract first author from author_name array
                String author = "Unknown Author";
                if (bookJson.has("author_name")) {
                    JSONArray authors = bookJson.getJSONArray("author_name");
                    if (authors.length() > 0) {
                        author = authors.getString(0);
                    }
                }

                // Extract first publish year
                String publishYear = "N/A";
                if (bookJson.has("first_publish_year")) {
                    publishYear = String.valueOf(bookJson.getInt("first_publish_year"));
                }

                // Extract first publisher from publisher array
                String publisher = "N/A";
                if (bookJson.has("publisher")) {
                    JSONArray publishers = bookJson.getJSONArray("publisher");
                    if (publishers.length() > 0) {
                        publisher = publishers.getString(0);
                    }
                }

                // Extract direct reading URL (if available via Internet Archive or Open Library)
                String readUrl = null;
                if (bookJson.has("ia")) {
                    JSONArray iaArray = bookJson.getJSONArray("ia");
                    if (iaArray.length() > 0) {
                        String iaId = iaArray.getString(0);
                        readUrl = "https://archive.org/details/" + iaId + "/mode/2up";
                    }
                }
                if (readUrl == null && bookJson.has("key")) {
                    readUrl = "https://openlibrary.org" + bookJson.getString("key");
                }

                // Create Book object with readUrl (online result)
                books.add(new Book(title, author, publishYear, publisher, readUrl));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return books;
    }
}
