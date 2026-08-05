package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

public record ForgotPasswordStartRequest(String username) {
    @Override
    public String toString() {
        return "ForgotPasswordStartRequest[username=<redacted>]";
    }
}