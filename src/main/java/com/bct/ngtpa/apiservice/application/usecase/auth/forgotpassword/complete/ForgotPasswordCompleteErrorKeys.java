package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

final class ForgotPasswordCompleteErrorKeys {
  static final String AUTH_FLOW_INVALID = "err.forgot-password.authFlow.invalid";
  static final String CSRF_INVALID = "err.forgot-password.csrf.invalid";
  static final String REQUEST_INVALID = "err.forgot-password.complete.requestInvalid";
  static final String PASSWORD_REQUIRED = "err.forgot-password.password.required";
  static final String CONFIRM_PASSWORD_REQUIRED = "err.forgot-password.confirmPassword.required";
  static final String PASSWORD_MISMATCH = "err.forgot-password.password.mismatch";
  static final String PASSWORD_MIN_LENGTH = "err.forgot-password.password.minLength";
  static final String PASSWORD_MAX_LENGTH = "err.forgot-password.password.maxLength";
  static final String PASSWORD_SPACES = "err.forgot-password.password.spaces";
  static final String PASSWORD_SAME_AS_USERNAME = "err.forgot-password.password.sameAsUsername";
  static final String PASSWORD_CATEGORIES = "err.forgot-password.password.categories";
  static final String PASSWORD_INVALID = "err.tpa.password.invalid";
  static final String PASSWORD_REUSED = "err.tpa.password.used.in.last.5.changes.";
  static final String MEMBER_NOT_FOUND = "err.tpa.member.not.found";
  static final String SYSTEM_UNAVAILABLE = "err.system.unavailable";

  private ForgotPasswordCompleteErrorKeys() {}
}
