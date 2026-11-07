package com.example.BookMate;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class BookSearchRankingTest {
    @Test public void exactWorkBeatsUnrelatedGutenbergResults() {
        Book unrelated = new Book("Uncle Tom's Cabin", "Harriet Beecher Stowe", "", "", "https://www.gutenberg.org/ebooks/203");
        Book target = new Book("The Art of War", "Sun Tzu", "", "", "https://archive.org/details/war");
        assertSame(target, BookSearchRanking.rank("art of war", Arrays.asList(unrelated, target)).get(0));
    }

    @Test public void authorAndAccentsAreSearchable() {
        Book target = new Book("Les Misérables", "Victor Hugo", "", "");
        Book other = new Book("Other", "Other", "", "");
        assertSame(target, BookSearchRanking.rank("les miserables", Arrays.asList(other, target)).get(0));
        assertSame(target, BookSearchRanking.rank("Victor Hugo", Arrays.asList(other, target)).get(0));
    }

    @Test public void sameTitleDifferentAuthorsSurviveDeduplication() {
        Book first = new Book("The Art of War", "Sun Tzu", "", "");
        Book second = new Book("The Art of War", "Machiavelli", "", "");
        List<Book> results = BookSearchRanking.rank("art of war", Arrays.asList(first, second, first));
        assertEquals(2, results.size());
    }
}
