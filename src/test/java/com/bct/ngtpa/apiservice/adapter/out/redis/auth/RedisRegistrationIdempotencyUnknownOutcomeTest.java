package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RedisRegistrationIdempotencyUnknownOutcomeTest {

  @Test
  void mapperRoundTripsUnknownOutcomeWithoutSnapshot() {
    Instant now = Instant.parse("2026-07-14T00:00:00Z");
    var record =
        new RegistrationIdempotencyRecord(
            RegistrationIdempotencyAction.REGISTRATION_COMPLETE,
            new RegistrationIdempotencyKey("idem_safe"),
            new RegistrationRequestHash("a".repeat(64)),
            RegistrationIdempotencyStatus.UNKNOWN_OUTCOME,
            null,
            now,
            now,
            now.plusSeconds(300));
    var mapper = new RedisRegistrationIdempotencyJsonMapper();

    String json = mapper.toJson(record);

    assertThat(mapper.fromJson(json).status())
        .isEqualTo(RegistrationIdempotencyStatus.UNKNOWN_OUTCOME);
    assertThat(json).doesNotContain("password", "confirmPassword", "auth_flow_sid");
  }
}
