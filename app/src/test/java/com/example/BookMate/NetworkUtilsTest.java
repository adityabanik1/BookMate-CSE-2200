package com.example.BookMate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkUtilsTest {
    @Test public void fallbackRejectsSameTitleByDifferentAuthor() throws Exception {
        assertFalse(NetworkUtils.matchesBook("The Art of War", "Sun Tzu", "The Art of War",
                new org.json.JSONArray("[\"Niccolo Machiavelli\"]"), false));
        assertTrue(NetworkUtils.matchesBook("The Art of War", "Sun Tzu", "The Art of War",
                new org.json.JSONArray("[\"Sun Tzu\"]"), false));
    }

    @Test public void fallbackChecksGutendexAuthorsAndNormalizedTitle() throws Exception {
        org.json.JSONArray authors = new org.json.JSONArray("[{\"name\":\"Austen, Jane\"}]");
        assertTrue(NetworkUtils.matchesBook("Pride and Prejudice", "Jane Austen",
                "PRIDE AND PREJUDICE!", authors, true));
        assertFalse(NetworkUtils.matchesBook("Pride and Prejudice", "Jane Austen",
                "Pride and Prejudice Study Guide", authors, true));
        assertFalse(NetworkUtils.matchesBook("Pride and Prejudice", "Unknown Author",
                "Pride and Prejudice", authors, true));
        assertFalse(NetworkUtils.matchesBook("Pride and Prejudice", "Jane Austen",
                "Pride and Prejudice", null, true));
    }

    @Test
    public void searchPrefersDesignatedLendingEditionOverFirstScan() throws Exception {
        java.lang.reflect.Method parser = NetworkUtils.class.getDeclaredMethod("parseJsonResponse", String.class);
        parser.setAccessible(true);
        String json = "{\"docs\":[{\"title\":\"A book\",\"ia\":[\"preview_scan\"],"
                + "\"lending_identifier\":\"lendable_edition\"}]}";
        java.util.List<?> results = (java.util.List<?>) parser.invoke(null, json);
        assertEquals("https://archive.org/details/lendable_edition", ((Book) results.get(0)).getReadUrl());
    }

    @Test
    public void gutenbergRemainsPreferredForOpenFullText() throws Exception {
        java.lang.reflect.Method parser = NetworkUtils.class.getDeclaredMethod("parseJsonResponse", String.class);
        parser.setAccessible(true);
        String json = "{\"docs\":[{\"title\":\"A classic\",\"lending_identifier\":\"a_scan\","
                + "\"id_project_gutenberg\":[\"1342\"]}]}";
        java.util.List<?> results = (java.util.List<?>) parser.invoke(null, json);
        assertEquals("https://www.gutenberg.org/ebooks/1342.html.images", ((Book) results.get(0)).getReadUrl());
    }

    @Test
    public void cleanupPreservesLongBookAndParagraphs() {
        StringBuilder body = new StringBuilder("First chapter\n\n");
        for (int i = 0; i < 30000; i++) body.append("Middle passage.\n");
        body.append("\nFinal chapter.");
        String wrapped = "License\r\n*** START OF THE PROJECT GUTENBERG EBOOK ***\r\n"
                + body.toString().replace("\n", "\r\n")
                + "\r\n*** END OF THE PROJECT GUTENBERG EBOOK ***\r\nLicense";
        assertEquals(body.toString(), NetworkUtils.cleanBookText(wrapped));
        assertNull(NetworkUtils.cleanBookText(null));
    }

    @Test
    public void downloadReturnsWholeUtf8ResponseOrFailsWithoutTruncation() throws Exception {
        String book = "First\n\nMiddle: caf\u00e9 \uD83D\uDE00\n\nLast";
        ServerSocket server = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(5000);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<?> served = worker.submit(() -> {
            for (int i = 0; i < 5; i++) {
                try (Socket client = server.accept()) {
                    client.setSoTimeout(5000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(
                            client.getInputStream(), StandardCharsets.US_ASCII));
                    String request = input.readLine();
                    String header;
                    while ((header = input.readLine()) != null && !header.isEmpty()) { }
                    boolean redirect = request.contains("/redirect ");
                    boolean html = request.contains("/html ");
                    byte[] body = (html ? "<html>Sign in required</html>" : book)
                            .getBytes(StandardCharsets.UTF_8);
                    String response = redirect
                            ? "HTTP/1.1 302 Found\r\nLocation: /book\r\nContent-Length: 0\r\n"
                            : "HTTP/1.1 200 OK\r\nContent-Type: "
                            + (html ? "text/html" : "text/plain; charset=utf-8")
                            + "\r\nContent-Length: " + body.length + "\r\n";
                    OutputStream output = client.getOutputStream();
                    output.write((response + "Connection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    if (!redirect) output.write(body);
                    output.flush();
                }
            }
            return null;
        });
        try {
            String base = "http://127.0.0.1:" + server.getLocalPort();
            assertEquals(book, NetworkUtils.downloadTextFromUrl(base + "/book", book.length()));
            assertNull(NetworkUtils.downloadTextFromUrl(base + "/book", book.length() - 1));
            assertNull(NetworkUtils.downloadTextFromUrl(base + "/html", 1000));
            assertEquals(book, NetworkUtils.downloadTextFromUrl(base + "/redirect", 1000));
            served.get(5, TimeUnit.SECONDS);
        } finally {
            server.close();
            worker.shutdownNow();
        }
    }

    @Test
    public void authorNameFormatsCorrectlyFromGutendex() {
        assertEquals("Jane Austen", NetworkUtils.formatAuthorName("Austen, Jane"));
        assertEquals("Arthur Conan Doyle", NetworkUtils.formatAuthorName("Doyle, Arthur Conan"));
        assertEquals("Plato", NetworkUtils.formatAuthorName("Plato"));
        assertEquals("Unknown Author", NetworkUtils.formatAuthorName(null));
        assertEquals("Unknown Author", NetworkUtils.formatAuthorName("   "));
    }

    @Test
    public void parseGutendexResponseConstructsProperBooks() {
        String json = "{\"results\":[{"
                + "\"id\":1342,"
                + "\"title\":\"Pride and Prejudice\","
                + "\"authors\":[{\"name\":\"Austen, Jane\",\"birth_year\":1775,\"death_year\":1817}]"
                + "}]}";
        List<Book> books = NetworkUtils.parseGutendexJsonResponse(json);
        assertNotNull(books);
        assertEquals(1, books.size());
        Book book = books.get(0);
        assertEquals("Pride and Prejudice", book.getTitle());
        assertEquals("Jane Austen", book.getAuthor());
        assertEquals("Project Gutenberg", book.getPublisher());
        assertEquals("https://www.gutenberg.org/ebooks/1342.html.images", book.getReadUrl());
    }

    @Test
    public void providerConstantsAreDefined() {
        assertEquals("all", NetworkUtils.PROVIDER_ALL);
        assertEquals("gutenberg", NetworkUtils.PROVIDER_GUTENBERG);
        assertEquals("openlibrary", NetworkUtils.PROVIDER_OPEN_LIBRARY);
    }
}
