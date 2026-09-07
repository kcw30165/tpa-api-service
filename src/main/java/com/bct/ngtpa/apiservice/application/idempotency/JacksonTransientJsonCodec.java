package com.bct.ngtpa.apiservice.application.idempotency;

import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/** Application-owned transient JSON codec for internal idempotency envelopes. */
public final class JacksonTransientJsonCodec implements TransientJsonPort {
  private final ObjectMapper mapper;

  public JacksonTransientJsonCodec() {
    this(new ObjectMapper());
  }

  JacksonTransientJsonCodec(ObjectMapper mapper) {
    this.mapper =
        Objects.requireNonNull(mapper, "mapper must not be null")
            .copy()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Override
  public String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("transient JSON serialization failed", exception);
    }
  }

  @Override
  public <T> T read(String json, Class<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("transient JSON deserialization failed", exception);
    }
  }
}
