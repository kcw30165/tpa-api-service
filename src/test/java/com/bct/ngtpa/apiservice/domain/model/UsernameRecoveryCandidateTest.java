package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the minimal protected Username Recovery candidate record required by the approved final
 * design.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 5
 *       memory model record and section 3 candidate fields (Scheme-Ref, account environment, trust
 *       code, registration flag, username only when registered);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 7
 *       (username for registered candidates only) and section 6 step 11 (opaque Scheme-Refs created
 *       only when a session is created);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4
 *       (zero-registered projection carries {@code "schemeRef": null});
 *   <li>{@code docs/design/6-username-recovery/username-recovery-test-traceability-design.md} APIM
 *       invariants (registered requires username; unregistered must not carry one).
 * </ul>
 *
 * <p>Invariants: {@code accountEnv}, {@code trustCode}, and {@code deliveryState} are required
 * (accountEnv/trustCode additionally nonblank); {@code schemeRef} is a non-null {@link
 * UsernameRecoverySchemeRef} for registered candidates and may be null only for pre-session
 * unregistered projections (any non-null Scheme-Ref is nonblank by value-object construction);
 * {@code userName} is present and nonblank only for registered candidates. {@link #toString()}
 * never discloses {@code schemeRef} or {@code userName}.
 */
class UsernameRecoveryCandidateTest {

  private static UsernameRecoveryCandidate candidate(
      UsernameRecoverySchemeRef schemeRef, boolean registered, String userName) {
    return new UsernameRecoveryCandidate(
        schemeRef,
        "DB",
        "DBA",
        registered,
        userName,
        UsernameRecoveryDeliveryState.AVAILABLE,
        null,
        null);
  }

  @Test
  void registeredCandidateRetainsUsernameAndSchemeRef() {
    UsernameRecoveryCandidate candidate =
        candidate(new UsernameRecoverySchemeRef("sch_1"), true, "john.doe");

    assertEquals(new UsernameRecoverySchemeRef("sch_1"), candidate.schemeRef());
    assertEquals("DB", candidate.accountEnv());
    assertEquals("DBA", candidate.trustCode());
    assertTrue(candidate.registered());
    assertEquals("john.doe", candidate.userName());
    assertEquals(UsernameRecoveryDeliveryState.AVAILABLE, candidate.deliveryState());
  }

  @Test
  void unregisteredCandidateRetainsNoUsername() {
    UsernameRecoveryCandidate candidate = candidate(null, false, null);

    assertFalse(candidate.registered());
    assertNull(candidate.userName());
  }

  @Test
  void allowsNullSchemeRefForPreSessionUnregisteredProjection() {
    // Zero-registered responses carry no generated Scheme-Ref (api-contract section 4).
    UsernameRecoveryCandidate candidate = candidate(null, false, null);

    assertNull(candidate.schemeRef());
    assertFalse(candidate.registered());
  }

  @Test
  void rejectsRegisteredCandidateWithoutNonblankUsername() {
    assertThrows(
        IllegalArgumentException.class,
        () -> candidate(new UsernameRecoverySchemeRef("sch_1"), true, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> candidate(new UsernameRecoverySchemeRef("sch_1"), true, "   "));
  }

  @Test
  void rejectsUnregisteredCandidateWithUsername() {
    assertThrows(IllegalArgumentException.class, () -> candidate(null, false, "john.doe"));
  }

  @Test
  void rejectsNullAccountEnvTrustCodeAndDeliveryState() {
    assertThrows(
        NullPointerException.class,
        () ->
            new UsernameRecoveryCandidate(
                new UsernameRecoverySchemeRef("sch_1"),
                null,
                "DBA",
                true,
                "john.doe",
                UsernameRecoveryDeliveryState.AVAILABLE,
                null,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new UsernameRecoveryCandidate(
                new UsernameRecoverySchemeRef("sch_1"),
                "DB",
                null,
                true,
                "john.doe",
                UsernameRecoveryDeliveryState.AVAILABLE,
                null,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new UsernameRecoveryCandidate(
                new UsernameRecoverySchemeRef("sch_1"),
                "DB",
                "DBA",
                true,
                "john.doe",
                null,
                null,
                null));
  }

  @Test
  void rejectsBlankAccountEnvAndTrustCode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UsernameRecoveryCandidate(
                new UsernameRecoverySchemeRef("sch_1"),
                "",
                "DBA",
                true,
                "john.doe",
                UsernameRecoveryDeliveryState.AVAILABLE,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UsernameRecoveryCandidate(
                new UsernameRecoverySchemeRef("sch_1"),
                "   ",
                "DBA",
                true,
                "john.doe",
                UsernameRecoveryDeliveryState.AVAILABLE,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UsernameRecoveryCandidate(
                new UsernameRecoverySchemeRef("sch_1"),
                "DB",
                "",
                true,
                "john.doe",
                UsernameRecoveryDeliveryState.AVAILABLE,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new UsernameRecoveryCandidate(
                new UsernameRecoverySchemeRef("sch_1"),
                "DB",
                "   ",
                true,
                "john.doe",
                UsernameRecoveryDeliveryState.AVAILABLE,
                null,
                null));
  }

  @Test
  void rejectsRegisteredCandidateWithoutSchemeRef() {
    assertThrows(IllegalArgumentException.class, () -> candidate(null, true, "john.doe"));
  }

  @Test
  void candidateSchemeRefIsProtectedByNonblankValueObject() {
    // Any non-null candidate Scheme-Ref is carried by the opaque value object, which
    // rejects null/blank values, so a non-null Scheme-Ref is always nonblank.
    UsernameRecoveryCandidate candidate =
        candidate(new UsernameRecoverySchemeRef("sch_1"), true, "john.doe");

    assertNotNull(candidate.schemeRef());
    assertEquals("sch_1", candidate.schemeRef().value());
  }

  @Test
  void toStringDoesNotDiscloseUsernameOrSchemeRef() {
    UsernameRecoveryCandidate candidate =
        candidate(new UsernameRecoverySchemeRef("sch_secret-1"), true, "secret.user");

    assertEquals(
        "UsernameRecoveryCandidate[schemeRef=<redacted>, accountEnv=DB, trustCode=DBA,"
            + " registered=true, userName=<redacted>, deliveryState=AVAILABLE]",
        candidate.toString());
  }
}
