package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;

/**
 * Request to bind an issued OTP challenge to the forgot-password session. The trust and scheme
 * fields are the values the member lookup returned. The stored protected context carries none of
 * them at START, so the binding persists them for resend; a null or blank value keeps what the
 * stored context already holds.
 */
public record ForgotPasswordOtpBindingCommand(
    AuthFlowSessionId sessionId,
    SessionLockHandle mutationLock,
    MessageDeliveryStatus deliveryStatus,
    String otpChallengeId,
    String contactChannel,
    String protectedDestination,
    String maskedDestination,
    Instant challengeExpiresAt,
    Instant resendAvailableAt,
    Instant now,
    String trustCode,
    String schemeName,
    String schemeNameChi) {
  @Override
  public String toString() {
    return "ForgotPasswordOtpBindingCommand[session=<redacted>,lock=<redacted>,deliveryStatus="
        + deliveryStatus
        + ",challenge=<redacted>,destination=<redacted>,timing=<redacted>]";
  }
}
