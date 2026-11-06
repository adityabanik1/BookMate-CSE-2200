package com.example.BookMate;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================
 * GeminiSummaryService.java — Generative AI Book Summaries
 * ============================================================
 * Generates concise, engaging, spoiler-free summaries of any book
 * using Google's Gemini 2.5 Flash API (gemini-2.5-flash).
 *
 * Features:
 * - Primary: Google Gemini 2.5 Flash API.
 * - Key Security: Loaded from BuildConfig.GEMINI_API_KEY (secrets.properties)
 *   or private SharedPreferences.
 * - Offline/Failover: Automatically falls back to public Wikipedia / Open
 *   Library synopsis if the Gemini API key is missing or offline.
 * - Caching: Summaries are persisted in private SharedPreferences
 *   so repeated views are instant and consume zero API quota.
 * ============================================================
 */
public class GeminiSummaryService {

    private static final String TAG = "GeminiSummaryService";
    private static final String PREFS_NAME = "bookmate_ai_summaries";
    private static final String PREFS_API_KEY = "gemini_api_key_override";

    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=";

    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SummaryCallback {
        void onSuccess(String summary, boolean isAiGenerated);
        void onError(String errorMessage);
    }

    /**
     * Retrieves an AI summary for a book, serving from cache if available,
     * otherwise querying Gemini 2.5 Flash or falling back gracefully.
     */
    public static void getBookSummary(Context context, String title, String author, boolean forceRefresh, SummaryCallback callback) {
        if (title == null || title.trim().isEmpty()) {
            callback.onError("Book title is required.");
            return;
        }

        String cacheKey = buildCacheKey(title, author);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Check local cache first unless forceRefresh is true
        if (!forceRefresh && prefs.contains(cacheKey)) {
            String cachedSummary = prefs.getString(cacheKey, null);
            if (cachedSummary != null && !cachedSummary.isEmpty()) {
                callback.onSuccess(cachedSummary, true);
                return;
            }
        }

        String apiKey = getEffectiveApiKey(context);

        executor.execute(() -> {
            try {
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    // ── Primary: Gemini 2.5 Flash Generation ────────────
                    String aiSummary = callGeminiApi(title, author, apiKey);
                    if (aiSummary != null && !aiSummary.trim().isEmpty()) {
                        prefs.edit().putString(cacheKey, aiSummary).apply();
                        mainHandler.post(() -> callback.onSuccess(aiSummary, true));
                        return;
                    }
                }

                // ── Fallback: Wikipedia / Public Knowledge Synopsis ─────
                String publicSynopsis = fetchPublicSynopsis(title);
                if (publicSynopsis != null && !publicSynopsis.trim().isEmpty()) {
                    prefs.edit().putString(cacheKey, publicSynopsis).apply();
                    mainHandler.post(() -> callback.onSuccess(publicSynopsis, false));
                    return;
                }

                // Default graceful synopsis if offline or metadata only
                String defaultSynopsis = buildFallbackLiterarySynopsis(title, author);
                mainHandler.post(() -> callback.onSuccess(defaultSynopsis, false));

            } catch (Exception e) {
                Log.w(TAG, "Summary generation error: " + e.getMessage());
                String fallback = buildFallbackLiterarySynopsis(title, author);
                mainHandler.post(() -> callback.onSuccess(fallback, false));
            }
        });
    }

    /**
     * Calls Gemini 2.5 Flash generateContent API.
     */
    private static String callGeminiApi(String title, String author, String apiKey) throws Exception {
        URL url = new URL(GEMINI_API_BASE + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);

        String prompt = "You are an insightful literary guide for BookMate. " +
                "Write a captivating, spoiler-free 3 to 4 sentence overview of the book \"" + title + "\"" +
                (author != null && !author.isEmpty() ? " by " + author : "") + ". " +
                "Highlight the central premise, major themes, and why it is a compelling read.";

        // Construct JSON Payload
        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);

        JSONArray partsArray = new JSONArray();
        partsArray.put(textPart);

        JSONObject contentObj = new JSONObject();
        contentObj.put("parts", partsArray);

        JSONArray contentsArray = new JSONArray();
        contentsArray.put(contentObj);

        JSONObject requestJson = new JSONObject();
        requestJson.put("contents", contentsArray);

        byte[] outputBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(outputBytes);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String responseStr = readStream(conn.getInputStream());
            JSONObject responseJson = new JSONObject(responseStr);
            JSONArray candidates = responseJson.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject firstCandidate = candidates.getJSONObject(0);
                JSONObject content = firstCandidate.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null && parts.length() > 0) {
                        return parts.getJSONObject(0).optString("text", "").trim();
                    }
                }
            }
        } else {
            String errResponse = readStream(conn.getErrorStream());
            Log.w(TAG, "Gemini API error (" + responseCode + "): " + errResponse);
        }

        return null;
    }

    /**
     * Fallback synopsis from public encyclopedic endpoint (Wikipedia REST API).
     */
    private static String fetchPublicSynopsis(String title) {
        try {
            // Clean title for search (e.g. remove edition subtitles)
            String cleanTitle = title.split("(?i)\\b(volume|vol|part|book)\\b")[0]
                    .replaceAll("[,:;-].*$", "")
                    .trim();
            String encoded = URLEncoder.encode(cleanTitle, "UTF-8");
            URL url = new URL("https://en.wikipedia.org/api/rest_v1/page/summary/" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "BookMate-Android-App/1.0");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String jsonStr = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(jsonStr);
                String extract = json.optString("extract", "");
                if (extract.length() > 50) {
                    return extract.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String buildFallbackLiterarySynopsis(String title, String author) {
        return "Discover \"" + title + "\"" + (author != null && !author.isEmpty() ? " by " + author : "") +
                ", an acclaimed work featuring rich character development and timeless themes. " +
                "Start reading to explore its narrative and historical significance.";
    }

    public static String getEffectiveApiKey(Context context) {
        // Priority 1: User runtime override in preferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String overrideKey = prefs.getString(PREFS_API_KEY, null);
        if (overrideKey != null && !overrideKey.trim().isEmpty()) {
            return overrideKey.trim();
        }

        // Priority 2: BuildConfig GEMINI_API_KEY loaded from secrets.properties
        try {
            if (BuildConfig.GEMINI_API_KEY != null && !BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
                return BuildConfig.GEMINI_API_KEY.trim();
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    public static void setApiKeyOverride(Context context, String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            prefs.edit().remove(PREFS_API_KEY).apply();
        } else {
            prefs.edit().putString(PREFS_API_KEY, apiKey.trim()).apply();
        }
    }

    private static String buildCacheKey(String title, String author) {
        String cleanTitle = title.toLowerCase().replaceAll("[^a-z0-9]", "_");
        String cleanAuthor = author != null ? author.toLowerCase().replaceAll("[^a-z0-9]", "_") : "";
        return "ai_summary_" + cleanTitle + "_" + cleanAuthor;
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
