package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginDestinationMasker;

public final class ForgotPasswordDestinationMasker {
  private final LoginDestinationMasker delegate = new LoginDestinationMasker();

  public String mask(String channel, String destination) {
    return delegate.mask(channel, destination);
  }

  @Override
  public String toString() {
    return "ForgotPasswordDestinationMasker[delegate=<redacted>]";
  }
}
