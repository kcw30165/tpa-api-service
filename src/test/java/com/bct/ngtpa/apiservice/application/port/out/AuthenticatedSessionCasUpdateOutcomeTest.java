package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateOutcome;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: the outcome vocabulary for the generic CAS port does not exist yet. Deliberately
 * NOT {@code AuthFlowSessionMutationOutcome} (scenario-specific) and NOT
 * {@code AuthenticatedSessionCsrfRotationStatus} (CSRF-specific) -- a new, generic set. No
 * REVISION_ALREADY_PRESENT / legacy-initialization outcome exists, per the ratified decision not
 * to build legacy-revision-initialization machinery.
 */
class AuthenticatedSessionCasUpdateOutcomeTest {

  @Test
  void exposesExactlyTheGenericOutcomeSet() {
    assertThat(AuthenticatedSessionCasUpdateOutcome.values())
        .containsExactly(
            AuthenticatedSessionCasUpdateOutcome.APPLIED,
            AuthenticatedSessionCasUpdateOutcome.NOT_FOUND,
            AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH,
            AuthenticatedSessionCasUpdateOutcome.REVISION_MISSING,
            AuthenticatedSessionCasUpdateOutcome.CORRUPT_STATE,
            AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE);
  }
}
