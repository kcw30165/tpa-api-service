package com.bct.ngtpa.apiservice.application.dto;

/**
 * Application-facing record representing a single Config Service entry.
 *
 * <p>Field names ({@code configKey}, {@code configValue}) follow the assumed camelCase
 * naming convention of the Config Service REST API. Verify against the actual service
 * before promoting to production.
 */
public record ConfigEntry(
        String application,
        String profile,
        String label,
        String configKey,
        String configValue) {
}
