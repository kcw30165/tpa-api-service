package com.bct.ngtpa.apiservice.adapter.out.redis.idempotency;

import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Strict schema-aware JSON boundary with sanitized failures. */
public final class RedisIdempotencyJsonMapper {
  private final ObjectMapper mapper;

  public RedisIdempotencyJsonMapper() {
    this(new ObjectMapper());
  }

  RedisIdempotencyJsonMapper(ObjectMapper mapper) {
    this.mapper =
        Objects.requireNonNull(mapper)
            .copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public String toJson(IdempotencyRecord value) {
    Objects.requireNonNull(value, "idempotency record must not be null");
    try {
      return mapper.writeValueAsString(toWire(value));
    } catch (RuntimeException | JsonProcessingException exception) {
      throw new CacheException("Shared idempotency state serialization failed", exception);
    }
  }

  public IdempotencyRecord fromJson(String json) {
    if (json == null || json.isBlank()) {
      throw new CacheException("Shared idempotency state deserialization failed");
    }
    try {
      RedisIdempotencyRecord wire = mapper.readValue(json, RedisIdempotencyRecord.class);
      if (wire.schemaVersion() != RedisIdempotencyRecord.CURRENT_SCHEMA_VERSION) {
        throw new IllegalArgumentException("unsupported shared idempotency schema");
      }
      return toDomain(wire);
    } catch (RuntimeException | JsonProcessingException exception) {
      throw new CacheException("Shared idempotency state deserialization failed", exception);
    }
  }

  private static RedisIdempotencyRecord toWire(IdempotencyRecord value) {
    IdempotentTerminalResponseSnapshot snapshot = value.terminalResponse();
    RedisIdempotencyRecord.RedisTerminalResponse terminal =
        snapshot == null
            ? null
            : new RedisIdempotencyRecord.RedisTerminalResponse(
                snapshot.schemaVersion(),
                snapshot.httpStatus(),
                snapshot.mediaType(),
                snapshot.serializedSafeBody(),
                snapshot.safeHeaders(),
                snapshot.protectedReplayReference() == null
                    ? null
                    : snapshot.protectedReplayReference().value());
    return new RedisIdempotencyRecord(
        1,
        value.operationId().capability().value(),
        value.operationId().action().value(),
        value.operationId().scope().value(),
        value.operationId().key().value(),
        value.fingerprint().version(),
        value.fingerprint().value(),
        value.reservationToken().value(),
        value.status().name(),
        terminal,
        value.createdAt().toString(),
        value.updatedAt().toString(),
        value.expiresAt().toString());
  }

  private static IdempotencyRecord toDomain(RedisIdempotencyRecord wire) {
    var operation =
        new IdempotencyOperationId(
            new IdempotencyCapability(wire.capability()), new IdempotencyAction(wire.action()),
            new IdempotencyOperationScope(wire.operationScope()),
                new IdempotencyKey(wire.idempotencyKey()));
    IdempotentTerminalResponseSnapshot terminal = null;
    if (wire.terminalResponse() != null) {
      var response = wire.terminalResponse();
      terminal =
          new IdempotentTerminalResponseSnapshot(
              response.schemaVersion(),
              response.httpStatus(),
              response.mediaType(),
              response.serializedSafeBody(),
              response.safeHeaders() == null ? Map.of() : response.safeHeaders(),
              response.protectedReplayReference() == null
                  ? null
                  : new ProtectedReplayReference(response.protectedReplayReference()));
    }
    return new IdempotencyRecord(
        operation,
        new IdempotencyFingerprint(wire.fingerprintVersion(), wire.fingerprint()),
        new IdempotencyReservationToken(wire.reservationToken()),
        IdempotencyStatus.valueOf(wire.status()),
        terminal,
        Instant.parse(wire.createdAt()),
        Instant.parse(wire.updatedAt()),
        Instant.parse(wire.expiresAt()));
  }
}
