package com.example.BookMate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ============================================================
 * NetworkUtils.java — HTTP Networking + Multi-Provider Search
 * ============================================================
 * Supports multiple public, free, keyless book providers:
 * 1. Project Gutenberg (via Gutendex REST API):
 *    - Keyless: https://gutendex.com/books/?search={query}
 *    - 70,000+ public-domain books with full-text reading and TTS.
 * 2. Open Library Search API:
 *    - Keyless: https://openlibrary.org/search.json?q={query}&limit=15
 *    - Worldwide catalog metadata and digital library loan links.
 * 3. Free Dictionary API:
 *    - Keyless: https://api.dictionaryapi.dev/api/v2/entries/en/{word}
 *
 * Threading: This class performs BLOCKING network I/O.
 * Callers must invoke searchBooks() / getDefinition() on a
 * background thread (via ExecutorService) — never on the main thread.
 * ============================================================
 */
public class NetworkUtils {

    public static final String PROVIDER_ALL = "all";
    public static final String PROVIDER_GUTENBERG = "gutenberg";
    public static final String PROVIDER_OPEN_LIBRARY = "openlibrary";

    private static final String BASE_URL = "https://openlibrary.org/search.json";
    private static final String GUTENDEX_BASE_URL = "https://gutendex.com/books/";
    private static final String DICTIONARY_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/";
    private static final int MAX_BOOK_CHARS = 10000000;

    /**
     * Looks up a word in the Free Dictionary API.
     * Returns e.g. "(noun) A large book...", or null if the word isn't
     * found or there is no internet. MUST be called from a background thread.
     */
    public static String getDefinition(String word) {
        try {
            String url = DICTIONARY_URL + URLEncoder.encode(word, "UTF-8");
            String json = downloadTextFromUrl(url, 500000); // common words have long answers
            if (json == null) return null; // e.g. HTTP 404 = word not in the dictionary

            JSONArray entries = new JSONArray(json);
            JSONObject entry = entries.getJSONObject(0);
            JSONObject meaning = entry.getJSONArray("meanings").getJSONObject(0);
            JSONObject first = meaning.getJSONArray("definitions").getJSONObject(0);
            return "(" + meaning.getString("partOfSpeech") + ") " + first.getString("definition");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Searches for books matching the query across the specified provider.
     *
     * @param query Search query term.
     * @param provider PROVIDER_ALL, PROVIDER_GUTENBERG, or PROVIDER_OPEN_LIBRARY.
     * @return List of matching books, or null if network error.
     */
    public static List<Book> searchBooks(String query, String provider) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (PROVIDER_GUTENBERG.equalsIgnoreCase(provider)) {
            return searchGutenberg(query);
        } else if (PROVIDER_OPEN_LIBRARY.equalsIgnoreCase(provider)) {
            return searchOpenLibrary(query);
        }
        return searchAllProviders(query);
    }

    /**
     * Convenience search method — defaults to searching all providers.
     */
    public static List<Book> searchBooks(String query) {
        return searchBooks(query, PROVIDER_ALL);
    }

    /**
     * Searches Project Gutenberg via Open Library's dedicated Gutenberg catalog,
     * with fallback to Gutendex REST API.
     * MUST be called from a background thread.
     */
    public static List<Book> searchGutenberg(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Primary: Search Open Library's Gutenberg index (fast & reliable)
        List<Book> olGutenberg = searchOpenLibraryGutenberg(query);
        if (olGutenberg != null && !olGutenberg.isEmpty()) {
            return olGutenberg;
        }

        // 2. Secondary / Fallback: Gutendex API with 3.5s timeout
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
            String urlString = GUTENDEX_BASE_URL + "?search=" + encodedQuery + "&languages=en";
            String json = fetchJsonFromUrl(urlString, 3500);
            if (json != null) {
                List<Book> gutendexResults = parseGutendexJsonResponse(json);
                if (gutendexResults != null && !gutendexResults.isEmpty()) {
                    return gutendexResults;
                }
            }
        } catch (Exception e) {
            // Gutendex unavailable or timed out
        }

        return olGutenberg != null ? olGutenberg : new ArrayList<>();
    }

