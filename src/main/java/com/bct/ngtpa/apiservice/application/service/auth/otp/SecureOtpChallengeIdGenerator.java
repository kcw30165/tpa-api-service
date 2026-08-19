package com.bct.ngtpa.apiservice.application.service.auth.otp;

import java.util.UUID;

public final class SecureOtpChallengeIdGenerator implements OtpChallengeIdGenerator {

  @Override
  public String generate() {
    return "otp_" + UUID.randomUUID().toString().replace("-", "");
  }
}
