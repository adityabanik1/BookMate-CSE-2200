package com.example.BookMate;

import java.util.ArrayList;
import java.util.List;

/** Bounded reader sections, preserving every character of the downloaded book. */
final class BookTextPages {
    private static final int SECTION_LENGTH = 12000;
    private final String text;
    private final List<Integer> starts = new ArrayList<>();

    BookTextPages(String text) {
        this.text = text;
        starts.add(0);
        int start = 0;
        while (text.length() - start > SECTION_LENGTH) {
            int end = start + SECTION_LENGTH;
            int boundary = text.lastIndexOf('\n', end - 1);
            if (boundary <= start + SECTION_LENGTH / 2) {
                boundary = text.lastIndexOf(' ', end - 1);
            }
            if (boundary > start + SECTION_LENGTH / 2) {
                end = boundary + 1;
            }
            if (Character.isHighSurrogate(text.charAt(end - 1))
                    && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            starts.add(end);
            start = end;
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
}
