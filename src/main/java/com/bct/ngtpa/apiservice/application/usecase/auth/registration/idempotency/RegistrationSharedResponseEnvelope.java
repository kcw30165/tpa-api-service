package com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/** Strict action-tagged JSON envelope for Registration replay bodies. */
public final class RegistrationSharedResponseEnvelope {
  private final ObjectMapper mapper;

  public RegistrationSharedResponseEnvelope() {
    this.mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public String encode(RegistrationSharedIdempotencyAction action, Object publicPayload) {
    Objects.requireNonNull(action, "registration idempotency action must not be null");
    Objects.requireNonNull(publicPayload, "registration public payload must not be null");
    try {
      return mapper.writeValueAsString(new WriteWire(action.value(), publicPayload));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration replay response encoding failed");
    }
  }

  public RegistrationSharedIdempotencyAction decodeAction(String serializedSafeBody) {
    if (serializedSafeBody == null || serializedSafeBody.isBlank()) {
      throw new IllegalArgumentException("Registration replay response decoding failed");
    }
    try {
      ReadWire wire = mapper.readValue(serializedSafeBody, ReadWire.class);
      if (wire.idempotencyAction() == null
          || wire.idempotencyAction().isBlank()
          || wire.payload() == null) {
        throw new IllegalArgumentException("Registration replay response decoding failed");
      }
      for (RegistrationSharedIdempotencyAction action :
          RegistrationSharedIdempotencyAction.values()) {
        if (action.value().equals(wire.idempotencyAction())) {
          return action;
        }
      }
      throw new IllegalArgumentException("Registration replay action is unsupported");
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Registration replay response decoding failed");
    }
  }

  private record WriteWire(String idempotencyAction, Object payload) {}

  private record ReadWire(String idempotencyAction, JsonNode payload) {}
}
