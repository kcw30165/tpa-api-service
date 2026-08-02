package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordOtpVerifyRequest(String code) {
    @Override
    public String toString() {
        return "ForgotPasswordOtpVerifyRequest[code=<redacted>]";
    }
}