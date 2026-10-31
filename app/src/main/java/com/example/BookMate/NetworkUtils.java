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
     * @return A list of Book objects, or null if the request failed (e.g. no internet).
     */
    public static List<Book> searchBooks(String query) {
        List<Book> books = null; // stays null if anything goes wrong

        try {
            // Build the request URL with URL-encoded query and English language filter
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String urlString = BASE_URL + "?q=" + encodedQuery + "&language=eng&limit=15";
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
            books = null; // network error
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

                // Check for Project Gutenberg direct ID
                String gutenbergId = null;
                if (bookJson.has("id_project_gutenberg")) {
                    JSONArray pgArray = bookJson.getJSONArray("id_project_gutenberg");
                    if (pgArray.length() > 0) {
                        gutenbergId = pgArray.getString(0);
                    }
                }

                // Extract Internet Archive identifier (preferring English scans)
                String iaId = null;
                if (bookJson.has("ia")) {
                    JSONArray iaArray = bookJson.getJSONArray("ia");
                    for (int j = 0; j < iaArray.length(); j++) {
                        String candidate = iaArray.getString(j);
                        String lower = candidate.toLowerCase();
                        if (!lower.contains("_de") && !lower.contains("_fr") && !lower.contains("_ger")
                                && !lower.contains("_fre") && !lower.contains("_spa") && !lower.contains("_ita")
                                && !lower.contains("_rus")) {
                            iaId = candidate;
                            break;
                        }
                    }
                    if (iaId == null && iaArray.length() > 0) {
                        iaId = iaArray.getString(0);
                    }
                }

                // Choose the best mobile-friendly reading URL
                String readUrl = null;
                if (gutenbergId != null && !gutenbergId.isEmpty()) {
                    // Project Gutenberg clean mobile HTML view
                    readUrl = "https://www.gutenberg.org/ebooks/" + gutenbergId + ".html.images";
                } else if (iaId != null && !iaId.isEmpty()) {
                    // Responsive single-page embedded Internet Archive reader
                    readUrl = "https://archive.org/stream/" + iaId + "?ui=embed#mode/1up";
                } else if (bookJson.has("key")) {
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

    /**
     * Attempts to fetch full public domain text for a book.
     * Strategy:
     * 1. Direct Project Gutenberg link if in readUrl.
     * 2. Gutendex API search for free public domain text.
     * 3. Internet Archive OCR text if ia identifier exists.
     *
     * MUST be called from a background thread.
     */
    public static String fetchPublicDomainText(String title, String author, String readUrl) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }

        // 1. If readUrl is a Project Gutenberg link
        if (readUrl != null && readUrl.contains("gutenberg.org/ebooks/")) {
            try {
                String pgId = readUrl.replaceAll(".*gutenberg\\.org/ebooks/([0-9]+).*", "$1");
                if (pgId.matches("\\d+")) {
                    String text = downloadTextFromUrl("https://www.gutenberg.org/cache/epub/" + pgId + "/pg" + pgId + ".txt", 150000);
                    if (text == null) {
                        text = downloadTextFromUrl("https://www.gutenberg.org/files/" + pgId + "/" + pgId + "-0.txt", 150000);
                    }
                    if (text != null && text.length() > 200) {
                        return cleanBookText(text);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Query Gutendex API for free public domain books (English)
        try {
            String encodedTitle = URLEncoder.encode(title.trim(), "UTF-8");
            String gutendexUrl = "https://gutendex.com/books/?search=" + encodedTitle + "&languages=en";
            String jsonResponse = downloadTextFromUrl(gutendexUrl, 50000);
            if (jsonResponse != null) {
                JSONObject root = new JSONObject(jsonResponse);
                if (root.has("results")) {
                    JSONArray results = root.getJSONArray("results");
                    if (results.length() > 0) {
                        JSONObject firstBook = results.getJSONObject(0);
                        if (firstBook.has("formats")) {
                            JSONObject formats = firstBook.getJSONObject("formats");
                            String textUrl = null;
                            if (formats.has("text/plain; charset=utf-8")) {
                                textUrl = formats.getString("text/plain; charset=utf-8");
                            } else if (formats.has("text/plain; charset=us-ascii")) {
                                textUrl = formats.getString("text/plain; charset=us-ascii");
                            }

                            if (textUrl != null) {
                                String text = downloadTextFromUrl(textUrl, 150000);
                                if (text != null && text.length() > 200) {
                                    return cleanBookText(text);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Query Internet Archive OCR text if ia identifier exists
        if (readUrl != null && readUrl.contains("archive.org/stream/")) {
            try {
                String temp = readUrl.substring(readUrl.indexOf("archive.org/stream/") + 19);
                int qIndex = temp.indexOf("?");
                if (qIndex != -1) temp = temp.substring(0, qIndex);
                int hIndex = temp.indexOf("#");
                if (hIndex != -1) temp = temp.substring(0, hIndex);
                String iaId = temp.trim();

                if (!iaId.isEmpty()) {
                    String iaTextUrl = "https://archive.org/stream/" + iaId + "/" + iaId + "_djvu.txt";
                    String text = downloadTextFromUrl(iaTextUrl, 150000);
                    if (text != null && text.length() > 200) {
                        return cleanBookText(text);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Downloads plain text from a URL following redirects up to 5 times.
     */
    public static String downloadTextFromUrl(String urlString, int maxChars) {
        int redirects = 0;
        while (redirects < 5 && urlString != null) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "BookMateApp/1.0 (Android)");

                int responseCode = connection.getResponseCode();

                // Handle HTTP redirects (301, 302, 303, 307, 308)
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307
                        || responseCode == 308) {
                    String newUrl = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (newUrl != null && !newUrl.isEmpty()) {
                        urlString = newUrl;
                        redirects++;
                        continue;
                    }
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                        if (sb.length() >= maxChars) {
                            break;
                        }
                    }
                    reader.close();
                    return sb.toString();
                }

                return null;
            } catch (Exception e) {
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    /**
     * Cleans up Gutenberg/Archive boilerplate headers and footers.
     */
    public static String cleanBookText(String rawText) {
        if (rawText == null) return null;
        String text = rawText;

        // Skip Gutenberg headers
        int startIndex = text.indexOf("*** START OF THE PROJECT GUTENBERG");
        if (startIndex == -1) {
            startIndex = text.indexOf("*** START OF THIS PROJECT GUTENBERG");
        }
        if (startIndex != -1) {
            int lineEnd = text.indexOf("\n", startIndex);
            if (lineEnd != -1) {
                text = text.substring(lineEnd + 1);
            }
        }

        // Strip Gutenberg footer
        int endIndex = text.indexOf("*** END OF THE PROJECT GUTENBERG");
        if (endIndex == -1) {
            endIndex = text.indexOf("*** END OF THIS PROJECT GUTENBERG");
        }
        if (endIndex != -1) {
            text = text.substring(0, endIndex);
        }

        // Limit size to ~120,000 characters to keep TextView rendering blazing fast & responsive
        if (text.length() > 120000) {
            text = text.substring(0, 120000) + "\n\n[... End of preview section. Switch to Web View for complete edition ...]";
        }

        return text.trim();
    }
}
