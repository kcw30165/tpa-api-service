package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ProtectedForgotPasswordStartReplay;
import org.junit.jupiter.api.Test;

class RedisForgotPasswordStartProtectedReplayJsonMapperTest {
  private final RedisForgotPasswordStartProtectedReplayJsonMapper mapper =
      new RedisForgotPasswordStartProtectedReplayJsonMapper();

  @Test
  void roundTripsStrictWireRecord() {
    var value = new ProtectedForgotPasswordStartReplay(1, "v1", "AES/GCM/NoPadding", "iv", "ciphertext");
    assertThat(mapper.fromJson(mapper.toJson(value))).isEqualTo(value);
  }

  @Test
  void rejectsUnknownFieldsMalformedJsonAndUnsupportedSchema() {
    assertThatThrownBy(() -> mapper.fromJson("{\"schemaVersion\":1,\"keyVersion\":\"v1\",\"algorithm\":\"AES/GCM/NoPadding\",\"iv\":\"iv\",\"ciphertext\":\"cipher\",\"extra\":true}"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.fromJson("not-json")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.fromJson("{\"schemaVersion\":2,\"keyVersion\":\"v1\",\"algorithm\":\"AES/GCM/NoPadding\",\"iv\":\"iv\",\"ciphertext\":\"cipher\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
