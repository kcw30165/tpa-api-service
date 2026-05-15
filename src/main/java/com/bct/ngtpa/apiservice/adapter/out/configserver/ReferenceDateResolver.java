package com.bct.ngtpa.apiservice.adapter.out.configserver;

import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class ReferenceDateResolver {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Clock clock;

    public ReferenceDateResolver() {
        this(Clock.systemDefaultZone());
    }

    ReferenceDateResolver(Clock clock) {
        this.clock = clock;
    }

    public LocalDate resolve(String accountEnv, String overrideDate, String overrideZoneId) {
        if (isProductionLikeAccountEnv(accountEnv)) {
            return resolveServerDate();
        }

        if (isBlank(overrideDate) && isBlank(overrideZoneId)) {
            return resolveServerDate();
        }
        if (isBlank(overrideDate) || isBlank(overrideZoneId)) {
            throw new InvalidContributionRequestException(
                    "reference-date.override-date and reference-date.override-zone-id must be provided together");
        }

        validateOverrideZoneId(overrideZoneId);

        try {
            return LocalDate.parse(overrideDate, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new InvalidContributionRequestException(
                    "reference-date.override-date must use dd/MM/yyyy format");
        }
    }

    private LocalDate resolveServerDate() {
        return LocalDate.now(clock);
    }

    private void validateOverrideZoneId(String overrideZoneId) {
        try {
            ZoneId.of(overrideZoneId);
        } catch (RuntimeException ex) {
            throw new InvalidContributionRequestException("reference-date.override-zone-id is invalid");
        }
    }

    private boolean isProductionLikeAccountEnv(String accountEnv) {
        if (accountEnv == null || accountEnv.isBlank()) {
            return true;
        }

        return "PROD".equalsIgnoreCase(accountEnv)
                || "PRD".equalsIgnoreCase(accountEnv)
                || "PRODUCTION".equalsIgnoreCase(accountEnv)
                || "DR".equalsIgnoreCase(accountEnv);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}