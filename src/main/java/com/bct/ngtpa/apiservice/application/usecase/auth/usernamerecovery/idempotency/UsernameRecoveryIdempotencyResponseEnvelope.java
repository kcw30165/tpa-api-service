package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import java.util.Objects;

/** Strict Username Recovery action envelope delegated to the central response envelope. */
public final class UsernameRecoveryIdempotencyResponseEnvelope {
  private final ActionTaggedIdempotencyResponseEnvelope delegate;

  public UsernameRecoveryIdempotencyResponseEnvelope(TransientJsonPort json) {
    this.delegate = new ActionTaggedIdempotencyResponseEnvelope(Objects.requireNonNull(json));
  }

  public String encode(UsernameRecoveryIdempotencyAction action, Object payload) {
    Objects.requireNonNull(action, "Username Recovery action must not be null");
    return delegate.encode(action.action(), payload);
  }

  public <T> T decode(UsernameRecoveryIdempotencyAction action, String body, Class<T> payloadType) {
    Objects.requireNonNull(action, "Username Recovery action must not be null");
    return delegate.decodePayload(action.action(), body, payloadType);
  }
}
