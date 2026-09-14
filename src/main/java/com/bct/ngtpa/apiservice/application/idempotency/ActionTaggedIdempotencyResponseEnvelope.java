package com.bct.ngtpa.apiservice.application.idempotency;

import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import java.util.Map;
import java.util.Objects;

/** Central strict action-tagged JSON envelope for safe replay payloads. */
public final class ActionTaggedIdempotencyResponseEnvelope {
  private final TransientJsonPort json;

  public ActionTaggedIdempotencyResponseEnvelope(TransientJsonPort json) {
    this.json = Objects.requireNonNull(json, "json must not be null");
  }

  public String encode(IdempotencyAction action, Object payload) {
    Objects.requireNonNull(action, "idempotency action must not be null");
    Objects.requireNonNull(payload, "idempotency response payload must not be null");
    try {
      return json.write(new WriteWire(action.value(), payload));
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  public <T> T decodePayload(
      IdempotencyAction expectedAction, String serializedBody, Class<T> payloadType) {
    Objects.requireNonNull(expectedAction, "expected idempotency action must not be null");
    Objects.requireNonNull(payloadType, "idempotency payload type must not be null");
    ReadWire wire = read(serializedBody);
    if (!expectedAction.value().equals(wire.idempotencyAction())) {
      throw new IllegalArgumentException("idempotency replay action mismatch");
    }
    try {
      return json.read(json.write(wire.payload()), payloadType);
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  public String decodeAction(String serializedBody) {
    return read(serializedBody).idempotencyAction();
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

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("idempotency replay response decoding failed");
  }

  private record WriteWire(String idempotencyAction, Object payload) {}

  private record ReadWire(String idempotencyAction, Map<String, Object> payload) {}
}
