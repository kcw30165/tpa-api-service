package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyReplaySnapshot;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordRequestHash;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;

public final class RedisForgotPasswordIdempotencyJsonMapper {
  private final ObjectMapper objectMapper;

  public RedisForgotPasswordIdempotencyJsonMapper() {
    this(defaultObjectMapper());
  }

  public RedisForgotPasswordIdempotencyJsonMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String toJson(ForgotPasswordIdempotencyRecord record) {
    try {
      return objectMapper.writeValueAsString(toRedisRecord(record));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize forgot password idempotency record");
    }
  }

  public ForgotPasswordIdempotencyRecord fromJson(String json) {
    try {
      return fromRedisRecord(
          objectMapper.readValue(json, RedisForgotPasswordIdempotencyRecord.class));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to deserialize forgot password idempotency record");
    }
  }

  private static RedisForgotPasswordIdempotencyRecord toRedisRecord(
      ForgotPasswordIdempotencyRecord record) {
    RedisForgotPasswordIdempotencyReplaySnapshotRecord snapshot = null;
    if (record.replaySnapshot() != null) {
      snapshot =
          new RedisForgotPasswordIdempotencyReplaySnapshotRecord(
              record.replaySnapshot().outcomeCode(),
              record.replaySnapshot().opaqueCommitRef(),
              record.replaySnapshot().safeHeaders(),
              record.replaySnapshot().identityType());
    }
    return new RedisForgotPasswordIdempotencyRecord(
        record.action().name(),
        record.idempotencyKey().value(),
        record.requestHash().value(),
        record.status().name(),
        snapshot,
        record.createdAt().toString(),
        record.updatedAt().toString(),
        record.expiresAt().toString(),
        record.pendingOpaqueCommitRef());
  }

  private static ForgotPasswordIdempotencyRecord fromRedisRecord(
      RedisForgotPasswordIdempotencyRecord record) {
    ForgotPasswordIdempotencyReplaySnapshot snapshot = null;
    if (record.replaySnapshot() != null) {
      snapshot =
          new ForgotPasswordIdempotencyReplaySnapshot(
              record.replaySnapshot().outcomeCode(),
              record.replaySnapshot().opaqueCommitRef(),
              record.replaySnapshot().safeHeaders() == null
                  ? Map.of()
                  : record.replaySnapshot().safeHeaders(),
              record.replaySnapshot().identityType());
    }
    return new ForgotPasswordIdempotencyRecord(
        ForgotPasswordIdempotencyAction.valueOf(record.action()),
        new ForgotPasswordIdempotencyKey(record.idempotencyKey()),
        new ForgotPasswordRequestHash(record.requestHash()),
        ForgotPasswordIdempotencyStatus.valueOf(record.status()),
        snapshot,
        Instant.parse(record.createdAt()),
        Instant.parse(record.updatedAt()),
        Instant.parse(record.expiresAt()),
        record.pendingOpaqueCommitRef());
  }

  private static ObjectMapper defaultObjectMapper() {
    return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
