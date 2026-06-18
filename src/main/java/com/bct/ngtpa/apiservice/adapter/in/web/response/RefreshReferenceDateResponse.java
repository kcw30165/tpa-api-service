package com.bct.ngtpa.apiservice.adapter.in.web.response;

public record RefreshReferenceDateResponse(
        String accountEnv,
        String refDate,
        boolean configServiceUpdated,
        boolean redisUpdated) {
}