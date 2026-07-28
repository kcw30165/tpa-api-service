package com.bct.ngtpa.apiservice.application.dto.downstream;

/**
 * Framework-free request for classifying a technically valid downstream
 * business rejection.
 */
public record DownstreamErrorClassificationRequest(
        String pageId,
        String source,
        String endpoint,
        String downstreamErrorCode) {

    public DownstreamErrorClassificationRequest {
        pageId = requireText(pageId, "pageId");
        source = requireText(source, "source");
        endpoint = requireText(endpoint, "endpoint");
        downstreamErrorCode = requireText(downstreamErrorCode, "downstreamErrorCode");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
