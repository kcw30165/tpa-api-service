package com.bct.ngtpa.apiservice.application.dto;

/**
 * Application-facing query record for listing Config Service entries.
 * All fields are optional; null or blank values are excluded from the outbound query.
 */
public record ConfigQuery(
        String application,
        String profile,
        String label,
        String configKey) {
}
