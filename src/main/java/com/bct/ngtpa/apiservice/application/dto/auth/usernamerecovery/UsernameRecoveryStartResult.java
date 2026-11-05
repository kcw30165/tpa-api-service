package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.List;
import java.util.Objects;

/**
 * Application result of the Username Recovery start flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 4 (a valid, non-empty lookup result returns {@code csrfToken}, {@code expiresInSeconds}
 * and safe {@code schemes}, including when every scheme is unregistered; member-not-found returns
 * {@code RESTART_FLOW}) and {@code
 * docs/design/6-username-recovery/username-recovery-solution-design.md} section 6 (the session,
 * CSRF token and opaque Scheme-Refs are created for every valid, non-empty lookup result).
 *
 * <p>Invariants: {@code outcome} is required. {@code SESSION_CREATED} requires a nonblank {@code
 * csrfToken}, a positive {@code expiresInSeconds}, at least one scheme projection and a
 * session-bound schemeRef on every projection; the projections may be registered, unregistered or
 * mixed. {@code MEMBER_NOT_FOUND} carries no session data and no schemes. {@link #toString()}
 * never discloses the CSRF token or the auth-flow session id.
 *
 * <p>The {@code authFlowSessionId} component (Repair 024A) is server/transport-only: it never
 * enters the public projection or the replay snapshots, and a result without a session ({@link
 * #withoutSession}) leaves it null. The SESSION_CREATED non-null session-id invariant is enforced
 * at the service construction site ({@code
 * UsernameRecoveryStartProtectedOperation.sessionCreated}), not in this record constructor.
 */
public record UsernameRecoveryStartResult(
    Outcome outcome,
    AuthFlowSessionId authFlowSessionId,
    String csrfToken,
    Integer expiresInSeconds,
    List<UsernameRecoverySchemeProjection> schemes) {

  public enum Outcome {
    SESSION_CREATED,
    MEMBER_NOT_FOUND
  }

  public UsernameRecoveryStartResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    schemes = schemes == null ? List.of() : List.copyOf(schemes);
    switch (outcome) {
      case SESSION_CREATED -> validateSessionCreated(csrfToken, expiresInSeconds, schemes);
      case MEMBER_NOT_FOUND ->
          validateMemberNotFound(authFlowSessionId, csrfToken, expiresInSeconds, schemes);
    }
  }

  private static void validateSessionCreated(
      String csrfToken, Integer expiresInSeconds, List<UsernameRecoverySchemeProjection> schemes) {
    if (csrfToken == null || csrfToken.isBlank()) {
      throw new IllegalArgumentException("session-created result requires a nonblank csrfToken");
    }
    if (expiresInSeconds == null || expiresInSeconds <= 0) {
      throw new IllegalArgumentException(
          "session-created result requires a positive expiresInSeconds");
    }
    if (schemes.isEmpty()) {
      throw new IllegalArgumentException(
          "session-created result requires at least one scheme projection");
    }
    if (schemes.stream().anyMatch(s -> s.schemeRef() == null)) {
      throw new IllegalArgumentException(
          "session-created result requires a schemeRef for every projection");
    }
  }

  private static void validateMemberNotFound(
      AuthFlowSessionId authFlowSessionId,
      String csrfToken,
      Integer expiresInSeconds,
      List<UsernameRecoverySchemeProjection> schemes) {
    if (authFlowSessionId != null
        || !schemes.isEmpty()
        || csrfToken != null
        || expiresInSeconds != null) {
      throw new IllegalArgumentException(
          "member-not-found result must not carry session data or schemes");
    }
  }

  /**
   * Creates a result that carries no session: the outcome and the scheme projections only, with no
   * auth-flow session id, CSRF token or expiry. That is the shape of {@code MEMBER_NOT_FOUND}; the
   * constructor still rejects a {@code SESSION_CREATED} outcome built this way, because it requires
   * session data.
   */
  public static UsernameRecoveryStartResult withoutSession(
      Outcome outcome, List<UsernameRecoverySchemeProjection> schemes) {
    return new UsernameRecoveryStartResult(outcome, null, null, null, schemes);
  }

  @Override
  public String toString() {
    return "UsernameRecoveryStartResult[outcome="
        + outcome
        + ", authFlowSessionId=<redacted>, csrfToken=<redacted>, expiresInSeconds="
        + expiresInSeconds
        + ", schemeCount="
        + schemes.size()
        + "]";
  }
}
