package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.util.Objects;

final class RedisAuthFlowSessionKeyFactory {
  private static final String AUTH_CAPABILITY = "auth";
  private static final String FLOW_SESSION_NAMESPACE = "flow-session";

  private RedisAuthFlowSessionKeyFactory() {}

  static String keyFor(RedisCacheKeyFactory keyFactory, AuthFlowSessionId authFlowSessionId) {
    Objects.requireNonNull(keyFactory, "keyFactory must not be null");
    Objects.requireNonNull(authFlowSessionId, "authFlowSessionId must not be null");
    return keyFactory.key(AUTH_CAPABILITY, FLOW_SESSION_NAMESPACE, authFlowSessionId.value());
  }
}
