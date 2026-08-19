package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import java.util.Objects;

final class RedisCaptchaChallengeKeyFactory {

  private static final String AUTH_CAPABILITY = "auth";
  private static final String CAPTCHA_NAMESPACE = "captcha";

  private RedisCaptchaChallengeKeyFactory() {}

  static String keyFor(RedisCacheKeyFactory keyFactory, CaptchaChallengeId captchaChallengeId) {
    Objects.requireNonNull(keyFactory, "keyFactory must not be null");
    Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
    return keyFactory.key(AUTH_CAPABILITY, CAPTCHA_NAMESPACE, captchaChallengeId.value());
  }
}
