package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyKey;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Creates opaque Forgot Password operation identities without exposing workflow scope. */
public final class ForgotPasswordSharedIdempotencyIdentityFactory {
  private static final IdempotencyCapability CAPABILITY =
      new IdempotencyCapability("forgot-password");

  public IdempotencyOperationId create(
      ForgotPasswordSharedIdempotencyAction action,
      String rawAuthorizedScope,
      String clientIdempotencyKey) {
    Objects.requireNonNull(action, "Forgot Password idempotency action must not be null");
    return new IdempotencyOperationId(
        CAPABILITY,
        action.action(),
        new IdempotencyOperationScope(sha256(requireText(rawAuthorizedScope, "operation scope"))),
        new IdempotencyKey(requireText(clientIdempotencyKey, "client idempotency key")));
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Forgot Password " + label + " must not be blank");
    }
    return value.trim();
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Forgot Password operation scope derivation failed");
    }
  }
}
