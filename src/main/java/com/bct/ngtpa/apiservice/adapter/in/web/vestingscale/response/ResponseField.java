package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import com.fasterxml.jackson.annotation.JsonValue;

public record ResponseField<T>(boolean present, T value) {

  public ResponseField {
    if (!present && value != null) {
      throw new IllegalArgumentException("an absent response field cannot carry a value");
    }
  }

  public static <T> ResponseField<T> absent() {
    return new ResponseField<>(false, null);
  }

  public static <T> ResponseField<T> present(T value) {
    return new ResponseField<>(true, value);
  }

  @JsonValue
  public T jsonValue() {
    return value;
  }

  public static final class AbsentFilter {

    @Override
    public boolean equals(Object other) {
      return other instanceof ResponseField<?> field && !field.present();
    }
  }
}
