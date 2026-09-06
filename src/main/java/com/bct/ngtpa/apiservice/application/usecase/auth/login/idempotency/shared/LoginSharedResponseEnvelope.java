package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import java.util.Map;
import java.util.Objects;

/** Strict action-tagged JSON envelope for Login safe and transient response bodies. */
public final class LoginSharedResponseEnvelope {
  private final TransientJsonPort json;

  public LoginSharedResponseEnvelope(TransientJsonPort json) {
    this.json = Objects.requireNonNull(json, "json must not be null");
  }

  public String encode(LoginSharedIdempotencyAction action, Object payload) {
    Objects.requireNonNull(action, "Login idempotency action must not be null");
    Objects.requireNonNull(payload, "Login response payload must not be null");
    try {
      return json.write(new WriteWire(action.action().value(), payload));
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  public LoginSharedIdempotencyAction decodeAction(String serializedBody) {
    return action(read(serializedBody).idempotencyAction());
  }

  public <T> T decodePayload(
      LoginSharedIdempotencyAction expectedAction, String serializedBody, Class<T> payloadType) {
    Objects.requireNonNull(expectedAction, "Expected Login idempotency action must not be null");
    Objects.requireNonNull(payloadType, "Login response payload type must not be null");
    ReadWire wire = read(serializedBody);
    if (action(wire.idempotencyAction()) != expectedAction) {
      throw new IllegalArgumentException("Login replay action mismatch");
    }
    try {
      return json.read(json.write(wire.payload()), payloadType);
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  private ReadWire read(String serializedBody) {
    if (serializedBody == null || serializedBody.isBlank()) {
      throw invalid();
    }
    try {
      ReadWire wire = json.read(serializedBody, ReadWire.class);
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

  private LoginSharedIdempotencyAction action(String value) {
    for (LoginSharedIdempotencyAction action : LoginSharedIdempotencyAction.values()) {
      if (action.action().value().equals(value)) {
        return action;
      }
    }
    throw new IllegalArgumentException("Login replay action is unsupported");
  }

  private IllegalArgumentException invalid() {
    return new IllegalArgumentException("Login replay response decoding failed");
  }

  private record WriteWire(String idempotencyAction, Object payload) {}

  private record ReadWire(String idempotencyAction, Map<String, Object> payload) {}
}
