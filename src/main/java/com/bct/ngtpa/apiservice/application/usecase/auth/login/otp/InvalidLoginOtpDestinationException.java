package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

public final class InvalidLoginOtpDestinationException extends RuntimeException {
  public InvalidLoginOtpDestinationException() {
    super("Selected login OTP destination is invalid");
  }
}
