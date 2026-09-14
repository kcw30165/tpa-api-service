package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyKey;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyAction;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCapability;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyKey;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationScope;
import com.bct.ngtpa.apiservice.infrastructure.security.idempotency.HmacLoginIdempotencyPersistenceIdDeriver;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LoginStartIdempotencyNamespaceIsolationTest {
  private static final byte[] SHARED_SECRET =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final byte[] LEGACY_SECRET =
      "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);

  @Test
  void sharedAndLegacyLoginStartKeysAreOpaqueAndCannotCollide() {
    String rawClientKey = "client-key-sensitive";
    String rawScope = "login-start-anonymous-v1";
    var operation =
        new IdempotencyOperationId(
            new IdempotencyCapability("login"),
            new IdempotencyAction("credentials"),
            new IdempotencyOperationScope("opaqueScopeDerivedFromAnonymousV1"),
            new IdempotencyKey(rawClientKey));
    String shared =
        new RedisIdempotencyKeyFactory(
                new RedisCacheKeyFactory("ngtpa"),
                new HmacSha256IdempotencyPersistenceIdDeriver(SHARED_SECRET))
            .keyFor(operation);

    var legacyId =
        new HmacLoginIdempotencyPersistenceIdDeriver(LEGACY_SECRET)
            .derive(LoginIdempotencyAction.CREDENTIALS, new LoginIdempotencyKey(rawClientKey));
    String legacy =
        new RedisLoginIdempotencyKeyFactory("ngtpa")
            .keyFor(LoginIdempotencyAction.CREDENTIALS, legacyId);

    assertThat(shared).matches("ngtpa:idempotency:v1:[0-9a-f]{64}");
    assertThat(legacy).startsWith("ngtpa:auth:idempotency:login:credentials:");
    assertThat(shared).isNotEqualTo(legacy);
    assertThat(shared + legacy)
        .doesNotContain(
            rawClientKey, rawScope, "member.abc", "Exact Password", "afs_secret", "csrf_secret");
  }
}
