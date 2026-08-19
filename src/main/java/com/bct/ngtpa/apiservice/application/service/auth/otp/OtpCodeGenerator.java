package com.bct.ngtpa.apiservice.application.service.auth.otp;

public interface OtpCodeGenerator {

  String generate(int length, boolean numericOnly);
}
