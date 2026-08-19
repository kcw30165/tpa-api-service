package com.bct.ngtpa.apiservice.domain.model;

import java.util.Objects;

/**
 * Verified Username Recovery delivery destination and channel value object.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 3
 *       (delivery channel and verified destination session fields) and section 7 (the TAS-verified
 *       destination is retained only in protected context);
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 7
 *       (protected verified destination and channel).
 * </ul>
 *
 * <p>Invariants: the destination value is non-null and nonblank and the channel is non-null. {@link
 * #toString()} never discloses the destination value.
 */
public record UsernameRecoveryVerifiedDestination(String value, Channel channel) {

  public enum Channel {
    EMAIL,
    MOBILE
  }

  public UsernameRecoveryVerifiedDestination {
    Objects.requireNonNull(value, "destination must not be null");
    Objects.requireNonNull(channel, "destination channel must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("destination must not be blank");
    }
  }

  @Override
  public String toString() {
    return "UsernameRecoveryVerifiedDestination[value=<redacted>, channel=" + channel + "]";
  }
}
