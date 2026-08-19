package com.bct.ngtpa.apiservice.application.dto.workflow;

/** Canonical public errors owned by the login workflow. */
public enum LoginErrorKey implements WorkflowErrorKey {
  REQUEST_INVALID("err.login.request.invalid"),
  CREDENTIALS_INVALID("err.login.credentials.invalid"),
  CREDENTIALS_UNAVAILABLE("err.login.credentials.unavailable"),
  INCORRECT_PASSWORD("err.login.incorrect.password"),
  INCORRECT_USERNAME("err.login.incorrect.username"),
  INVALID_ACCOUNT("err.login.invalid.account"),
  ACCOUNT_LOCKED("err.login.locked.account"),
  AUTH_FLOW_INVALID("err.login.authFlow.invalid"),
  CSRF_INVALID("err.login.csrf.invalid"),
  ORIGIN_INVALID("err.login.origin.invalid"),
  OTP_DESTINATION_INVALID("err.login.otp.destination.invalid"),
  OTP_REQUIRED("err.login.otp.required"),
  OTP_INVALID("err.login.otp.invalid"),
  OTP_EXPIRED("err.login.otp.expired"),
  OTP_COOLDOWN_ACTIVE("err.login.otp.cooldownActive"),
  OTP_DELIVERY_FAILED("err.login.otp.deliveryFailed"),
  OTP_VERIFY_ATTEMPTS_EXCEEDED("err.login.otp.verifyAttemptsExceeded"),
  ACCOUNT_LIST_UNAVAILABLE("err.login.accountList.unavailable"),
  NO_ACCESSIBLE_ACCOUNTS("err.login.accountList.empty"),
  SESSION_CREATION_FAILED("err.login.session.creationFailed");

  private final String value;

  LoginErrorKey(String value) {
    this.value = WorkflowKeyValidator.validate(value, "Invalid login error key");
  }

  @Override
  public String value() {
    return value;
  }
}
