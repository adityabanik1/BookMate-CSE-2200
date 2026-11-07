package com.example.BookMate;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReadingStreakTest {

    @Test
    public void testFiveMinuteGoalThreshold() {
        // Less than 300 seconds (< 5 minutes) must NOT meet streak goal
        ReadingStreak.DayLog underGoal = new ReadingStreak.DayLog(
                "2026-11-07", "Today", "Pride and Prejudice", "Jane Austen", 240); // 4 mins
        assertFalse("4 minutes must not meet the 5-minute streak goal", underGoal.isGoalMet());

        // Exactly 300 seconds (5 minutes) MUST meet streak goal
        ReadingStreak.DayLog exactGoal = new ReadingStreak.DayLog(
                "2026-11-07", "Today", "Pride and Prejudice", "Jane Austen", 300); // 5 mins
        assertTrue("300 seconds (5 mins) must meet the streak goal", exactGoal.isGoalMet());

        // Above 300 seconds (e.g. 22 minutes) MUST meet streak goal
        ReadingStreak.DayLog overGoal = new ReadingStreak.DayLog(
                "2026-11-06", "Yesterday", "Alice in Wonderland", "Lewis Carroll", 1320); // 22 mins
        assertTrue("22 minutes must meet the streak goal", overGoal.isGoalMet());
    }

    @Test
    public void testFormattedDuration() {
        ReadingStreak.DayLog logMins = new ReadingStreak.DayLog(
                "2026-11-07", "Today", "Book A", "Author A", 480);
        assertEquals("8 mins", logMins.getFormattedDuration());

        ReadingStreak.DayLog logSecs = new ReadingStreak.DayLog(
                "2026-11-07", "Today", "Book B", "Author B", 45);
        assertEquals("45s", logSecs.getFormattedDuration());
    }

    @Test
    public void testDateFormatting() {
        String today = ReadingStreak.day(0);
        assertEquals("Today", ReadingStreak.formatDisplayDate(today));

        String yesterday = ReadingStreak.day(1);
        assertEquals("Yesterday", ReadingStreak.formatDisplayDate(yesterday));
    }
}
