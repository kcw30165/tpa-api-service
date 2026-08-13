package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the protected verified-destination value object retained by the Username Recovery session.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 3 (session fields include delivery channel and verified destination) and section 7 (the
 * TAS-verified destination is stored in protected context) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 7 (protected
 * verified destination and channel).
 *
 * <p>Invariants: the destination value is non-null and nonblank and the channel is non-null;
 * {@link #toString()} never discloses the destination value.
 */
class UsernameRecoveryVerifiedDestinationTest {

    @Test
    void retainsValueAndEmailChannel() {
        UsernameRecoveryVerifiedDestination destination = new UsernameRecoveryVerifiedDestination(
                "p****c@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

        assertEquals("p****c@example.com", destination.value());
        assertEquals(UsernameRecoveryVerifiedDestination.Channel.EMAIL, destination.channel());
    }

    @Test
    void supportsMobileChannel() {
        UsernameRecoveryVerifiedDestination destination = new UsernameRecoveryVerifiedDestination(
                "p****c", UsernameRecoveryVerifiedDestination.Channel.MOBILE);

        assertEquals(UsernameRecoveryVerifiedDestination.Channel.MOBILE, destination.channel());
    }

    @Test
    void rejectsNullValue() {
        assertThrows(
                NullPointerException.class,
                () -> new UsernameRecoveryVerifiedDestination(null, UsernameRecoveryVerifiedDestination.Channel.EMAIL));
    }

    @Test
    void rejectsNullChannel() {
        assertThrows(
                NullPointerException.class,
                () -> new UsernameRecoveryVerifiedDestination("p****c@example.com", null));
    }

    @Test
    void rejectsBlankValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UsernameRecoveryVerifiedDestination("", UsernameRecoveryVerifiedDestination.Channel.EMAIL));
        assertThrows(
                IllegalArgumentException.class,
                () -> new UsernameRecoveryVerifiedDestination("   ", UsernameRecoveryVerifiedDestination.Channel.EMAIL));
    }

    @Test
    void toStringNeverDisclosesDestinationValue() {
        UsernameRecoveryVerifiedDestination destination = new UsernameRecoveryVerifiedDestination(
                "p****c@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

        assertEquals(
                "UsernameRecoveryVerifiedDestination[value=<redacted>, channel=EMAIL]",
                destination.toString());
    }
}
