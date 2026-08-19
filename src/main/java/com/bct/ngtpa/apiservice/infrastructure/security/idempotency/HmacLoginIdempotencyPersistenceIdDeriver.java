package com.bct.ngtpa.apiservice.infrastructure.security.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacLoginIdempotencyPersistenceIdDeriver
    implements LoginIdempotencyPersistenceIdDeriver {
  private static final String NS = "ngtpa:login-idempotency:v1";
  private final byte[] secret;

  public HmacLoginIdempotencyPersistenceIdDeriver(byte[] secret) {
    if (secret == null || secret.length < 32)
      throw new IllegalArgumentException("Login idempotency HMAC secret is missing or weak");
    this.secret = secret.clone();
  }

  public LoginIdempotencyPersistenceId derive(
      LoginIdempotencyAction action, LoginIdempotencyKey key) {
    Objects.requireNonNull(action);
    Objects.requireNonNull(key);
    try {
      Mac m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(secret, "HmacSHA256"));
      String in = NS + ":" + action.name() + ":" + key.value();
      return new LoginIdempotencyPersistenceId(
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(m.doFinal(in.getBytes(StandardCharsets.UTF_8))));
    } catch (Exception e) {
      throw new IllegalStateException("Login idempotency persistence identity unavailable");
    }
  }

  @Override
  public String toString() {
    return "HmacLoginIdempotencyPersistenceIdDeriver[configured=true]";
  }
}
