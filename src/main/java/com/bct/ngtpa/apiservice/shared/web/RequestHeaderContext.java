package com.bct.ngtpa.apiservice.shared.web;

public record RequestHeaderContext(
        String accountRef,
        String requestId,
        String language) {
}