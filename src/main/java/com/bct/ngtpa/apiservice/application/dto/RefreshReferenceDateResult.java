package com.bct.ngtpa.apiservice.application.dto;

public record RefreshReferenceDateResult(
        String accountEnv,
        String refDate,
        boolean redisUpdated) {
}