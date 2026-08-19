package com.bct.ngtpa.apiservice.application.dto.message;

public record SenderResult(
    SenderStatus status, String providerMessageId, String failureCode, String failureMessage) {}
