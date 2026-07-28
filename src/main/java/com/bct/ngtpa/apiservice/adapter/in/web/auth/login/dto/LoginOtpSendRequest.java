package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

public record LoginOtpSendRequest(String destinationId) {
    @Override
    public String toString() {
        return "LoginOtpSendRequest[destinationId=<redacted>]";
    }
}
