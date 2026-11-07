package com.example.BookMate;

import java.net.URI;

/** Validated edition identity; never use titles to select or return a loan. */
final class LoanTarget {
    final String archiveId;
    final String editionId;

    private LoanTarget(String archiveId, String editionId) {
        this.archiveId = archiveId;
        this.editionId = editionId;
    }

    static LoanTarget fromUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!isProviderUrl(value)) return null;
            String[] path = uri.getPath().split("/");
            if (path.length < 3) return null;
            if ("archive.org".equalsIgnoreCase(uri.getHost())
                    && ("details".equals(path[1]) || "stream".equals(path[1])
                    || "embed".equals(path[1]))
                    && path[2].matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) {
                return new LoanTarget(path[2], null);
            }
            if ("openlibrary.org".equalsIgnoreCase(uri.getHost())
                    && "books".equals(path[1]) && path[2].matches("OL[0-9]+M")) {
                return new LoanTarget(null, path[2]);
            }
        } catch (Exception ignored) { }
        return null;
    }

    static boolean isProviderUrl(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ("openlibrary.org".equalsIgnoreCase(uri.getHost())
                    || "archive.org".equalsIgnoreCase(uri.getHost()));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isLoansLandingPage(String value) {
        if (!isProviderUrl(value)) return false;
        try {
            URI uri = new URI(value);
            return "openlibrary.org".equalsIgnoreCase(uri.getHost())
                    && "/account/loans".equals(uri.getPath());
        } catch (Exception ignored) { return false; }
    }

    static boolean isSignInPage(String value) {
        if (!isProviderUrl(value)) return false;
        try {
            String path = new URI(value).getPath();
            return path != null && (path.startsWith("/account/login")
                    || path.startsWith("/account/create") || path.startsWith("/account/verify")
                    || path.startsWith("/verify_human"));
        } catch (Exception ignored) { return false; }
    }

    static boolean isGoogleSignInUrl(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && "accounts.google.com".equalsIgnoreCase(uri.getHost());
        } catch (Exception ignored) { return false; }
    }

    String actionUrl(boolean returning) {
        String path = archiveId != null ? "/borrow/ia/" + archiveId
                : "/books/" + editionId + "/x/borrow";
        return "https://openlibrary.org" + path + "?action=" + (returning ? "return" : "borrow");
    }

    String bookUrl() {
        return archiveId != null ? "https://archive.org/details/" + archiveId
                : "https://openlibrary.org/books/" + editionId;
    }

    boolean matches(String ocaid, String bookKey) {
        return archiveId != null ? archiveId.equals(ocaid)
                : ("/books/" + editionId).equals(bookKey);
    }
}
