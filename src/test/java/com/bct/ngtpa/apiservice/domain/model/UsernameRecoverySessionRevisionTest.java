package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the nonnegative session revision value object used for optimistic-concurrency semantics.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * section 3 (session fields include the revision) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 7 (session
 * revision retained as minimal protected data). Task 010 invariants: revision is nonnegative and
 * advances through approved state mutations.
 *
 * <p>Invariants: the revision is never negative; {@link #next()} advances by exactly one; an
 * overflowed advance wraps negative and is therefore rejected by construction rather than silently
 * producing a negative revision.
 */
class UsernameRecoverySessionRevisionTest {

    @Test
    void startsAtZero() {
        assertEquals(0, new UsernameRecoverySessionRevision(0).value());
    }

    @Test
    void advancesByExactlyOne() {
        assertEquals(1, new UsernameRecoverySessionRevision(0).next().value());
        assertEquals(3, new UsernameRecoverySessionRevision(2).next().value());
    }

    @Test
    void rejectsNegative() {
        assertThrows(
                IllegalArgumentException.class, () -> new UsernameRecoverySessionRevision(-1));
    }

    @Test
    void nextNeverSilentlyWrapsToNegative() {
        UsernameRecoverySessionRevision max = new UsernameRecoverySessionRevision(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, max::next);
    }

    @Test
    void toStringDisclosesOnlyTheCounter() {
        assertEquals(
                "UsernameRecoverySessionRevision[value=0]",
                new UsernameRecoverySessionRevision(0).toString());
    }
}
