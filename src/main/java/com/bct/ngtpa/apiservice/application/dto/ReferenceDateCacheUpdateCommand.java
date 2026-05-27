package com.bct.ngtpa.apiservice.application.dto;

public record ReferenceDateCacheUpdateCommand(
        String cacheKey,
        String cacheValue) {
}