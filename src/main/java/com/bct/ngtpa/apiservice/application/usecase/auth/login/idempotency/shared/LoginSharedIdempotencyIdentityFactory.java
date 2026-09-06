package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationIdentityFactory;
import java.util.Objects;

/** Compatibility API supplying Login policy to the central identity factory. */
public final class LoginSharedIdempotencyIdentityFactory {
  private static final IdempotencyCapability CAPABILITY = new IdempotencyCapability("login");
  private final IdempotencyOperationIdentityFactory delegate;

  public LoginSharedIdempotencyIdentityFactory() {
    this(new IdempotencyOperationIdentityFactory());
  }

  LoginSharedIdempotencyIdentityFactory(IdempotencyOperationIdentityFactory delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  public IdempotencyOperationId create(
      LoginSharedIdempotencyAction action, String rawAuthorizedScope, String clientKey) {
    Objects.requireNonNull(action, "Login idempotency action must not be null");
    return delegate.create(CAPABILITY, action.action(), rawAuthorizedScope, clientKey);
  }
}
