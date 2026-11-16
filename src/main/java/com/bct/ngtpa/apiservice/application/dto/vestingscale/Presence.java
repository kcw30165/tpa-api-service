package com.bct.ngtpa.apiservice.application.dto.vestingscale;

import java.util.Objects;

public record Presence<T>(State state, T value) {

  public Presence {
    Objects.requireNonNull(state, "state must not be null");
    if (state == State.ABSENT && value != null) {
      throw new IllegalArgumentException("absent presence cannot carry a value");
    }
  }

  public static <T> Presence<T> absent() {
    return new Presence<>(State.ABSENT, null);
  }

  public static <T> Presence<T> present(T value) {
    return new Presence<>(State.PRESENT, value);
  }

  public boolean isPresent() {
    return state == State.PRESENT;
  }

  public enum State {
    ABSENT,
    PRESENT
  }
}
