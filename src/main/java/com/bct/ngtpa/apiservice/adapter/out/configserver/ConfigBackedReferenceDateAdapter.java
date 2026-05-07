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
    public Mono<LocalDate> resolveReferenceDate() {
        return Mono.fromSupplier(this::resolve);
    }

    private LocalDate resolve() {
        if (isProductionLikeDeployment(properties.getDeploymentEnv())) {
            return resolveServerDate();
        }

        var overrideDate = properties.getOverrideDate();
        var overrideZoneId = properties.getOverrideZoneId();
        if (isBlank(overrideDate) && isBlank(overrideZoneId)) {
            return resolveServerDate();
        }
        if (isBlank(overrideDate) || isBlank(overrideZoneId)) {
            throw new InvalidContributionRequestException(
                    "reference-date.override-date and reference-date.override-zone-id must be provided together");
        }

        var zoneId = resolveOverrideZoneId(overrideZoneId);

        // TODO: Replace temporary config-backed non-prod override pair with Config Service API call once Config Service contract is available.
        try {
            return LocalDate.parse(overrideDate, DATE_FORMATTER)
                    .atStartOfDay(zoneId)
                    .toLocalDate();
        } catch (DateTimeParseException ex) {
            throw new InvalidContributionRequestException(
                    "reference-date.override-date must use dd/MM/yyyy format");
        }
    }

    private LocalDate resolveServerDate() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    private ZoneId resolveOverrideZoneId(String overrideZoneId) {
        try {
            return ZoneId.of(overrideZoneId);
        } catch (RuntimeException ex) {
            throw new InvalidContributionRequestException("reference-date.override-zone-id is invalid");
        }
    }

    private boolean isProductionLikeDeployment(String deploymentEnv) {
        if (deploymentEnv == null || deploymentEnv.isBlank()) {
            return true;
        }

        return "PROD".equalsIgnoreCase(deploymentEnv)
                || "PRD".equalsIgnoreCase(deploymentEnv)
                || "PRODUCTION".equalsIgnoreCase(deploymentEnv)
                || "DR".equalsIgnoreCase(deploymentEnv);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}