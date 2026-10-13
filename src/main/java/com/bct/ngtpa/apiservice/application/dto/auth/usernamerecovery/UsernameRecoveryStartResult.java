package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.List;
import java.util.Objects;

/**
 * Application result of the Username Recovery start flow.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4 (successful
 * start returns {@code csrfToken}, {@code expiresInSeconds}, and safe {@code schemes};
 * member-not-found and zero-registered start return {@code RESTART_FLOW}) and {@code
 * docs/design/6-username-recovery/username-recovery-solution-design.md} section 6 (session, CSRF,
 * and opaque Scheme-Refs are created only when at least one registered scheme exists).
 *
 * <p>Invariants: {@code outcome} is required. {@code SESSION_CREATED} requires a nonblank {@code
 * csrfToken}, a positive {@code expiresInSeconds}, at least one scheme projection including at
 * least one registered projection, and a session-bound schemeRef on every projection. {@code
 * ZERO_REGISTERED} requires at least one unregistered projection with a null schemeRef and carries
 * no session data. {@code MEMBER_NOT_FOUND} carries no session data and no schemes. {@link
 * #toString()} never discloses the CSRF token or the auth-flow session id.
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
    MEMBER_NOT_FOUND,
    ZERO_REGISTERED
  }

  public UsernameRecoveryStartResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    schemes = schemes == null ? List.of() : List.copyOf(schemes);
    switch (outcome) {
      case SESSION_CREATED -> {
        if (csrfToken == null || csrfToken.isBlank()) {
          throw new IllegalArgumentException(
              "session-created result requires a nonblank csrfToken");
        }
        if (expiresInSeconds == null || expiresInSeconds <= 0) {
          throw new IllegalArgumentException(
              "session-created result requires a positive expiresInSeconds");
        }
        if (schemes.isEmpty()) {
          throw new IllegalArgumentException(
              "session-created result requires at least one scheme projection");
        }
        if (schemes.stream().noneMatch(UsernameRecoverySchemeProjection::registered)) {
          throw new IllegalArgumentException(
              "session-created result requires at least one registered scheme");
        }
        if (schemes.stream().anyMatch(s -> s.schemeRef() == null)) {
          throw new IllegalArgumentException(
              "session-created result requires a schemeRef for every projection");
        }
      }
      case MEMBER_NOT_FOUND -> {
        if (authFlowSessionId != null
            || !schemes.isEmpty()
            || csrfToken != null
            || expiresInSeconds != null) {
          throw new IllegalArgumentException(
              "member-not-found result must not carry session data or schemes");
        }
      }
      case ZERO_REGISTERED -> {
        if (authFlowSessionId != null || csrfToken != null || expiresInSeconds != null) {
          throw new IllegalArgumentException("zero-registered result must not carry session data");
        }
        if (schemes.isEmpty()) {
          throw new IllegalArgumentException(
              "zero-registered result requires at least one scheme projection");
        }
        if (schemes.stream().anyMatch(UsernameRecoverySchemeProjection::registered)) {
          throw new IllegalArgumentException(
              "zero-registered result must not contain registered schemes");
        }
        if (schemes.stream().anyMatch(s -> s.schemeRef() != null)) {
          throw new IllegalArgumentException(
              "zero-registered result projections must not carry schemeRef");
        }
      }
    }
  }

  /**
   * Creates a result that carries no session: the outcome and the scheme projections only, with no
   * auth-flow session id, CSRF token or expiry. That is the shape of {@code MEMBER_NOT_FOUND} and
   * {@code ZERO_REGISTERED}; the constructor still rejects a {@code SESSION_CREATED} outcome built
   * this way, because it requires session data.
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
