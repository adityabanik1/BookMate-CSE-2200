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
 * GeminiSummaryService.java — Generative AI Book Summaries & Chat
 * Generates concise, engaging, spoiler-free summaries and answers
 * interactive reader questions about any book (genre, page count,
 * characters, plot points) using Google's Gemini Flash API.
 *
 * Features:
 * - Primary: Google Gemini Flash API with automatic resilient fallback
 *   across candidate models (gemini-3.5-flash, gemini-3.5-flash-lite,
 *   gemini-3.8-flash, and legacy gemini-2.5-flash).
 * - Interactive Book Chat: Answers custom questions, character inquiries,
 *   genre classifications, and page count queries.
 * - Key Security: Loaded from BuildConfig.GEMINI_API_KEY (secrets.properties)
 *   or private SharedPreferences.
 * - Offline/Failover: Automatically falls back gracefully when offline or
 *   when API keys are unconfigured.
 * - Caching: Summaries are persisted in private SharedPreferences.
 */
public class GeminiSummaryService {

    private static final String TAG = "GeminiSummaryService";
    private static final String PREFS_NAME = "bookmate_ai_summaries";
    private static final String PREFS_API_KEY = "gemini_api_key_override";

    // Candidate models in preference order:
    // 1) gemini-3.5-flash (primary active production model for current keys)
    // 2) gemini-3.5-flash-lite (high-speed, high-availability fallback)
    // 3) gemini-3.8-flash (advanced model; handles spikes via fallback)
    // 4) gemini-2.5-flash (retained for legacy accounts)
    private static final String[] CANDIDATE_MODELS = {
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-2.5-flash"
    };

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SummaryCallback {
        void onSuccess(String summary, boolean isAiGenerated);
        void onError(String errorMessage);
    }

