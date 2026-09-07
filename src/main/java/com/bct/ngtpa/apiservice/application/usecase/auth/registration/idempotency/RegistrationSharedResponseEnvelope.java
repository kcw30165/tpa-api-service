package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.JacksonTransientJsonCodec;
import com.bct.ngtpa.apiservice.application.idempotency.ActionTaggedIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyAction;
import java.util.Objects;

/** Registration action-mapping compatibility API over the central envelope protocol. */
public final class RegistrationSharedResponseEnvelope {
  private final ActionTaggedIdempotencyResponseEnvelope delegate;

  public RegistrationSharedResponseEnvelope() {
    this(
        new ActionTaggedIdempotencyResponseEnvelope(
            new JacksonTransientJsonCodec()));
  }

  RegistrationSharedResponseEnvelope(ActionTaggedIdempotencyResponseEnvelope delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  public String encode(RegistrationSharedIdempotencyAction action, Object publicPayload) {
    Objects.requireNonNull(action, "registration idempotency action must not be null");
    Objects.requireNonNull(publicPayload, "registration public payload must not be null");
    try {
      return delegate.encode(new IdempotencyAction(action.value()), publicPayload);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Registration replay response encoding failed");
    }
  }

  public RegistrationSharedIdempotencyAction decodeAction(String serializedSafeBody) {
    String value;
    try {
      value = delegate.decodeAction(serializedSafeBody);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Registration replay response decoding failed");
    }
    for (RegistrationSharedIdempotencyAction action : RegistrationSharedIdempotencyAction.values()) {
      if (action.value().equals(value)) return action;
    }
    throw new IllegalArgumentException("Registration replay action is unsupported");
  }
}
