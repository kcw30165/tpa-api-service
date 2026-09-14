package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisIdempotencyPersistenceFoundationTest {
  private static final byte[] SECRET_A =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final byte[] SECRET_B =
      "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);

  @Test
  void keyUsesDeterministicHmacAndHidesLocatorComponents() {
    var operation = operation();
    String key = keys(SECRET_A).keyFor(operation);
    assertThat(key)
        .matches("ngtpa:idempotency:v1:[0-9a-f]{64}")
        .doesNotContain("profile", "update-details", "opaqueScope01", "requestKey01");
    assertThat(key).isEqualTo(keys(SECRET_A).keyFor(operation));
    assertThat(key).isNotEqualTo(keys(SECRET_B).keyFor(operation));
  }

  @Test
  void secretConfigurationRejectsMissingMalformedPlaceholderAndShortValues() {
    assertThatThrownBy(() -> IdempotencyPersistenceSecretFactory.decode(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("null");
    assertThatThrownBy(() -> IdempotencyPersistenceSecretFactory.decode("change-me"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("change-me");
    assertThatThrownBy(() -> IdempotencyPersistenceSecretFactory.decode("not-base64***"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining("not-base64");
    String shortSecret =
        Base64.getEncoder().encodeToString("short-secret".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> IdempotencyPersistenceSecretFactory.decode(shortSecret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(shortSecret);
    assertThat(
            IdempotencyPersistenceSecretFactory.decode(
                Base64.getEncoder().encodeToString(SECRET_A)))
        .containsExactly(SECRET_A);
  }

  @Test
  void mapperRoundTripsCompletedSafeSnapshot() {
    var mapper = new RedisIdempotencyJsonMapper();
    var record = completedRecord();
    assertThat(mapper.fromJson(mapper.toJson(record))).isEqualTo(record);
  }

  @Test
  void wireRepresentationsRedactProtectedValues() throws Exception {
    var mapper = new RedisIdempotencyJsonMapper();
    var record = completedRecord();
    String json = mapper.toJson(record);
    var wire =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(json, RedisIdempotencyRecord.class);
    assertThat(wire.toString())
        .doesNotContain(
            "opaqueScope01",
            "requestKey01",
            "a".repeat(64),
            "reservationToken0123456789abcdefgh",
            "protectedReplay01",
            "success");
    assertThat(wire.terminalResponse().toString())
        .doesNotContain("protectedReplay01", "success", "false");
  }

  @Test
  void mapperRejectsUnknownSchemaAndMalformedJsonWithoutDisclosure() {
    var mapper = new RedisIdempotencyJsonMapper();
    assertThatThrownBy(() -> mapper.fromJson("{\"schemaVersion\":2}"))
        .isInstanceOf(CacheException.class)
        .hasMessage("Shared idempotency state deserialization failed")
        .hasMessageNotContaining("schemaVersion");
    assertThatThrownBy(() -> mapper.fromJson("{not-json"))
        .isInstanceOf(CacheException.class)
        .hasMessageNotContaining("{not-json");
  }

  private static RedisIdempotencyKeyFactory keys(byte[] secret) {
    return new RedisIdempotencyKeyFactory(
        new RedisCacheKeyFactory("ngtpa"), new HmacSha256IdempotencyPersistenceIdDeriver(secret));
  }

  private static IdempotencyRecord completedRecord() {
    var now = Instant.parse("2026-09-02T00:00:00Z");
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            1,
            422,
            "application/json",
            "{\"success\":false}",
            Map.of("Content-Language", "en"),
            new ProtectedReplayReference("protectedReplay01"));
    return new IdempotencyRecord(
        operation(),
        new IdempotencyFingerprint(1, "a".repeat(64)),
        new IdempotencyReservationToken("reservationToken0123456789abcdefgh"),
        IdempotencyStatus.COMPLETED,
        snapshot,
        now,
        now,
        now.plusSeconds(300));
  }

  private static IdempotencyOperationId operation() {
    return new IdempotencyOperationId(
        new IdempotencyCapability("profile"), new IdempotencyAction("update-details"),
        new IdempotencyOperationScope("opaqueScope01"), new IdempotencyKey("requestKey01"));
  }
}
