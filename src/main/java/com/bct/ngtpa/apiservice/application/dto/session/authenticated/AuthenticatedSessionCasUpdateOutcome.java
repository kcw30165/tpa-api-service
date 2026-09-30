package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

/**
 * Generic outcome vocabulary for {@code AuthenticatedSessionCasUpdatePort}. Deliberately NOT {@code
 * AuthFlowSessionMutationOutcome} (scenario-specific) and NOT {@code
 * AuthenticatedSessionCsrfRotationStatus} (CSRF-specific).
 *
 * <p>No legacy-adoption outcome exists (contrast with {@code AuthFlowSession}'s {@code
 * LegacyAuthFlowSessionRevisionInitializationOutcome}): a session with a missing or malformed
 * revision fails the mutation with {@link #REVISION_MISSING} or {@link #CORRUPT_STATE} rather than
 * being adopted on first write, per the ratified no-legacy-migration decision.
 */
public enum AuthenticatedSessionCasUpdateOutcome {
  APPLIED,
  NOT_FOUND,
  REVISION_MISMATCH,
  REVISION_MISSING,
  CORRUPT_STATE,
  SYSTEM_FAILURE
}
