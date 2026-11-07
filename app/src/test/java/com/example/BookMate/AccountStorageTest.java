package com.example.BookMate;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountStorageTest {
    @Test public void existingOwnerKeepsOriginalLibrary() {
        assertEquals("bookmate.db", AccountStorage.databaseName("first", "first"));
    }

    @Test public void switchingAccountsUsesSeparateStableLibraries() {
        String second = AccountStorage.databaseName("first", "second");
        assertNotEquals("bookmate.db", second);
        assertEquals(second, AccountStorage.databaseName("first", "second"));
        assertNotEquals(second, AccountStorage.databaseName("first", "third"));
        assertEquals("bookmate.db", AccountStorage.databaseName("first", "first"));
    }

    @Test public void accountIdsCannotBecomeFilesystemPaths() {
        String name = AccountStorage.databaseName("first", "../other/user");
        assertFalse(name.contains("/"));
        assertFalse(name.contains("\\"));
    }
}
