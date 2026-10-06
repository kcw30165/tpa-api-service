package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyPersistenceId;
import org.junit.jupiter.api.Test;

class RedisLoginIdempotencyKeyFactoryTest {

  private static final String VALID_PERSISTENCE_ID_VALUE = "a".repeat(43);

  @Test
  void keyForBuildsNamespacedKeyFromActionAndPersistenceId() {
    var factory = new RedisLoginIdempotencyKeyFactory("ngtpa");
    var id = new LoginIdempotencyPersistenceId(VALID_PERSISTENCE_ID_VALUE);
    String key = factory.keyFor(LoginIdempotencyAction.CREDENTIALS, id);
    assertThat(key).isEqualTo("ngtpa:auth:idempotency:login:credentials:" + VALID_PERSISTENCE_ID_VALUE);
  }

  @Test
  void constructorStripsTrailingColonFromPrefix() {
    var factory = new RedisLoginIdempotencyKeyFactory("ngtpa:");
    var id = new LoginIdempotencyPersistenceId(VALID_PERSISTENCE_ID_VALUE);
    String key = factory.keyFor(LoginIdempotencyAction.OTP_SEND, id);
    assertThat(key).isEqualTo("ngtpa:auth:idempotency:login:otp-send:" + VALID_PERSISTENCE_ID_VALUE);
  }

  @Test
  void constructorRejectsNullPrefix() {
    assertThatThrownBy(() -> new RedisLoginIdempotencyKeyFactory(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructorRejectsBlankPrefix() {
    assertThatThrownBy(() -> new RedisLoginIdempotencyKeyFactory("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keyForRejectsNullAction() {
    var factory = new RedisLoginIdempotencyKeyFactory("ngtpa");
    var id = new LoginIdempotencyPersistenceId(VALID_PERSISTENCE_ID_VALUE);
    assertThatThrownBy(() -> factory.keyFor(null, id))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keyForRejectsNullPersistenceId() {
    var factory = new RedisLoginIdempotencyKeyFactory("ngtpa");
    assertThatThrownBy(() -> factory.keyFor(LoginIdempotencyAction.CREDENTIALS, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
