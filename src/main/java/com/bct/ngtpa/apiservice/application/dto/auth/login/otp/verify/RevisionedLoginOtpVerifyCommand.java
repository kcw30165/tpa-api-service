package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import java.time.Instant;

public record RevisionedLoginOtpVerifyCommand(
    SessionLockHandle mutationLock,
    AuthFlowSessionSnapshot expectedSnapshot,
    String expectedChallengeId,
    String reservationHash,
    AuthenticatedLoginHandoffReference handoffReference,
    LoginOtpVerificationOperationReference operationReference,
    RedactedSubmittedOtp submittedOtp,
    Instant verificationTime,
    SessionRevision proposedRevision) {
  public RevisionedLoginOtpVerifyCommand {
    if (mutationLock == null
        || !mutationLock.acquired()
        || expectedSnapshot == null
        || expectedChallengeId == null
        || reservationHash == null
        || handoffReference == null
        || operationReference == null
        || submittedOtp == null
        || verificationTime == null
        || proposedRevision == null)
      throw new IllegalArgumentException("complete revisioned verify command required");
  }

  @Override
  public String toString() {
    return "RevisionedLoginOtpVerifyCommand[<redacted>]";
  }
}
