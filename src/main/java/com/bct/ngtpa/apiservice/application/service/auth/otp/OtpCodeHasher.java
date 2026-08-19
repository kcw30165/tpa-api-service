package com.bct.ngtpa.apiservice.application.service.auth.otp;

public interface OtpCodeHasher {

  String hash(String rawCode);

  boolean matches(String rawCode, String storedHash);
}
