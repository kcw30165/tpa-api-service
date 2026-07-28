package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

public record LoginStartRequest(String username, String password) {
    @Override
    public String toString() {
        return "LoginStartRequest[username=<redacted>, password=<redacted>]";
    }
}
