package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyAction;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import java.util.Objects;

/** Compatibility API supplying Registration policy to the central identity factory. */
public final class RegistrationSharedIdempotencyIdentityFactory {
  private static final IdempotencyCapability CAPABILITY = new IdempotencyCapability("registration");
  private final IdempotencyOperationIdentityFactory delegate;

  public RegistrationSharedIdempotencyIdentityFactory() {
    this(new IdempotencyOperationIdentityFactory());
  }

  RegistrationSharedIdempotencyIdentityFactory(IdempotencyOperationIdentityFactory delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  public IdempotencyOperationId create(
      RegistrationSharedIdempotencyAction action, String clientIdempotencyKey, String rawOperationScope) {
    Objects.requireNonNull(action, "registration idempotency action must not be null");
    return delegate.create(CAPABILITY, new IdempotencyAction(action.value()), rawOperationScope, clientIdempotencyKey);
  }
}
