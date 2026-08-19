package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaAnswerHash;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaStatus;
import java.time.Instant;

record RedisCaptchaChallengeRecord(
    String captchaChallengeId,
    String answerHash,
    String status,
    int failedAttemptCount,
    int maxAttempts,
    String createdAt,
    String expiresAt,
    String verifiedAt,
    String consumedAt,
    String refreshedAt,
    String lockedAt) {

  static RedisCaptchaChallengeRecord from(CaptchaChallenge challenge) {
    return new RedisCaptchaChallengeRecord(
        challenge.captchaChallengeId().value(),
        challenge.answerHash() == null ? null : challenge.answerHash().value(),
        challenge.status().name(),
        challenge.failedAttemptCount(),
        challenge.maxAttempts(),
        instantToString(challenge.createdAt()),
        instantToString(challenge.expiresAt()),
        instantToString(challenge.verifiedAt()),
        instantToString(challenge.consumedAt()),
        instantToString(challenge.refreshedAt()),
        instantToString(challenge.lockedAt()));
  }

  CaptchaChallenge toCaptchaChallenge() {
    return new CaptchaChallenge(
        new CaptchaChallengeId(captchaChallengeId),
        answerHash == null ? null : new CaptchaAnswerHash(answerHash),
        CaptchaStatus.valueOf(status),
        failedAttemptCount,
        maxAttempts,
        instantFromString(createdAt),
        instantFromString(expiresAt),
        instantFromString(verifiedAt),
        instantFromString(consumedAt),
        instantFromString(refreshedAt),
        instantFromString(lockedAt));
  }

  @Override
  public String toString() {
    return "RedisCaptchaChallengeRecord[captchaChallengeId=<redacted>, answerHash=<redacted>, status="
        + status
        + ", failedAttemptCount="
        + failedAttemptCount
        + ", maxAttempts="
        + maxAttempts
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + ", verifiedAt="
        + verifiedAt
        + ", consumedAt="
        + consumedAt
        + ", refreshedAt="
        + refreshedAt
        + ", lockedAt="
        + lockedAt
        + "]";
  }

  private static String instantToString(Instant instant) {
    return instant == null ? null : instant.toString();
  }

  private static Instant instantFromString(String value) {
    return value == null || value.isBlank() ? null : Instant.parse(value);
  }
}
