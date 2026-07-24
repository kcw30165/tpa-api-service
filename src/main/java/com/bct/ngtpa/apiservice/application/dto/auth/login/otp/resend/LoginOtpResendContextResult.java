package com.bct.ngtpa.apiservice.application.dto.auth.login.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.LoginOtpChannel;

public record LoginOtpResendContextResult(LoginOtpResendContextStatus status, String activeOtpChallengeId,
        LoginOtpChannel channel, String destination, String maskedDestination) {
    public static LoginOtpResendContextResult valid(String id, LoginOtpChannel c, String d, String m) {
        return new LoginOtpResendContextResult(LoginOtpResendContextStatus.VALID, id, c, d, m);
    }

    public static LoginOtpResendContextResult invalid(LoginOtpResendContextStatus s) {
        return new LoginOtpResendContextResult(s, null, null, null, null);
    }

    public boolean valid() {
        return status == LoginOtpResendContextStatus.VALID;
    }

    @Override
    public String toString() {
        return "LoginOtpResendContextResult[status=" + status + ", activeOtpChallengeId=<redacted>, channel=" + channel
                + ", destination=<redacted>, maskedDestination=<redacted>]";
    }
}
