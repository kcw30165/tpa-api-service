package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Objects;

/**
 * Auth-flow session selected-trust value. The registration username/check contract only stores
 * trustCode and accountEnv.
 */
public record SelectedTrust(String trustCode, String accountEnv) {
  public SelectedTrust {
    trustCode = requiredText(trustCode, "trustCode");
    accountEnv = requiredText(accountEnv, "accountEnv");
  }

  private static String requiredText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
