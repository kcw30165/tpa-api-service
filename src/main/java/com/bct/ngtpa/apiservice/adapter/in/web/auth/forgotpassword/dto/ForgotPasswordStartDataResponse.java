package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordStartDataResponse(String csrfToken) {
    @Override
    public String toString() {
        return "ForgotPasswordStartDataResponse[csrfToken=<redacted>]";
    }
}