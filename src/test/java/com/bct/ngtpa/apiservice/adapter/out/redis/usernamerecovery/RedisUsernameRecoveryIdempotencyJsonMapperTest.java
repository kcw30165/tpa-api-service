package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyOperationReference;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.Status;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Group 1: minimal redacted serialization for the Username Recovery idempotency persistence record.
 *
 * <p>Verifies lossless JSON round-trips for IN_PROGRESS/COMPLETED/UNKNOWN_OUTCOME, redacted {@code
 * toString} of the persisted record, and that serialization and deserialization failures are never
 * swallowed: they surface as {@link CacheException} with generic safe messages that never disclose
 * stored content.
 */
class RedisUsernameRecoveryIdempotencyJsonMapperTest {

  private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
  private static final String DIGEST =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String KEY_VALUE = "idem_12345678";
  private static final String OP_REF = "op-1234567890";
  private static final String SNAPSHOT = "snap-opaque-value";

  private final RedisUsernameRecoveryIdempotencyJsonMapper mapper =
      new RedisUsernameRecoveryIdempotencyJsonMapper();

  @Test
  void roundTripsInProgressRecordWithoutReplaySnapshot() {
    var record = record(Status.IN_PROGRESS, null);

    var restored = mapper.fromJson(mapper.toJson(record));

    assertThat(restored).isEqualTo(record);
    assertThat(restored.status()).isEqualTo(Status.IN_PROGRESS);
    assertThat(restored.replaySnapshot()).isNull();
    assertThat(restored.operationReference().value()).isEqualTo(OP_REF);
  }

  @Test
  void roundTripsCompletedRecordWithReplaySnapshot() {
    var record = record(Status.COMPLETED, new ReplaySnapshot(SNAPSHOT));

    var restored = mapper.fromJson(mapper.toJson(record));

    assertThat(restored).isEqualTo(record);
    assertThat(restored.status()).isEqualTo(Status.COMPLETED);
    assertThat(restored.replaySnapshot().value()).isEqualTo(SNAPSHOT);
  }

  @Test
  void roundTripsUnknownOutcomeRecordWithoutReplaySnapshot() {
    var record = record(Status.UNKNOWN_OUTCOME, null);

    var restored = mapper.fromJson(mapper.toJson(record));

    assertThat(restored).isEqualTo(record);
    assertThat(restored.status()).isEqualTo(Status.UNKNOWN_OUTCOME);
    assertThat(restored.replaySnapshot()).isNull();
  }

  @Test
  void serializedJsonNeverRetainsSensitiveRawValues() {
    var json = mapper.toJson(record(Status.IN_PROGRESS, null));

    assertThat(json)
        .contains("\"action\":\"REQUEST\"")
        .contains("\"status\":\"IN_PROGRESS\"")
        .doesNotContain(
            "dateOfBirth",
            "identityValue",
            "email",
            "mobile",
            "userName",
            "csrfToken",
            "csrfHash",
            "auth_flow_sid",
            "schemeRef",
            "trustCode",
            "accountEnv",
            "captchaAnswer",
            "captchaChallengeId",
            "cookie",
            "password",
            "policyNo",
            "certNo");
  }

  @Test
  void persistedRecordToStringRedactsSensitiveValues() {
    var redisRecord =
        new RedisUsernameRecoveryIdempotencyRecord(
            "REQUEST",
            KEY_VALUE,
            DIGEST,
            OP_REF,
            "IN_PROGRESS",
            null,
            NOW.toString(),
            NOW.toString(),
            NOW.plusSeconds(300).toString());

    assertThat(redisRecord.toString())
        .contains("<redacted>")
        .doesNotContain(KEY_VALUE, DIGEST, OP_REF, SNAPSHOT);
  }

  @Test
  void fromJsonRejectsNullAndBlank() {
    assertThatThrownBy(() -> mapper.fromJson(null))
        .isInstanceOf(CacheException.class)
        .hasMessage("Username recovery idempotency state deserialization failed");
    assertThatThrownBy(() -> mapper.fromJson("   "))
        .isInstanceOf(CacheException.class)
        .hasMessage("Username recovery idempotency state deserialization failed");
  }

  @Test
  void fromJsonPropagatesMalformedInputAsSafeCacheFailure() {
    assertThatThrownBy(() -> mapper.fromJson("{not-json"))
        .isInstanceOf(CacheException.class)
        .hasMessage("Username recovery idempotency state deserialization failed")
        .hasMessageNotContaining("{not-json");
  }

  @Test
  void toJsonRejectsNullRecord() {
    assertThatThrownBy(() -> mapper.toJson(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void fromJsonRejectsSemanticallyInvalidStoredStateAsSafeCacheFailure() {
    // COMPLETED without a replay snapshot is invalid per the port record contract.
    var json =
        "{\"action\":\"REQUEST\",\"idempotencyKey\":\""
            + KEY_VALUE
            + "\",\"fingerprint\":\""
            + DIGEST
            + "\",\"operationReference\":\""
            + OP_REF
            + "\",\"status\":\"COMPLETED\",\"createdAt\":\""
            + NOW
            + "\",\"updatedAt\":\""
            + NOW
            + "\",\"expiresAt\":\""
            + NOW.plusSeconds(300)
            + "\"}";

    assertThatThrownBy(() -> mapper.fromJson(json))
        .isInstanceOf(CacheException.class)
        .hasMessage("Username recovery idempotency state deserialization failed");
  }

  private static UsernameRecoveryIdempotencyRecord record(Status status, ReplaySnapshot snapshot) {
    return new UsernameRecoveryIdempotencyRecord(
        UsernameRecoveryIdempotencyAction.REQUEST,
        new UsernameRecoveryIdempotencyKey(KEY_VALUE),
        new UsernameRecoveryIdempotencyFingerprint(DIGEST),
        new UsernameRecoveryIdempotencyOperationReference(OP_REF),
        status,
        snapshot,
        NOW,
        NOW,
        NOW.plusSeconds(300));
  }
}
