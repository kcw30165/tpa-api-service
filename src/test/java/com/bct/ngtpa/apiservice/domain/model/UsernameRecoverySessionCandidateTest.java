package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies the protected session-scoped Username Recovery candidate.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 3 (candidate
 * fields: Scheme-Ref, account environment, trust code, registration flag, username only when
 * registered, delivery state, accepted timestamp, sender/delivery operation reference when
 * available) and {@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 * section 7 (requested timestamp and sender/idempotency reference required for replay safety) and
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4
 * (session-bound opaque schemeRefs, including for unregistered schemes).
 *
 * <p>The Task 006 {@link UsernameRecoveryCandidate} is reused by composition; this session
 * candidate adds delivery metadata (requested timestamp and sender reference) that the committed
 * Task 006 projection does not carry, and additionally requires a session-bound (non-null)
 * Scheme-Ref.
 *
 * <p>Invariants: {@code candidate} is non-null with a non-null Scheme-Ref; a {@code REQUESTED}
 * candidate requires a non-null {@code requestedAt} and a nonblank {@code senderReference}; any
 * other delivery state must not retain delivery metadata. {@link #toString()} never discloses the
 * Scheme-Ref, username, or sender reference.
 */
class UsernameRecoverySessionCandidateTest {

  private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:01:00Z");

  private static UsernameRecoverySessionCandidate registered(
      String scheme, String env, String trust, UsernameRecoveryDeliveryState state) {
    return new UsernameRecoverySessionCandidate(
        new UsernameRecoveryCandidate(
            new UsernameRecoverySchemeRef(scheme), env, trust, true, "user-" + scheme, state),
        null,
        null);
  }

  private static UsernameRecoverySessionCandidate unregistered(
      String scheme, String env, String trust, UsernameRecoveryDeliveryState state) {
    return new UsernameRecoverySessionCandidate(
        new UsernameRecoveryCandidate(
            new UsernameRecoverySchemeRef(scheme), env, trust, false, null, state),
        null,
        null);
  }

  @Test
  void delegatesEstablishedCandidateFields() {
    UsernameRecoverySessionCandidate candidate =
        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);

    assertEquals(new UsernameRecoverySchemeRef("sch_1"), candidate.schemeRef());
    assertEquals("DB", candidate.accountEnv());
    assertEquals("DBA", candidate.trustCode());
    assertTrue(candidate.registered());
    assertEquals("user-sch_1", candidate.userName());
    assertEquals(UsernameRecoveryDeliveryState.AVAILABLE, candidate.deliveryState());
    assertNull(candidate.requestedAt());
    assertNull(candidate.senderReference());
  }

  @Test
  void unregisteredSessionCandidateRetainsNoUsername() {
    UsernameRecoverySessionCandidate candidate =
        unregistered("sch_9", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);

    assertTrue(!candidate.registered());
    assertNull(candidate.userName());
  }

  @Test
  void requiresNonNullCandidate() {
    assertThrows(
        NullPointerException.class, () -> new UsernameRecoverySessionCandidate(null, null, null));
  }

  @Test
  void requiresSessionBoundSchemeRef() {
    UsernameRecoveryCandidate preSession =
        new UsernameRecoveryCandidate(
            null, "DB", "DBA", false, null, UsernameRecoveryDeliveryState.AVAILABLE);

    assertThrows(
        IllegalArgumentException.class,
        () -> new UsernameRecoverySessionCandidate(preSession, null, null));
  }

  @Test
  void requestedCandidateRequiresRequestedAtAndSenderReference() {
    UsernameRecoverySessionCandidate available =
        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);

    assertThrows(
        NullPointerException.class,
        () -> available.withState(UsernameRecoveryDeliveryState.REQUESTED, null, "sender-ref-1"));
    assertThrows(
        NullPointerException.class,
        () -> available.withState(UsernameRecoveryDeliveryState.REQUESTED, REQUESTED_AT, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> available.withState(UsernameRecoveryDeliveryState.REQUESTED, REQUESTED_AT, "   "));
  }

  @Test
  void nonRequestedCandidateMustNotRetainDeliveryMetadata() {
    UsernameRecoverySessionCandidate available =
        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);

    assertThrows(
        IllegalArgumentException.class,
        () -> new UsernameRecoverySessionCandidate(available.candidate(), REQUESTED_AT, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new UsernameRecoverySessionCandidate(available.candidate(), null, "sender-ref-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UsernameRecoverySessionCandidate(
                available.candidate(), REQUESTED_AT, "sender-ref-1"));
  }

  @Test
  void withStateRebuildsDeliveryStateAndMetadata() {
    UsernameRecoverySessionCandidate available =
        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.AVAILABLE);

    UsernameRecoverySessionCandidate reserved =
        available.withState(UsernameRecoveryDeliveryState.RESERVED, null, null);
    assertEquals(UsernameRecoveryDeliveryState.RESERVED, reserved.deliveryState());
    assertNull(reserved.requestedAt());
    assertNull(reserved.senderReference());

    UsernameRecoverySessionCandidate requested =
        available.withState(UsernameRecoveryDeliveryState.REQUESTED, REQUESTED_AT, "sender-ref-1");
    assertEquals(UsernameRecoveryDeliveryState.REQUESTED, requested.deliveryState());
    assertEquals(REQUESTED_AT, requested.requestedAt());
    assertEquals("sender-ref-1", requested.senderReference());
    assertEquals(available.schemeRef(), requested.schemeRef());
    assertEquals("user-sch_1", requested.userName());

    UsernameRecoverySessionCandidate failed =
        available.withState(UsernameRecoveryDeliveryState.FAILED, null, null);
    assertEquals(UsernameRecoveryDeliveryState.FAILED, failed.deliveryState());
    assertNull(failed.requestedAt());
    assertNull(failed.senderReference());

    UsernameRecoverySessionCandidate unknown =
        available.withState(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME, null, null);
    assertEquals(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME, unknown.deliveryState());
    assertNull(unknown.requestedAt());
    assertNull(unknown.senderReference());
  }

  @Test
  void toStringNeverDisclosesSchemeRefUserNameOrSenderReference() {
    UsernameRecoverySessionCandidate requested =
        registered("sch_1", "DB", "DBA", UsernameRecoveryDeliveryState.RESERVED)
            .withState(UsernameRecoveryDeliveryState.REQUESTED, REQUESTED_AT, "sender-secret-ref");

    assertEquals(
        "UsernameRecoverySessionCandidate[candidate=UsernameRecoveryCandidate[schemeRef=<redacted>,"
            + " accountEnv=DB, trustCode=DBA, registered=true, userName=<redacted>,"
            + " deliveryState=REQUESTED], requestedAt=2026-01-01T00:01:00Z,"
            + " senderReference=<redacted>]",
        requested.toString());
  }
}
