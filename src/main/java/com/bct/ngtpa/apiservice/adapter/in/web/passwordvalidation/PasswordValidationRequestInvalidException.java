package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

final class PasswordValidationRequestInvalidException extends RuntimeException {
  private final String target;

  PasswordValidationRequestInvalidException(String target) {
    super("Invalid password-validation request");
    this.target = target;
  }

  String target() {
    return target;
  }
}
