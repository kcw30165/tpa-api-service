package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import java.util.Objects;

/** Compatibility API supplying Login policy to the central identity factory. */
public final class LoginIdempotencyIdentityFactory {
  private static final IdempotencyCapability CAPABILITY = new IdempotencyCapability("login");
  private final IdempotencyOperationIdentityFactory delegate;

  public LoginIdempotencyIdentityFactory() {
    this(new IdempotencyOperationIdentityFactory());
  }

  LoginIdempotencyIdentityFactory(IdempotencyOperationIdentityFactory delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  public IdempotencyOperationId create(
      LoginIdempotencyAction action, String rawAuthorizedScope, String clientKey) {
    Objects.requireNonNull(action, "Login idempotency action must not be null");
    return delegate.create(CAPABILITY, action.action(), rawAuthorizedScope, clientKey);
  }
}
