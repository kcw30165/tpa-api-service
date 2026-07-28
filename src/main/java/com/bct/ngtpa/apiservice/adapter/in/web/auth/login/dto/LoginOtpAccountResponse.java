package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

/**
 * Safe account-selection projection returned after successful Login OTP
 * verification.
 */
public record LoginOtpAccountResponse(
                String accountRef,
                String policyNo,
                String certNo,
                String accountEnv,
                String trustCode,
                String schemeType) {
}
