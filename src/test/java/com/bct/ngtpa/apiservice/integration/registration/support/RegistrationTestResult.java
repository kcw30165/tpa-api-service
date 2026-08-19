package com.bct.ngtpa.apiservice.integration.registration.support;

public record RegistrationTestResult(
    RegistrationTestStatus status, String actualResult, String reason, String evidence) {

  public RegistrationTestResult {
    status = status == null ? RegistrationTestStatus.NOT_RUN : status;
    actualResult = RegistrationTestSanitizer.sanitize(actualResult);
    reason = RegistrationTestSanitizer.sanitize(reason);
    evidence = evidence == null ? "" : evidence;
  }
}
