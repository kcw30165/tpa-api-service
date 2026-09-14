package com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay;

import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.ProtectedUsernameRecoveryStartReplay;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RedisUsernameRecoveryStartProtectedReplayJsonMapper {
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public String toJson(ProtectedUsernameRecoveryStartReplay value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Username Recovery START replay serialization failed");
    }
  }

  public ProtectedUsernameRecoveryStartReplay fromJson(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("Username Recovery START replay JSON is missing");
    }
    try {
      return mapper.readValue(json, ProtectedUsernameRecoveryStartReplay.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Username Recovery START replay deserialization failed");
    }
  }
}
