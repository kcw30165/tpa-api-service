package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

public record SimpleWebError(String code, String target, String message) {
    public SimpleWebError(String code, String target) {
        this(code, target, null);
    }
}
