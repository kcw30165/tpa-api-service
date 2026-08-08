package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the opaque protected Username Recovery session identifier value object.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 3 (session fields include the session identity) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 7 (minimal
 * protected session data; session identifier never a raw transport header).
 *
 * <p>Invariants: the identifier is non-null, nonblank, and restricted to safe opaque characters;
 * {@link #toString()} never discloses the value.
 */
class UsernameRecoverySessionIdTest {

    @Test
    void retainsOpaqueValue() {
        UsernameRecoverySessionId id = new UsernameRecoverySessionId("afs_opaque-1");
        assertEquals("afs_opaque-1", id.value());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> new UsernameRecoverySessionId(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySessionId(""));
        assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySessionId("   "));
    }

    @Test
    void rejectsUnsafeCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySessionId("bad value"));
        assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySessionId("bad/../value"));
        assertThrows(IllegalArgumentException.class, () -> new UsernameRecoverySessionId("bad;drop"));
    }

    @Test
    void toStringNeverDisclosesValue() {
        UsernameRecoverySessionId id = new UsernameRecoverySessionId("afs_secret-1");
        assertEquals("UsernameRecoverySessionId[value=<redacted>]", id.toString());
    }
}
