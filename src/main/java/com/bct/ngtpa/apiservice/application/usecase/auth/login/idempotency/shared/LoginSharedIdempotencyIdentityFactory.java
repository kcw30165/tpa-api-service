package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyKey;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Creates context-neutral Login operation identities without exposing raw session scope. */
public final class LoginSharedIdempotencyIdentityFactory {
  private static final IdempotencyCapability CAPABILITY = new IdempotencyCapability("login");

  public IdempotencyOperationId create(
      LoginSharedIdempotencyAction action, String rawAuthorizedScope, String clientKey) {
    Objects.requireNonNull(action, "Login idempotency action must not be null");
    return new IdempotencyOperationId(
        CAPABILITY,
        action.action(),
        new IdempotencyOperationScope(hashRequired(rawAuthorizedScope)),
        new IdempotencyKey(clientKey));
  }

  private String hashRequired(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Login idempotency operation scope must not be blank");
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
