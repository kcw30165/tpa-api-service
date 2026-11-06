package com.bct.ngtpa.apiservice.domain.passwordvalidation;

public enum PasswordValidationPurpose {
  PERSONAL_INFORMATION_UPDATE,
  CHANGE_INVESTMENT_CHOICE;

  public static PasswordValidationPurpose parse(String value) {
    if (PERSONAL_INFORMATION_UPDATE.name().equals(value)) {
      return PERSONAL_INFORMATION_UPDATE;
    }
    throw new IllegalArgumentException("Unsupported password-validation purpose");
  }
}
