package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyAction;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyKey;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Registration-owned mapping into the context-neutral shared operation identity. */
public final class RegistrationSharedIdempotencyIdentityFactory {
  private static final IdempotencyCapability CAPABILITY = new IdempotencyCapability("registration");

  public IdempotencyOperationId create(
      RegistrationSharedIdempotencyAction action,
      String clientIdempotencyKey,
      String rawOperationScope) {
    Objects.requireNonNull(action, "registration idempotency action must not be null");
    return new IdempotencyOperationId(
        CAPABILITY,
        new IdempotencyAction(action.value()),
        new IdempotencyOperationScope(opaqueScope(rawOperationScope)),
        new IdempotencyKey(requireText(clientIdempotencyKey, "client idempotency key")));
  }

  private static String opaqueScope(String rawScope) {
    return sha256(requireText(rawScope, "registration operation scope"));
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value.trim();
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Registration idempotency scope derivation failed");
    }
  }
}
