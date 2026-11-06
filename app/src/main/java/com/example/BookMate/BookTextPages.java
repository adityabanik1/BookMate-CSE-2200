package com.example.BookMate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded reader sections, preserving every character of the downloaded book,
 * with intelligent Chapter detection for table-of-contents navigation.
 */
final class BookTextPages {
    private static final int SECTION_LENGTH = 12000;
    private final String text;
    private final List<Integer> starts = new ArrayList<>();
    private final List<Chapter> chapters = new ArrayList<>();

    public static final class Chapter {
        private final String title;
        private final int charOffset;
        private final int sectionIndex;

        public Chapter(String title, int charOffset, int sectionIndex) {
            this.title = title;
            this.charOffset = charOffset;
            this.sectionIndex = sectionIndex;
        }

        public String getTitle() {
            return title;
        }

        public int getCharOffset() {
            return charOffset;
        }

        public int getSectionIndex() {
            return sectionIndex;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    BookTextPages(String text) {
        this.text = text != null ? text : "";
        starts.add(0);
        int start = 0;
        while (this.text.length() - start > SECTION_LENGTH) {
            int end = start + SECTION_LENGTH;
            int boundary = this.text.lastIndexOf('\n', end - 1);
            if (boundary <= start + SECTION_LENGTH / 2) {
                boundary = this.text.lastIndexOf(' ', end - 1);
            }
            if (boundary > start + SECTION_LENGTH / 2) {
                end = boundary + 1;
            }
            if (Character.isHighSurrogate(this.text.charAt(end - 1))
                    && Character.isLowSurrogate(this.text.charAt(end))) {
                end--;
            }
            starts.add(end);
            start = end;
        }
        extractChapters();
    }

    private void extractChapters() {
        if (text.isEmpty()) return;

        Pattern pattern = Pattern.compile(
                "(?m)^[ \\t]*((?:CHAPTER|Chapter|BOOK|Book|PART|Part|ACT|Act|SCENE|Scene|STORY|Story)\\s+[0-9IVXLCDM]+(?:[.:\\-\\s][^\\n]{0,60})?|[IVXLCDM]+\\.\\s+[A-Z0-9\\s',.\\-]{3,60}|PROLOGUE|Prologue|EPILOGUE|Epilogue|PREFACE|Preface|INTRODUCTION|Introduction)[ \\t]*$"
        );
        Matcher m = pattern.matcher(text);
        List<int[]> candidates = new ArrayList<>();
        List<String> rawTitles = new ArrayList<>();

        while (m.find()) {
            candidates.add(new int[]{m.start(), m.end()});
            rawTitles.add(m.group(1).trim());
        }

        // Filter out table of contents (TOC entries have little/no text between them: < 100 chars)
        List<Chapter> detected = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            int startPos = candidates.get(i)[0];
            int endPos = candidates.get(i)[1];
            String title = rawTitles.get(i);

            // If next candidate appears within 100 characters after this heading,
            // it is a Table of Contents listing rather than an actual chapter body.
            if (i + 1 < candidates.size() && (candidates.get(i + 1)[0] - endPos < 100)) {
                continue; // Skip TOC entry
            }

            // Check if there is a subtitle on the next line
            int lineEnd = candidates.get(i)[1];
            if (lineEnd < text.length()) {
                int nextLineEnd = text.indexOf('\n', lineEnd + 1);
                if (nextLineEnd != -1 && nextLineEnd - lineEnd < 60) {
                    String nextLine = text.substring(lineEnd + 1, nextLineEnd).trim();
                    if (!nextLine.isEmpty()
                            && !nextLine.toLowerCase().startsWith("chapter")
                            && !nextLine.startsWith("[")
                            && Character.isUpperCase(nextLine.charAt(0))) {
                        String cleanPrefix = title.replaceAll("[.:\\-\\s]+$", "");
                        title = cleanPrefix + " — " + nextLine;
                    }
                }
            }

            int sec = pageForOffset(startPos);
            detected.add(new Chapter(title, startPos, sec));
        }

        // If the first chapter starts after substantial front matter (> 3000 chars),
        // add a "Front Matter / Preface" entry at section 0
        if (!detected.isEmpty()) {
            if (detected.get(0).getCharOffset() > 3000) {
                chapters.add(new Chapter("Front Matter / Preface", 0, 0));
            }
            chapters.addAll(detected);
        }
    }

    int size() { return starts.size(); }
    int start(int page) { return starts.get(page); }
    int end(int page) { return page + 1 < size() ? start(page + 1) : text.length(); }
    String content(int page) { return text.substring(start(page), end(page)); }

    int pageForOffset(int offset) {
        int low = 0;
        int high = size() - 1;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (start(mid) <= offset) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public int getFirstContentSection() {
        if (chapters.size() > 1 && chapters.get(0).getTitle().startsWith("Front Matter")) {
            return chapters.get(1).getSectionIndex();
        }
        return 0;
    }

    public String getChapterTitleForPage(int page) {
        if (chapters.isEmpty()) return null;
        Chapter candidate = null;
        for (Chapter ch : chapters) {
            if (ch.getSectionIndex() == page) {
                return ch.getTitle();
            }
            if (ch.getSectionIndex() < page) {
                candidate = ch;
            } else {
                break;
            }
        }
        return candidate != null ? candidate.getTitle() : chapters.get(0).getTitle();
    }
}
