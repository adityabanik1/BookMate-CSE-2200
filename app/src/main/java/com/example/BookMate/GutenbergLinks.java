package com.example.BookMate;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GutenbergLinks {
    /** The catalog lists actual formats, including items without HTML/text editions. */
    static String catalogUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || !("www.gutenberg.org".equalsIgnoreCase(uri.getHost())
                    || "gutenberg.org".equalsIgnoreCase(uri.getHost()))) return value;
            Matcher match = Pattern.compile("^/ebooks/([0-9]+)(?:\\.html(?:\\.images)?)?/?$")
                    .matcher(uri.getPath());
            if (match.matches()) return "https://www.gutenberg.org/ebooks/" + match.group(1);
        } catch (Exception ignored) { }
        return value;
    }
}
