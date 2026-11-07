package com.example.BookMate;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoanServiceTest {
    private final LoanTarget target = LoanTarget.fromUrl("https://archive.org/details/exact_edition");

    @Test public void onlyMatchingEditionConfirmsActiveLoan() {
        assertEquals(LoanService.State.ACTIVE, LoanService.parseLoans(
                "{\"loans\":[{\"ocaid\":\"exact_edition\",\"book\":\"/books/OL123M\"}]}", target));
        assertEquals(LoanService.State.NONE, LoanService.parseLoans(
                "{\"loans\":[{\"ocaid\":\"other_edition\"}]}", target));
    }

    @Test public void emptyAuthenticatedListCanConfirmAbsence() {
        assertEquals(LoanService.State.NONE, LoanService.parseLoans("{\"loans\":[]}", target));
    }

    @Test public void errorsAndSchemaChangesNeverConfirmReturn() {
        String[] responses = { "", "<html>Sign in</html>", "{}", "[]", "null",
                "{\"error\":\"unauthorized\",\"loans\":[]}",
                "{\"success\":false,\"loans\":[]}", "{\"loans\":null}",
                "{\"loans\":[null]}", "{\"loans\":[{}]}",
                "{\"loans\":[{\"ocaid\":null}]}", "{\"loans\":[{\"ocaid\":123}]}",
                "{\"loans\":[{\"different_identifier\":\"exact_edition\"}]}" };
        for (String body : responses) {
            assertEquals(body, LoanService.State.UNKNOWN, LoanService.parseLoans(body, target));
        }
    }

    @Test public void editionTargetMatchesBookKeyWithoutConfusingOtherLoans() {
        LoanTarget edition = LoanTarget.fromUrl("https://openlibrary.org/books/OL123M");
        assertEquals(LoanService.State.ACTIVE, LoanService.parseLoans(
                "{\"loans\":[{\"book\":\"/books/OL123M\"}]}", edition));
        assertEquals(LoanService.State.NONE, LoanService.parseLoans(
                "{\"loans\":[{\"book\":\"/books/OL999M\"}]}", edition));
        assertEquals(LoanService.State.UNKNOWN, LoanService.parseLoans(
                "{\"loans\":[{\"ocaid\":\"other\"}]}", edition));
    }

    @Test public void missingSessionRequiresSignInWithoutNetworkRequest() {
        assertEquals(LoanService.State.SIGN_IN_REQUIRED, LoanService.check(target, null));
        assertEquals(LoanService.State.SIGN_IN_REQUIRED, LoanService.check(target, ""));
    }

    @Test public void availabilitySeparatesOpenBorrowableAndUnavailable() {
        assertEquals(LoanService.Availability.OPEN, LoanService.parseAvailability(
                "{\"lending_status\":{\"is_readable\":true}}"));
        assertEquals(LoanService.Availability.BORROWABLE, LoanService.parseAvailability(
                "{\"lending_status\":{\"available_to_browse\":true}}"));
        assertEquals(LoanService.Availability.BORROWABLE, LoanService.parseAvailability(
                "{\"lending_status\":{\"available_to_borrow\":true}}"));
        assertEquals(LoanService.Availability.UNAVAILABLE, LoanService.parseAvailability(
                "{\"lending_status\":{\"is_lendable\":false}}"));
        assertEquals(LoanService.Availability.UNAVAILABLE, LoanService.parseAvailability(
                "{\"lending_status\":{\"available_to_browse\":false,\"available_to_borrow\":false}}"));
    }

    @Test public void malformedAvailabilityDoesNotBecomeARestrictionOrSuccess() {
        for (String body : new String[]{"{}", "<html>Offline</html>",
                "{\"lending_status\":{}}", "{\"lending_status\":{\"is_lendable\":\"false\"}}",
                "{\"error\":\"failed\",\"lending_status\":{\"is_readable\":true}}"}) {
            assertEquals(LoanService.Availability.UNKNOWN, LoanService.parseAvailability(body));
        }
    }
}
