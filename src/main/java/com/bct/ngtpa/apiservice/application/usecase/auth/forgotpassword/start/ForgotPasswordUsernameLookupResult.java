package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;

public record ForgotPasswordUsernameLookupResult(
    ForgotPasswordUsernameLookupStatus status,
    String customerId,
    String userId,
    MemberIdentityType identityType,
    FailureOrigin failureOrigin) {

  public static ForgotPasswordUsernameLookupResult confirmed(
      String customerId, String userId, MemberIdentityType identityType) {
    return new ForgotPasswordUsernameLookupResult(
        ForgotPasswordUsernameLookupStatus.CONFIRMED, customerId, userId, identityType, null);
  }

  public static ForgotPasswordUsernameLookupResult notFound() {
    return new ForgotPasswordUsernameLookupResult(
        ForgotPasswordUsernameLookupStatus.NOT_FOUND, null, null, null, null);
  }

  public static ForgotPasswordUsernameLookupResult systemFailed() {
    return systemFailed(FailureOrigin.DOWNSTREAM_TECHNICAL);
  }

  public static ForgotPasswordUsernameLookupResult systemFailed(FailureOrigin failureOrigin) {
    return new ForgotPasswordUsernameLookupResult(
        ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED, null, null, null, failureOrigin);
  }

  @Override
  public String toString() {
    return "ForgotPasswordUsernameLookupResult[status="
        + status
        + ", customerId=<redacted>, userId=<redacted>, identityType="
        + "<redacted>"
        + "]";
  }
}
