package com.bct.ngtpa.apiservice.application.dto.downstream;

/**
 * Framework-free classification result for BFF-facing downstream business rejection metadata.
 */
public record DownstreamErrorClassification(
        String eventType,
        String logLevel,
        String responseCode,
        String target,
        boolean idempotencyCacheable,
        Integer cacheTtlSeconds,
        boolean fallbackUsed) {
}
