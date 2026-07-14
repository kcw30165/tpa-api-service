package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record CompleteRegistrationRequest(String password, String confirmPassword) {
    @Override
    public String toString() {
        return "CompleteRegistrationRequest[password=<redacted>, confirmPassword=<redacted>]";
    }
}
