package com.bct.ngtpa.apiservice.domain.model;

/**
 * Nonnegative Username Recovery session revision value object used for optimistic-concurrency
 * semantics.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 3
 *       (session revision retained as a session field) and section 4 (revision persisted
 *       consistently with candidate state and session status);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 7
 *       (session revision retained as minimal protected data).
 * </ul>
 *
 * <p>Invariants: the revision is never negative and {@link #next()} advances by exactly one. An
 * overflowed advance wraps negative and is therefore rejected by construction rather than silently
 * producing a negative revision.
 */
public record UsernameRecoverySessionRevision(int value) {

  public UsernameRecoverySessionRevision {
    if (value < 0) {
      throw new IllegalArgumentException("session revision must be nonnegative");
    }
  }

  public UsernameRecoverySessionRevision next() {
    return new UsernameRecoverySessionRevision(value + 1);
  }

  @Override
  public String toString() {
    return "UsernameRecoverySessionRevision[value=" + value + "]";
  }
}
