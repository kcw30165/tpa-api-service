package com.bct.ngtpa.apiservice.adapter.out.apim.oauth;

public record ApimTokenCacheEntry(
        String profileId,
        String accessToken,
        String tokenType,
        long expiresAtMillis,
        String refreshToken
) {}
