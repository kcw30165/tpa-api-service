package com.bct.ngtpa.apiservice.application.service.session;

import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import java.time.Instant;
import java.util.Objects;

public class SessionTtlCalculator {

  public Instant expiresAt(Instant now, SessionTtlPolicy policy) {
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    if (policy.enabled() && policy.ttlSeconds() <= 0) {
      throw new IllegalArgumentException("Enabled session TTL must be greater than zero");
    }
    return now.plusSeconds(policy.ttlSeconds());
  }
}
