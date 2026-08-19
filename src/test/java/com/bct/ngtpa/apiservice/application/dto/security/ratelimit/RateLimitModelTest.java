package com.bct.ngtpa.apiservice.application.dto.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RateLimitModelTest {

  @Test
  void rateLimitBucketDefinesRegistrationBucketsOnly() {
    assertEquals(
        Set.of(
            RateLimitBucket.REGISTRATION_START_BY_IP,
            RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH,
            RateLimitBucket.OTP_VERIFY_BY_REGISTRATION_SESSION),
        Set.copyOf(Arrays.asList(RateLimitBucket.values())));
  }

  @Test
  void rateLimitStatusDefinesAllowedBlockedDisabled() {
    assertEquals(
        Set.of(RateLimitStatus.ALLOWED, RateLimitStatus.BLOCKED, RateLimitStatus.DISABLED),
        Set.copyOf(Arrays.asList(RateLimitStatus.values())));
  }

  @Test
  void rateLimitCommandCarriesPolicyAndBucketFields() {
    var command =
        new RateLimitCommand(
            RateLimitBucket.REGISTRATION_START_BY_IP,
            "opaque-bucket-id",
            true,
            10,
            600,
            "request-123");

    assertEquals(RateLimitBucket.REGISTRATION_START_BY_IP, command.bucket());
    assertEquals("opaque-bucket-id", command.bucketId());
    assertTrue(command.enabled());
    assertEquals(10, command.maxAttempts());
    assertEquals(600, command.windowSeconds());
    assertEquals("request-123", command.correlationId());
  }

  @Test
  void rateLimitCommandRejectsInvalidEnabledPolicyFields() {
    assertThrows(
        NullPointerException.class, () -> new RateLimitCommand(null, "opaque", true, 1, 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitCommand(RateLimitBucket.REGISTRATION_START_BY_IP, " ", true, 1, 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitCommand(
                RateLimitBucket.REGISTRATION_START_BY_IP, "opaque", true, 0, 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitCommand(
                RateLimitBucket.REGISTRATION_START_BY_IP, "opaque", true, 1, 0, null));
  }

  @Test
  void rateLimitCommandAllowsDisabledPolicyWithoutBucketIdOrPositiveLimit() {
    var command =
        new RateLimitCommand(RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH, null, false, 0, 0, null);

    assertFalse(command.enabled());
    assertNull(command.bucketId());
    assertEquals(0, command.maxAttempts());
    assertEquals(0, command.windowSeconds());
  }

  @Test
  void rateLimitCommandUsesOpaqueBucketIdOnly() {
    Set<String> componentNames =
        Arrays.stream(RateLimitCommand.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());
    Set<String> accessorNames =
        Arrays.stream(RateLimitCommand.class.getDeclaredMethods())
            .filter(method -> method.getParameterCount() == 0)
            .map(method -> method.getName())
            .collect(Collectors.toSet());

    Set<String> forbiddenNames =
        Set.of(
            "email",
            "mobile",
            "phone",
            "phoneNumber",
            "ipAddress",
            "registrationSessionId",
            "redisKey",
            "key");

    assertTrue(componentNames.contains("bucketId"));
    forbiddenNames.forEach(
        name -> {
          assertFalse(
              componentNames.contains(name),
              "Forbidden RateLimitCommand record component: " + name);
          assertFalse(accessorNames.contains(name), "Forbidden RateLimitCommand accessor: " + name);
        });
  }

  @Test
  void rateLimitResultCarriesDecisionFields() {
    Instant resetAt = Instant.parse("2026-01-01T00:10:00Z");
    var result =
        new RateLimitResult(
            RateLimitStatus.ALLOWED, RateLimitBucket.REGISTRATION_START_BY_IP, 10, 9, 0, resetAt);

    assertEquals(RateLimitStatus.ALLOWED, result.status());
    assertEquals(RateLimitBucket.REGISTRATION_START_BY_IP, result.bucket());
    assertEquals(10, result.limit());
    assertEquals(9, result.remaining());
    assertEquals(0, result.retryAfterSeconds());
    assertEquals(resetAt, result.resetAt());
  }

  @Test
  void rateLimitResultCanRepresentAllowedDecision() {
    var result =
        RateLimitResult.allowed(
            RateLimitBucket.REGISTRATION_START_BY_IP, 10, 9, Instant.parse("2026-01-01T00:10:00Z"));

    assertEquals(RateLimitStatus.ALLOWED, result.status());
    assertTrue(result.remaining() >= 0);
    assertEquals(0, result.retryAfterSeconds());
  }

  @Test
  void rateLimitResultCanRepresentBlockedDecision() {
    var result =
        RateLimitResult.blocked(
            RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH,
            3,
            120,
            Instant.parse("2026-01-01T00:10:00Z"));

    assertEquals(RateLimitStatus.BLOCKED, result.status());
    assertEquals(0, result.remaining());
    assertTrue(result.retryAfterSeconds() > 0);
  }

  @Test
  void rateLimitResultCanRepresentDisabledDecision() {
    var result = RateLimitResult.disabled(RateLimitBucket.OTP_VERIFY_BY_REGISTRATION_SESSION);

    assertEquals(RateLimitStatus.DISABLED, result.status());
    assertEquals(0, result.limit());
    assertEquals(0, result.remaining());
    assertEquals(0, result.retryAfterSeconds());
    assertNull(result.resetAt());
  }

  @Test
  void rateLimitResultRejectsInvalidDecisionValues() {
    assertThrows(
        NullPointerException.class,
        () -> new RateLimitResult(null, RateLimitBucket.REGISTRATION_START_BY_IP, 1, 0, 0, null));
    assertThrows(
        NullPointerException.class,
        () -> new RateLimitResult(RateLimitStatus.ALLOWED, null, 1, 0, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitResult(
                RateLimitStatus.ALLOWED, RateLimitBucket.REGISTRATION_START_BY_IP, -1, 0, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitResult(
                RateLimitStatus.BLOCKED, RateLimitBucket.REGISTRATION_START_BY_IP, 1, 0, 0, null));
  }
}
