package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Objects;

@JsonDeserialize(using = ApimFieldDeserializer.class)
public final class ApimField<T> {

  private static final ApimField<?> ABSENT = new ApimField<>(State.ABSENT, null);
  private static final ApimField<?> NULL = new ApimField<>(State.NULL, null);

  private final State state;
  private final T value;

  private ApimField(State state, T value) {
    this.state = Objects.requireNonNull(state, "state must not be null");
    this.value = value;
  }

  @SuppressWarnings("unchecked")
  public static <T> ApimField<T> absent() {
    return (ApimField<T>) ABSENT;
  }

  @SuppressWarnings("unchecked")
  public static <T> ApimField<T> nullValue() {
    return (ApimField<T>) NULL;
  }

  public static <T> ApimField<T> value(T value) {
    return new ApimField<>(State.VALUE, Objects.requireNonNull(value, "value must not be null"));
  }

  public State state() {
    return state;
  }

  public T value() {
    return value;
  }

  public enum State {
    ABSENT,
    NULL,
    VALUE
  }
}
