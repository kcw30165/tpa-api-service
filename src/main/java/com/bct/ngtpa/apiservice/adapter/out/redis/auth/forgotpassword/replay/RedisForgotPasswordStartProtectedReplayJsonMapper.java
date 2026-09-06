package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword.replay;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared.ProtectedForgotPasswordStartReplay;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class RedisForgotPasswordStartProtectedReplayJsonMapper {
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public String toJson(ProtectedForgotPasswordStartReplay value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Forgot Password Start protected replay serialization failed");
    }
  }

  public ProtectedForgotPasswordStartReplay fromJson(String json) {
    if (json == null || json.isBlank()) throw new IllegalArgumentException("protected replay JSON must not be blank");
    try {
      return mapper.readValue(json, ProtectedForgotPasswordStartReplay.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Forgot Password Start protected replay deserialization failed");
    }
  }
}
