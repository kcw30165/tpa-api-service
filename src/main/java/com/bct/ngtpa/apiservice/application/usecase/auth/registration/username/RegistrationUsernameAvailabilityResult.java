package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

public record RegistrationUsernameAvailabilityResult(
    RegistrationUsernameAvailabilityStatus status,
    String downstreamErrorCode,
    RegistrationUsernameAvailabilityFailureOrigin failureOrigin) {
  public RegistrationUsernameAvailabilityResult(
      RegistrationUsernameAvailabilityStatus status, String downstreamErrorCode) {
    this(status, downstreamErrorCode, null);
  }

  public static RegistrationUsernameAvailabilityResult available() {
    return new RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus.AVAILABLE, null, null);
  }

  public static RegistrationUsernameAvailabilityResult rejected(String downstreamErrorCode) {
    return new RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus.REJECTED, downstreamErrorCode, null);
  }

  public static RegistrationUsernameAvailabilityResult downstreamFailed() {
    return new RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus.DOWNSTREAM_FAILED, null, null);
  }

  public static RegistrationUsernameAvailabilityResult downstreamFailed(
      RegistrationUsernameAvailabilityFailureOrigin failureOrigin) {
    return new RegistrationUsernameAvailabilityResult(
        RegistrationUsernameAvailabilityStatus.DOWNSTREAM_FAILED, null, failureOrigin);
  }
}
