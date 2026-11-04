package com.example.BookMate;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoanTargetTest {
    @Test public void archiveLinksKeepExactEditionAndUseProviderActions() {
        for (String mode : new String[]{"details", "stream", "embed"}) {
            LoanTarget target = LoanTarget.fromUrl("https://archive.org/" + mode
                    + "/edition_123?ui=embed#page/4");
            assertNotNull(target);
            assertEquals("edition_123", target.archiveId);
            assertEquals("https://openlibrary.org/borrow/ia/edition_123?action=borrow", target.actionUrl(false));
            assertEquals("https://openlibrary.org/borrow/ia/edition_123?action=return", target.actionUrl(true));
            assertTrue(target.matches("edition_123", "/books/OL1M"));
            assertFalse(target.matches("edition_123_second", "/books/OL1M"));
        }
    }

    @Test public void openLibraryEditionsHaveExplicitBorrowAndReturnRoutes() {
        LoanTarget target = LoanTarget.fromUrl("https://openlibrary.org/books/OL123M/Some_title");
        assertNotNull(target);
        assertEquals("https://openlibrary.org/books/OL123M/x/borrow?action=return", target.actionUrl(true));
        assertTrue(target.matches("another-archive-id", "/books/OL123M"));
        assertFalse(target.matches("OL123M", "/books/OL1234M"));
        assertNull(LoanTarget.fromUrl("https://openlibrary.org/works/OL123W"));
    }

    @Test public void rejectUntrustedOriginsAndInvalidIdentifiers() {
        String[] invalid = { null, "", "http://archive.org/details/edition", "file:///details/edition",
                "https://archive.org.evil.test/details/edition", "https://archive.org@evil.test/details/edition",
                "https://evil.test@archive.org/details/edition", "https://archive.org:8443/details/edition",
                "https://archive.org/details/..", "https://archive.org/details/%22quoted%22",
                "https://archive.org/details/item%3Faction%3Dreturn", "https://archive.org/details/",
                "https://openlibrary.org/books/OL123W", "javascript:alert(1)" };
        for (String url : invalid) assertNull(url, LoanTarget.fromUrl(url));
    }

    @Test public void accountScreenOnlyAllowsExactSecureProviderHosts() {
        assertTrue(LoanTarget.isProviderUrl("https://openlibrary.org/account/login"));
        assertTrue(LoanTarget.isProviderUrl("https://archive.org/bookreader/BookReaderAuth.php?token=example"));
        assertFalse(LoanTarget.isProviderUrl("https://archive.org.evil.test/"));
        assertFalse(LoanTarget.isProviderUrl("https://archive.org@evil.test/"));
        assertFalse(LoanTarget.isProviderUrl("intent://archive.org/"));
        assertFalse(LoanTarget.isProviderUrl("http://openlibrary.org/"));
    }

    @Test public void loginLandingShortcutOnlyMatchesTheExactProviderLoansPage() {
        assertTrue(LoanTarget.isLoansLandingPage("https://openlibrary.org/account/loans"));
        assertTrue(LoanTarget.isLoansLandingPage("https://openlibrary.org/account/loans?source=login"));
        for (String url : new String[]{"https://archive.org/account/loans",
                "https://openlibrary.org.evil.test/account/loans",
                "https://openlibrary.org/account/loans.json",
                "https://openlibrary.org/account/loans/history", "https://openlibrary.org/account/login",
                "http://openlibrary.org/account/loans", "https://evil.test@openlibrary.org/account/loans"}) {
            assertFalse(url, LoanTarget.isLoansLandingPage(url));
        }
    }

    @Test public void anUnloadedOrClearedWebViewStillNeedsTheLoginPage() {
        assertFalse(LoanTarget.isSignInPage(null));
        assertFalse(LoanTarget.isSignInPage("about:blank"));
        assertFalse(LoanTarget.isSignInPage("https://openlibrary.org/account/loans"));
        assertFalse(LoanTarget.isSignInPage("https://evil.test/account/login"));
        assertTrue(LoanTarget.isSignInPage("https://openlibrary.org/account/login?redirect=x"));
        assertTrue(LoanTarget.isSignInPage("https://openlibrary.org/verify_human"));
    }

    @Test public void googleSignInIsRecognizedWithoutAllowingLookalikeHosts() {
        assertTrue(LoanTarget.isGoogleSignInUrl("https://accounts.google.com/o/oauth2/auth?state=example"));
        assertFalse(LoanTarget.isGoogleSignInUrl("https://accounts.google.com.evil.test/"));
        assertFalse(LoanTarget.isGoogleSignInUrl("https://accounts.google.com@evil.test/"));
        assertFalse(LoanTarget.isGoogleSignInUrl("http://accounts.google.com/"));
        assertFalse(LoanTarget.isGoogleSignInUrl(null));
    }
}