    public interface ChatCallback {
        void onSuccess(String answer);
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
                callback.onSuccess(cachedSummary, prefs.getBoolean(cacheKey + "_ai_generated", false));
                return;
            }
        }

        String apiKey = getEffectiveApiKey(context);

        executor.execute(() -> {
            try {
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    // Primary: Gemini 2.5 Flash Generation
                    String aiSummary = callGeminiApi(title, author, apiKey);
                    if (aiSummary != null && !aiSummary.trim().isEmpty()) {
                        prefs.edit().putString(cacheKey, aiSummary)
                                .putBoolean(cacheKey + "_ai_generated", true).apply();
                        mainHandler.post(() -> callback.onSuccess(aiSummary, true));
                        return;
                    }
                }

                // Fallback: Wikipedia / Public Knowledge Synopsis
                String publicSynopsis = fetchPublicSynopsis(title);
                if (publicSynopsis != null && !publicSynopsis.trim().isEmpty()) {
                    prefs.edit().putString(cacheKey, publicSynopsis)
                            .putBoolean(cacheKey + "_ai_generated", false).apply();
                    mainHandler.post(() -> callback.onSuccess(publicSynopsis, false));
                    return;
                }

                // Default graceful synopsis if offline or metadata only
                String defaultSynopsis = buildFallbackLiterarySynopsis(title, author);
                mainHandler.post(() -> callback.onSuccess(defaultSynopsis, false));

            } catch (Exception e) {
                Log.w(TAG, "Summary generation failed: " + e.getClass().getSimpleName());
                String fallback = buildFallbackLiterarySynopsis(title, author);
                mainHandler.post(() -> callback.onSuccess(fallback, false));
            }
        });
    }

    /**
     * Answers a user question about a specific book (genre, page count, characters, plot)
     * using Google's Gemini 2.5 Flash API or graceful intelligent fallback.
     */
    public static void askBookQuestion(Context context, String title, String author, String question, ChatCallback callback) {
        if (question == null || question.trim().isEmpty()) {
            callback.onError("Please enter a question.");
            return;
        }

        String apiKey = getEffectiveApiKey(context);

        executor.execute(() -> {
            try {
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    String answer = callGeminiChatApi(title, author, question.trim(), apiKey);
                    if (answer != null && !answer.trim().isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(answer));
                        return;
                    }
                }

                String fallback = buildFallbackAnswer(title, author, question.trim());
                mainHandler.post(() -> callback.onSuccess(fallback));

            } catch (Exception e) {
                Log.w(TAG, "Chat request failed: " + e.getClass().getSimpleName());
                String fallback = buildFallbackAnswer(title, author, question.trim());
                mainHandler.post(() -> callback.onSuccess(fallback));
            }
        });
    }

    /**
     * Calls Gemini generateContent API for overview summary.
     */
    private static String callGeminiApi(String title, String author, String apiKey) throws Exception {
        String prompt = "You are an insightful literary guide for BookMate. " +
                "Write a captivating, spoiler-free 3 to 4 sentence overview of the book \"" + title + "\"" +
                (author != null && !author.isEmpty() ? " by " + author : "") + ". " +
                "Highlight the central premise, major themes, and why it is a compelling read.";
        return callGeminiGenerateContent(prompt, apiKey);
    }

    /**
     * Calls Gemini API for interactive Q&A about the book.
     */
    private static String callGeminiChatApi(String title, String author, String question, String apiKey) throws Exception {
        String prompt = "You are BookMate AI, an expert, enthusiastic literary companion assisting a reader with the book \""
                + title + "\"" + (author != null && !author.isEmpty() ? " by " + author : "") + ".\n\n"
                + "User question: \"" + question + "\"\n\n"
                + "Guidelines:\n"
                + "- Answer directly, accurately, and concisely (2 to 4 sentences).\n"
                + "- If asked about genre: describe the genre, atmosphere, and literary style.\n"
                + "- If asked about page count or length: provide realistic estimates for standard print editions and average reading time.\n"
                + "- If asked about characters or plot (e.g., what happened to a specific character): provide accurate character details and arcs. If major plot twists or endings are involved, start with a polite spoiler warning: '(Spoiler Warning: ...)'\n"
                + "- Maintain a warm, engaging, literary tone.";
        return callGeminiGenerateContent(prompt, apiKey);
    }

    /**
     * Unified generator executing against Google's Generative Language API
     * with automatic fallback across supported Gemini Flash candidate models.
     */
    private static String callGeminiGenerateContent(String prompt, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        String cleanedKey = cleanKey(apiKey);

        for (String model : CANDIDATE_MODELS) {
            HttpURLConnection conn = null;
            try {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + cleanedKey;
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("x-goog-api-key", cleanedKey);
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(18000);
                conn.setDoOutput(true);

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
                                String text = parts.getJSONObject(0).optString("text", "").trim();
                                if (!text.isEmpty()) {
                                    return text;
                                }
                            }
                        }
                    }
                } else {
                    // Log status only: provider bodies and exception URLs may contain private data.
                    Log.w(TAG, "Gemini API attempt with " + model + " failed (" + responseCode + ")");
                    // Continue to next model on 404 (model not found/authorized), 503 (high demand), 429, etc.
                }
            } catch (Exception e) {
                Log.w(TAG, "Gemini request failed on model " + model + ": " + e.getClass().getSimpleName());
            } finally {
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    /**
     * Fallback synopsis from public encyclopedic endpoint (Wikipedia REST API).
     */
    private static String fetchPublicSynopsis(String title) {
        HttpURLConnection conn = null;
        try {
            // Clean title for search (e.g. remove edition subtitles)
            String cleanTitle = title.split("(?i)\\b(volume|vol|part|book)\\b")[0]
                    .replaceAll("[,:;-].*$", "")
                    .trim();
            String encoded = URLEncoder.encode(cleanTitle, "UTF-8");
            URL url = new URL("https://en.wikipedia.org/api/rest_v1/page/summary/" + encoded);
            conn = (HttpURLConnection) url.openConnection();
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
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String buildFallbackLiterarySynopsis(String title, String author) {
        return "Overview unavailable. Please try again later.";
    }

    private static String buildFallbackAnswer(String title, String author, String query) {
        return "An answer is unavailable right now. Please try again later.";
    }
    public static String getEffectiveApiKey(Context context) {
        // Priority 1: User runtime override in preferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String overrideKey = prefs.getString(PREFS_API_KEY, null);
        if (overrideKey != null && !overrideKey.trim().isEmpty()) {
            return cleanKey(overrideKey);
        }

        // Priority 2: BuildConfig GEMINI_API_KEY loaded from secrets.properties
        try {
            if (BuildConfig.GEMINI_API_KEY != null && !BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
                return cleanKey(BuildConfig.GEMINI_API_KEY);
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private static String cleanKey(String key) {
        if (key == null) return "";
        String trimmed = key.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    public static void setApiKeyOverride(Context context, String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            prefs.edit().remove(PREFS_API_KEY).apply();
        } else {
            prefs.edit().putString(PREFS_API_KEY, cleanKey(apiKey)).apply();
        }
    }

    private static String buildCacheKey(String title, String author) {
        String cleanTitle = title.toLowerCase().replaceAll("[^a-z0-9]", "_");
        String cleanAuthor = author != null ? author.toLowerCase().replaceAll("[^a-z0-9]", "_") : "";
        return "ai_summary_v3_" + cleanTitle + "_" + cleanAuthor;
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
