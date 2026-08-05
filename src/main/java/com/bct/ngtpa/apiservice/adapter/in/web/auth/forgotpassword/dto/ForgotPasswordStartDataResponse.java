package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordStartDataResponse(String csrfToken, String identityType) {
    @Override
    public String toString() {
        return "ForgotPasswordStartDataResponse[csrfToken=<redacted>, identityType=<redacted>]";
    }
}
