package com.bct.ngtpa.apiservice.adapter.out.redis.auth.registration.replay;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.ProtectedRegistrationStartReplay;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RedisRegistrationStartProtectedReplayJsonMapper {
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public String toJson(ProtectedRegistrationStartReplay value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Registration Start protected replay serialization failed");
    }
  }

  public ProtectedRegistrationStartReplay fromJson(String json) {
    if (json == null || json.isBlank())
      throw new IllegalArgumentException("protected replay JSON must not be blank");
    try {
      return mapper.readValue(json, ProtectedRegistrationStartReplay.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException(
          "Registration Start protected replay deserialization failed");
    }
  }
}
