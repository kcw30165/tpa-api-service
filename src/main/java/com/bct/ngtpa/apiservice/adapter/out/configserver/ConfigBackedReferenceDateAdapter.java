package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.config.ReferenceDateProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class ConfigBackedReferenceDateAdapter implements ReferenceDatePort {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReferenceDateProperties properties;

    @Override
    public Mono<LocalDate> resolveReferenceDate(String env) {
        return Mono.fromSupplier(() -> resolve(env));
    }

    private LocalDate resolve(String env) {
        var zoneId = resolveZoneId();
        var today = LocalDate.now(zoneId);
        if (isProduction(env)) {
            return today;
        }

        var override = properties.getNonProdOverride();
        if (override == null || override.isBlank()) {
            return today;
        }

        // TODO: Replace temporary config-backed non-prod override with Config Service API call once Config Service contract is available.
        try {
            return LocalDate.parse(override, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new InvalidContributionRequestException(
                    "reference-date.non-prod-override must use dd/MM/yyyy format");
        }
    }

    private ZoneId resolveZoneId() {
        try {
            return ZoneId.of(properties.getZoneId());
        } catch (RuntimeException ex) {
            throw new InvalidContributionRequestException("reference-date.zone-id is invalid");
        }
    }

    private boolean isProduction(String env) {
        if (env == null || env.isBlank()) {
            return true;
        }

        return "PROD".equalsIgnoreCase(env)
                || "PRD".equalsIgnoreCase(env)
                || "PRODUCTION".equalsIgnoreCase(env);
    }
}