package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

/**
 * Generic outcome vocabulary for {@link AuthenticatedSessionConcurrencyService}.
 *
 * <p>Deliberately NOT one-to-one with {@link
 * com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateOutcome}:
 * only {@code REVISION_MISMATCH} from the CAS port is retryable and, if retries are exhausted,
 * surfaces here as {@link #REVISION_CONFLICT_EXHAUSTED}. Every other non-{@code APPLIED} CAS
 * outcome (not found, missing/malformed revision, corrupt stored state, or a system failure at the
 * CAS layer itself) collapses to {@link #SYSTEM_FAILURE}, since retrying cannot fix any of those.
 * {@code UNCHANGED} is a no-write eligibility observation from updateIfEligible only;
 * updateIfUsable continues to return SESSION_NOT_USABLE without a session in that case.
 */
public enum AuthenticatedSessionConcurrencyOutcome {
  APPLIED,
  UNCHANGED,
  SESSION_NOT_FOUND,
  SESSION_NOT_USABLE,
  REVISION_CONFLICT_EXHAUSTED,
  SYSTEM_FAILURE
}
