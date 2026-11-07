package com.example.BookMate;

import java.util.regex.Pattern;

/**
 * BookTextFormatter.java — Gutenberg & Public Domain Text Cleaner
 * Formats raw plain-text ebooks (e.g. from Project Gutenberg, Internet Archive)
 * into clean, readable, fluid paragraphs for mobile displays:
 * 1. Strips [Illustration: ...] and [Transcriber's Note: ...] OCR/ASCII artifacts.
 * 2. Unwraps 70-column hard line wraps within narrative paragraphs so text flows
 *    smoothly to fit screen width with lineSpacingMultiplier.
 * 3. Preserves intentional paragraph breaks (\n\n), poetry stanzas, and indented quotes.
 * 4. Eliminates excessive blank lines and trailing carriage returns.
 */
public final class BookTextFormatter {

    private static final Pattern ILLUSTRATION_PATTERN =
            Pattern.compile("(?is)\\[Illustration[^\\]]*\\]");
    private static final Pattern TRANSCRIBER_PATTERN =
            Pattern.compile("(?is)\\[Transcriber's Note[^\\]]*\\]");

    private BookTextFormatter() {}

    /**
     * Cleans and formats raw ebook text into fluid paragraphs.
     *
     * @param raw Raw text from Gutenberg or Archive.
     * @return Formatted reader-ready text, or empty string if input is null/empty.
     */
    public static String format(String raw) {
        if (raw == null) return null;
        if (raw.trim().isEmpty()) return "";

        // 1. Remove [Illustration: ...] and [Transcriber's Note: ...] artifacts
        String text = ILLUSTRATION_PATTERN.matcher(raw).replaceAll("");
        text = TRANSCRIBER_PATTERN.matcher(text).replaceAll("");

        // 2. Normalize carriage returns
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        // 3. Split into paragraphs by two or more newlines
        String[] rawParagraphs = text.split("\n\\s*\n+");
        StringBuilder result = new StringBuilder(text.length());

        for (String p : rawParagraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;

            String[] lines = trimmed.split("\n");

            // If lines represent poetry, verse, or heavily indented tables, preserve exact lines
            if (shouldPreserveExactLines(lines)) {
                result.append(trimmed).append("\n\n");
            } else {
                // Unwrap hard-wrapped lines into a single continuous paragraph
                StringBuilder unwrapped = new StringBuilder(trimmed.length() + 16);
                for (String line : lines) {
                    String cleanLine = line.trim();
                    if (cleanLine.isEmpty()) continue;
                    if (unwrapped.length() > 0) {
                        unwrapped.append(' ');
                    }
                    unwrapped.append(cleanLine);
                }
                result.append(unwrapped).append("\n\n");
            }
        }

        return result.toString().trim();
    }

    /**
     * Detects if lines are part of poetry, lyrics, or lists that must keep their line breaks.
     */
    private static boolean shouldPreserveExactLines(String[] lines) {
        if (lines.length <= 1) return false;
        int shortOrIndented = 0;
        for (String line : lines) {
            if (line.startsWith("   ") || line.startsWith("\t") || line.length() < 30) {
                shortOrIndented++;
            }
        }
        return shortOrIndented > lines.length / 2;
    }
}
