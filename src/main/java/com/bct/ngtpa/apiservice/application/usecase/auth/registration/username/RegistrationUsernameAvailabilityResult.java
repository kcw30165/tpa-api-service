package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

public record RegistrationUsernameAvailabilityResult(
    RegistrationUsernameAvailabilityStatus status, String downstreamErrorCode) {
  public static RegistrationUsernameAvailabilityResult available() {
    return new RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus.AVAILABLE, null);
  }

  public static RegistrationUsernameAvailabilityResult rejected(String downstreamErrorCode) {
    return new RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus.REJECTED, downstreamErrorCode);
  }

  public static RegistrationUsernameAvailabilityResult downstreamFailed() {
    return new RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus.DOWNSTREAM_FAILED, null);
  }
}
