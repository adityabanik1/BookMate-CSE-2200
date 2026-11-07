package com.example.BookMate;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Read-only verification. Account-changing requests stay in the provider WebView. */
final class LoanService {
    static final String LOANS_URL = "https://openlibrary.org/account/loans.json";
    static final String LOGIN_URL = "https://openlibrary.org/account/login?redirect=%2Faccount%2Floans";
    enum State { SIGN_IN_REQUIRED, ACTIVE, NONE, UNKNOWN, TIMED_OUT, NETWORK_ERROR, BLOCKED, RATE_LIMITED, SERVER_ERROR }
    enum Availability { OPEN, BORROWABLE, UNAVAILABLE, UNKNOWN }

    static State check(LoanTarget target, String cookies) {
        if (cookies == null || cookies.isEmpty()) return State.SIGN_IN_REQUIRED;
        HttpURLConnection connection = null;
        try {
            // Never follow a redirect with session cookies to another host.
            connection = (HttpURLConnection) new URL(LOANS_URL).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Cookie", cookies);
            connection.setRequestProperty("User-Agent", "BookMate/1.0 (Android; personal reading app)");
            int code = connection.getResponseCode();
            if (code == 401) return State.SIGN_IN_REQUIRED;
            if (code == 403) return State.BLOCKED;
            if (code == 429) return State.RATE_LIMITED;
            if (code >= 500) return State.SERVER_ERROR;
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                if (location != null && new URL(new URL(LOANS_URL), location).getPath()
                        .startsWith("/account/login")) return State.SIGN_IN_REQUIRED;
            }
            if (code != 200 || !isJson(connection.getContentType())) return State.UNKNOWN;
            return parseLoans(readBody(connection), target);
        } catch (java.net.SocketTimeoutException ignored) {
            return State.TIMED_OUT;
        } catch (java.io.IOException ignored) {
            return State.NETWORK_ERROR;
        } catch (Exception ignored) {
            // Never log provider bodies, cookies, signed URLs, or account details.
            return State.UNKNOWN;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Availability availability(LoanTarget target) {
        if (target.archiveId == null) return Availability.UNKNOWN;
        HttpURLConnection connection = null;
        try {
            // Public availability check, with no credentials or third-party service.
            URL url = new URL("https://archive.org/services/loans/loan/?action=availability&identifier="
                    + target.archiveId);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "BookMate/1.0 (Android; personal reading app)");
            if (connection.getResponseCode() != 200 || !isJson(connection.getContentType())) {
                return Availability.UNKNOWN;
            }
            return parseAvailability(readBody(connection));
        } catch (Exception ignored) {
            return Availability.UNKNOWN;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static State parseLoans(String body, LoanTarget target) {
        try {
            JSONObject root = new JSONObject(body);
            // Only an explicit, well-formed authenticated list can establish absence.
            if (root.has("error") || Boolean.FALSE.equals(root.opt("success"))) return State.UNKNOWN;
            JSONArray loans = root.getJSONArray("loans");
            boolean unknownEntry = false;
            for (int i = 0; i < loans.length(); i++) {
                JSONObject loan = loans.optJSONObject(i);
                if (loan == null) { unknownEntry = true; continue; }
                String ocaid = loan.opt("ocaid") instanceof String ? loan.getString("ocaid") : "";
                String bookKey = loan.opt("book") instanceof String ? loan.getString("book") : "";
                if (target.matches(ocaid, bookKey)) return State.ACTIVE;
                if (target.archiveId != null ? !ocaid.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")
                        : !bookKey.matches("/books/(OL[0-9]+M|ia:[A-Za-z0-9][A-Za-z0-9._-]{0,199})")) {
                    unknownEntry = true;
                }
            }
            return unknownEntry ? State.UNKNOWN : State.NONE;
        } catch (Exception ignored) {
            return State.UNKNOWN;
        }
    }

    static Availability parseAvailability(String body) {
        try {
            JSONObject root = new JSONObject(body);
            if (root.has("error")) return Availability.UNKNOWN;
            JSONObject status = root.getJSONObject("lending_status");
            if (Boolean.TRUE.equals(status.opt("is_readable"))) return Availability.OPEN;
            if (Boolean.TRUE.equals(status.opt("available_to_browse"))
                    || Boolean.TRUE.equals(status.opt("available_to_borrow"))) return Availability.BORROWABLE;
            if (Boolean.FALSE.equals(status.opt("is_lendable"))
                    || (Boolean.FALSE.equals(status.opt("available_to_browse"))
                    && Boolean.FALSE.equals(status.opt("available_to_borrow")))) return Availability.UNAVAILABLE;
        } catch (Exception ignored) { }
        return Availability.UNKNOWN;
    }

    private static boolean isJson(String type) {
        return type != null && type.toLowerCase(java.util.Locale.ROOT).contains("application/json");
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (System.nanoTime() > deadline) throw new java.net.SocketTimeoutException();
                if (output.size() + count > 1000000) throw new java.io.IOException("Response too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
