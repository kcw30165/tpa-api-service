package com.bct.ngtpa.apiservice.application.port.out.authflow;

/**
 * Marker capability for the existing generic OTP challenge core.
 *
 * <p>Operations remain owned by {@code OtpChallengePort}; this marker prevents login/recovery code
 * from introducing a second OTP orchestration API.
 */
public interface AuthFlowOtpPort {}
