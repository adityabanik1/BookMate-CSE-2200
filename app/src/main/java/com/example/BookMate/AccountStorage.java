package com.example.BookMate;

/** Stable local database names keep account libraries separate across sign-ins. */
final class AccountStorage {
    static String databaseName(String legacyOwner, String uid) {
        if (uid == null || uid.equals(legacyOwner)) return "bookmate.db";
        return "bookmate_" + java.util.UUID.nameUUIDFromBytes(
                uid.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".db";
    }
}
