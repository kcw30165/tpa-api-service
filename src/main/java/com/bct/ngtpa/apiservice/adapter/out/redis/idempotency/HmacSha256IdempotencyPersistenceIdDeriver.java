package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA-256 persistence locator derivation using a runtime-provided secret. */
public final class HmacSha256IdempotencyPersistenceIdDeriver
    implements IdempotencyPersistenceIdDeriver {
  private static final String ALGORITHM = "HmacSHA256";
  private static final int MINIMUM_SECRET_BYTES = 32;
  private final byte[] secret;

  public HmacSha256IdempotencyPersistenceIdDeriver(byte[] secret) {
    Objects.requireNonNull(secret, "idempotency persistence secret must not be null");
    if (secret.length < MINIMUM_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "idempotency persistence secret must contain at least 32 bytes");
    }
    this.secret = secret.clone();
  }

  @Override
  public String derive(IdempotencyOperationId operationId) {
    Objects.requireNonNull(operationId, "operation id must not be null");
    String canonical =
        operationId.capability().value()
            + '\0'
            + operationId.action().value()
            + '\0'
            + operationId.scope().value()
            + '\0'
            + operationId.key().value();
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Idempotency persistence identity derivation failed");
    }
  }
}
