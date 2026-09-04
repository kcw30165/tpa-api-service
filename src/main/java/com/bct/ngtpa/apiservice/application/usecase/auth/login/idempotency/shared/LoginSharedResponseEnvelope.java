package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/** Strict action-tagged JSON envelope for Login safe and transient response bodies. */
public final class LoginSharedResponseEnvelope {
  private final ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public String encode(LoginSharedIdempotencyAction action, Object payload) {
    Objects.requireNonNull(action, "Login idempotency action must not be null");
    Objects.requireNonNull(payload, "Login response payload must not be null");
    try {
      return mapper.writeValueAsString(new WriteWire(action.action().value(), payload));
    } catch (JsonProcessingException exception) {
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
      return mapper.treeToValue(wire.payload(), payloadType);
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw invalid();
    }
  }

  private ReadWire read(String serializedBody) {
    if (serializedBody == null || serializedBody.isBlank()) {
      throw invalid();
    }
    try {
      ReadWire wire = mapper.readValue(serializedBody, ReadWire.class);
      if (wire.idempotencyAction() == null
          || wire.idempotencyAction().isBlank()
          || wire.payload() == null
          || wire.payload().isNull()) {
        throw invalid();
      }
      return wire;
    } catch (JsonProcessingException exception) {
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

  private record ReadWire(String idempotencyAction, JsonNode payload) {}
}
