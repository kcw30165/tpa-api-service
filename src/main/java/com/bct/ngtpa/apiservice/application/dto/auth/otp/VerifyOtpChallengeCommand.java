package com.bct.ngtpa.apiservice.application.dto.auth.otp;

import java.time.Instant;

public record VerifyOtpChallengeCommand(String otpChallengeId, String code, Instant now) {}
