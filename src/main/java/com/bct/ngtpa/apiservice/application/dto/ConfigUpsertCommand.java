package com.bct.ngtpa.apiservice.application.dto;

/**
 * Application-facing command for creating or updating a Config Service entry.
 *
 * <p>All five fields are required by the Config Service. Validation is delegated
 * to the Config Service ({@code @Valid} on its {@code ConfigRequest}) which
 * returns a 400 that the adapter surfaces as a {@code ConfigServiceException}.
 *
 * <p>Field names ({@code configKey}, {@code configValue}) follow the assumed
 * camelCase convention. Verify against the actual service before promoting to
 * production.
 */
public record ConfigUpsertCommand(
        String application,
        String profile,
        String label,
        String configKey,
        String configValue) {
}