    /**
     * Queries Open Library specifically for Project Gutenberg ebooks.
     */
    public static List<Book> searchOpenLibraryGutenberg(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
            String urlString = BASE_URL + "?q=" + encodedQuery + "+id_project_gutenberg:*&language=eng&limit=15"
                    + "&fields=key,title,author_name,first_publish_year,publisher,id_project_gutenberg,cover_i";
            String json = fetchJsonFromUrl(urlString, 8000);
            if (json != null) {
                return parseJsonResponse(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Parses Gutendex JSON response into Book objects.
     */
    static List<Book> parseGutendexJsonResponse(String jsonString) {
        List<Book> books = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            if (!jsonObject.has("results")) return books;
            JSONArray results = jsonObject.getJSONArray("results");
            int count = Math.min(results.length(), 15);
            for (int i = 0; i < count; i++) {
                JSONObject item = results.getJSONObject(i);
                String title = item.optString("title", "Untitled");

                String author = "Unknown Author";
                if (item.has("authors")) {
                    JSONArray authors = item.getJSONArray("authors");
                    if (authors.length() > 0) {
                        JSONObject firstAuthor = authors.getJSONObject(0);
                        author = formatAuthorName(firstAuthor.optString("name", "Unknown Author"));
                    }
                }

                int id = item.optInt("id", -1);
                String publishYear = "Public Domain";
                String publisher = "Project Gutenberg";
                String readUrl = id > 0 ? "https://www.gutenberg.org/ebooks/" + id + ".html.images" : null;

                books.add(new Book(title, author, publishYear, publisher, readUrl));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return books;
    }

    /**
     * Formats Gutendex author names from "Lastname, Firstname" to "Firstname Lastname".
     */
    public static String formatAuthorName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Unknown Author";
        String trimmed = raw.trim();
        if (trimmed.contains(",")) {
            String[] parts = trimmed.split(",", 2);
            return parts[1].trim() + " " + parts[0].trim();
        }
        return trimmed;
    }

    /**
     * Searches Open Library API.
     * MUST be called from a background thread.
     */
    public static List<Book> searchOpenLibrary(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
            String urlString = BASE_URL + "?q=" + encodedQuery + "&language=eng&limit=15"
                    + "&fields=key,title,author_name,first_publish_year,publisher,ia,lending_identifier,id_project_gutenberg,cover_i";
            String json = fetchJsonFromUrl(urlString, 10000);
            if (json != null) {
                return parseJsonResponse(json);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Searches both Gutenberg and Open Library, merging results seamlessly.
     */
    public static List<Book> searchAllProviders(String query) {
        List<Book> gutenbergResults = searchGutenberg(query);
        List<Book> openLibResults = searchOpenLibrary(query);

        if (gutenbergResults == null && openLibResults == null) {
            return null; // Both failed (likely no internet)
        }

        List<Book> combined = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();

        if (gutenbergResults != null) {
            for (Book b : gutenbergResults) {
                combined.add(b);
                seenTitles.add(normalizeTitle(b.getTitle()));
            }
        }

        if (openLibResults != null) {
            for (Book b : openLibResults) {
                String norm = normalizeTitle(b.getTitle());
                if (!seenTitles.contains(norm)) {
                    combined.add(b);
                    seenTitles.add(norm);
                }
            }
        }

        return combined;
    }

    private static String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String fetchJsonFromUrl(String urlString, int timeoutMs) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("User-Agent", "BookMateApp/1.0 (Android)");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            }
        } catch (Exception e) {
            // Network failure or timeout
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    /**
     * Parses the Open Library JSON response into a list of Book objects.
     */
    private static List<Book> parseJsonResponse(String jsonString) {
        List<Book> books = new ArrayList<>();

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray docsArray = jsonObject.getJSONArray("docs");

            for (int i = 0; i < docsArray.length(); i++) {
                JSONObject bookJson = docsArray.getJSONObject(i);

                String title = bookJson.optString("title", "Untitled");

                String author = "Unknown Author";
                if (bookJson.has("author_name")) {
                    JSONArray authors = bookJson.getJSONArray("author_name");
                    if (authors.length() > 0) {
                        author = authors.getString(0);
                    }
                }

                String publishYear = "N/A";
                if (bookJson.has("first_publish_year")) {
                    publishYear = String.valueOf(bookJson.getInt("first_publish_year"));
                }

                String publisher = "N/A";
                if (bookJson.has("publisher")) {
                    JSONArray publishers = bookJson.getJSONArray("publisher");
                    if (publishers.length() > 0) {
                        publisher = publishers.getString(0);
                    }
                }

                String gutenbergId = null;
                if (bookJson.has("id_project_gutenberg")) {
                    JSONArray pgArray = bookJson.getJSONArray("id_project_gutenberg");
                    if (pgArray.length() > 0) {
                        gutenbergId = pgArray.getString(0);
                    }
                }

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

                String lendingId = bookJson.optString("lending_identifier", "");
                if (lendingId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) iaId = lendingId;
                String readUrl = null;
                if (gutenbergId != null && !gutenbergId.isEmpty()) {
                    readUrl = "https://www.gutenberg.org/ebooks/" + gutenbergId + ".html.images";
                } else if (iaId != null && !iaId.isEmpty()) {
                    readUrl = "https://archive.org/details/" + iaId;
                } else if (bookJson.has("key")) {
                    readUrl = "https://openlibrary.org" + bookJson.getString("key");
                }

                books.add(new Book(title, author, publishYear, publisher, readUrl));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return books;
    }

    /**
     * Attempts to fetch full public domain text for a book.
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
                    String text = downloadTextFromUrl("https://www.gutenberg.org/cache/epub/" + pgId + "/pg" + pgId + ".txt", MAX_BOOK_CHARS);
                    if (text == null) {
                        text = downloadTextFromUrl("https://www.gutenberg.org/files/" + pgId + "/" + pgId + "-0.txt", MAX_BOOK_CHARS);
                    }
                    if (text != null && text.length() > 200) {
                        return cleanBookText(text);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Query Open Library to check if a Project Gutenberg ID exists for this book
        try {
            String encodedTitle = URLEncoder.encode(title.trim(), "UTF-8");
            String olUrl = BASE_URL + "?q=" + encodedTitle + "+id_project_gutenberg:*&limit=1&fields=id_project_gutenberg";
            String jsonResponse = downloadTextFromUrl(olUrl, 50000);
            if (jsonResponse != null) {
                JSONObject root = new JSONObject(jsonResponse);
                if (root.has("docs")) {
                    JSONArray docs = root.getJSONArray("docs");
                    if (docs.length() > 0) {
                        JSONObject doc = docs.getJSONObject(0);
                        if (doc.has("id_project_gutenberg")) {
                            JSONArray pgArray = doc.getJSONArray("id_project_gutenberg");
                            if (pgArray.length() > 0) {
                                String pgId = pgArray.getString(0);
                                String text = downloadTextFromUrl("https://www.gutenberg.org/cache/epub/" + pgId + "/pg" + pgId + ".txt", MAX_BOOK_CHARS);
                                if (text == null) {
                                    text = downloadTextFromUrl("https://www.gutenberg.org/files/" + pgId + "/" + pgId + "-0.txt", MAX_BOOK_CHARS);
                                }
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

        // 3. Query Gutendex API for free public domain books (English)
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
                                String text = downloadTextFromUrl(textUrl, MAX_BOOK_CHARS);
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
        if (readUrl != null && (readUrl.contains("archive.org/stream/")
                || readUrl.contains("archive.org/details/"))) {
            try {
                String[] path = new URL(readUrl).getPath().split("/");
                String iaId = path.length > 2 ? path[2] : "";

                if (!iaId.isEmpty()) {
                    String iaTextUrl = "https://archive.org/stream/" + iaId + "/" + iaId + "_djvu.txt";
                    String text = downloadTextFromUrl(iaTextUrl, MAX_BOOK_CHARS);
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
     * Downloads a complete text response.
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

                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307
                        || responseCode == 308) {
                    String newUrl = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (newUrl != null && !newUrl.isEmpty()) {
                        urlString = new URL(url, newUrl).toString();
                        redirects++;
                        continue;
                    }
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String contentType = connection.getContentType();
                    if (contentType != null && contentType.toLowerCase(Locale.ROOT)
                            .contains("text/html")) return null;
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    char[] buffer = new char[8192];
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        if (sb.length() + count > maxChars) {
                            reader.close();
                            return null;
                        }
                        sb.append(buffer, 0, count);
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
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n');

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

        int endIndex = text.indexOf("*** END OF THE PROJECT GUTENBERG");
        if (endIndex == -1) {
            endIndex = text.indexOf("*** END OF THIS PROJECT GUTENBERG");
        }
        if (endIndex != -1) {
            text = text.substring(0, endIndex);
        }

        return text.trim();
    }
}
