package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.shared;

import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import java.util.Map;
import java.util.Objects;

/** Strict action-tagged envelope shared by later action-specific response bridges. */
public final class ForgotPasswordSharedResponseEnvelope {
  private final TransientJsonPort json;

  public ForgotPasswordSharedResponseEnvelope(TransientJsonPort json) {
    this.json = Objects.requireNonNull(json, "json must not be null");
  }

  public String encode(ForgotPasswordSharedIdempotencyAction action, Object payload) {
    Objects.requireNonNull(action, "Forgot Password idempotency action must not be null");
    Objects.requireNonNull(payload, "Forgot Password response payload must not be null");
    try {
      return json.write(new WriteWire(action.action().value(), payload));
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  public <T> T decodePayload(
      ForgotPasswordSharedIdempotencyAction expected,
      String serializedBody,
      Class<T> payloadType) {
    Objects.requireNonNull(expected, "Expected Forgot Password action must not be null");
    Objects.requireNonNull(payloadType, "Forgot Password payload type must not be null");
    ReadWire wire = read(serializedBody);
    if (!expected.action().value().equals(wire.idempotencyAction())) {
      throw new IllegalArgumentException("Forgot Password replay action mismatch");
    }
    try {
      return json.read(json.write(wire.payload()), payloadType);
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  private ReadWire read(String body) {
    if (body == null || body.isBlank()) throw invalid();
    try {
      ReadWire wire = json.read(body, ReadWire.class);
      if (wire.idempotencyAction() == null
          || wire.idempotencyAction().isBlank()
          || wire.payload() == null) {
        throw invalid();
      }
      return wire;
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  private IllegalArgumentException invalid() {
    return new IllegalArgumentException("Forgot Password replay response decoding failed");
  }

  private record WriteWire(String idempotencyAction, Object payload) {}

  private record ReadWire(String idempotencyAction, Map<String, Object> payload) {}
}
