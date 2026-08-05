package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordCompleteRequest(String password, String confirmPassword) {
    @Override
    public String toString() {
        return "ForgotPasswordCompleteRequest[password=<redacted>, confirmPassword=<redacted>]";
    }
}