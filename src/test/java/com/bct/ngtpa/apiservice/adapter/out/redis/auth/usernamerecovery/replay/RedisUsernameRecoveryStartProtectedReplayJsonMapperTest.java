package com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.ProtectedUsernameRecoveryStartReplay;
import org.junit.jupiter.api.Test;

class RedisUsernameRecoveryStartProtectedReplayJsonMapperTest {

  private final RedisUsernameRecoveryStartProtectedReplayJsonMapper mapper =
      new RedisUsernameRecoveryStartProtectedReplayJsonMapper();

  @Test
  void roundTripsStrictWireRecord() {
    var value =
        new ProtectedUsernameRecoveryStartReplay(1, "v1", "AES/GCM/NoPadding", "iv", "ciphertext");
    assertThat(mapper.fromJson(mapper.toJson(value))).isEqualTo(value);
  }

  @Test
  void rejectsUnknownFieldsMalformedJsonAndUnsupportedSchema() {
    assertThatThrownBy(
            () ->
                mapper.fromJson(
                    "{\"schemaVersion\":1,\"keyVersion\":\"v1\",\"algorithm\":\"AES/GCM/NoPadding\",\"iv\":\"iv\",\"ciphertext\":\"cipher\",\"extra\":true}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.fromJson("not-json"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                mapper.fromJson(
                    "{\"schemaVersion\":2,\"keyVersion\":\"v1\",\"algorithm\":\"AES/GCM/NoPadding\",\"iv\":\"iv\",\"ciphertext\":\"cipher\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromJsonRejectsNullOrBlankJson() {
    assertThatThrownBy(() -> mapper.fromJson(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.fromJson("   ")).isInstanceOf(IllegalArgumentException.class);
  }
}
