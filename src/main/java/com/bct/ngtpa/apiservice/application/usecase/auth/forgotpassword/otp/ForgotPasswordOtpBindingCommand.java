package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
public record ForgotPasswordOtpBindingCommand(AuthFlowSessionId sessionId,SessionLockHandle mutationLock,
        MessageDeliveryStatus deliveryStatus,String otpChallengeId,String contactChannel,
        String protectedDestination,String maskedDestination,Instant challengeExpiresAt,
        Instant resendAvailableAt,Instant now) {
    @Override public String toString(){return "ForgotPasswordOtpBindingCommand[session=<redacted>,lock=<redacted>,deliveryStatus="+deliveryStatus+",challenge=<redacted>,destination=<redacted>,timing=<redacted>]";}
}
