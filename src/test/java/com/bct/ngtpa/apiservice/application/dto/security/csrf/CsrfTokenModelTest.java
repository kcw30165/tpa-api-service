package com.bct.ngtpa.apiservice.application.dto.security.csrf;

import com.bct.ngtpa.apiservice.application.service.security.csrf.CsrfTokenValidator;
import com.bct.ngtpa.apiservice.application.service.security.csrf.Sha256CsrfTokenHasher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfTokenModelTest {

    @Test
    void createCsrfTokenResultCarriesRawTokenAndHash() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-01-01T00:10:00Z");

        var result = new CreateCsrfTokenResult(
                "csrf_raw_token",
                "token_hash",
                createdAt,
                expiresAt,
                600);

        assertThat(result.token()).isEqualTo("csrf_raw_token");
        assertThat(result.tokenHash()).isEqualTo("token_hash");
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void csrfTokenPersistedModelDoesNotContainRawToken() {
        var token = new CsrfToken(
                "workflow-ref-123",
                "token_hash",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"));

        assertThat(token.workflowRef()).isEqualTo("workflow-ref-123");
        assertThat(token.tokenHash()).isEqualTo("token_hash");
        Set<String> componentNames = Arrays.stream(CsrfToken.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        Set<String> accessorNames = Arrays.stream(CsrfToken.class.getDeclaredMethods())
                .filter(method -> method.getParameterCount() == 0)
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        Set<String> forbiddenNames = Set.of("token", "rawToken", "csrfToken", "plainToken");
        forbiddenNames.forEach(name -> {
            assertThat(componentNames).doesNotContain(name);
            assertThat(accessorNames).doesNotContain(name);
        });
    }

    @Test
    void validationStatusDefinesExpectedValues() {
        assertThat(Set.copyOf(Arrays.asList(CsrfTokenValidationStatus.values())))
                .isEqualTo(Set.of(
                        CsrfTokenValidationStatus.VALID,
                        CsrfTokenValidationStatus.MISSING,
                        CsrfTokenValidationStatus.MISMATCH,
                        CsrfTokenValidationStatus.EXPIRED,
                        CsrfTokenValidationStatus.INVALID_INPUT));
    }

    @Test
    void validateTokenReturnsValidWhenHashMatchesAndNotExpired() {
        var hasher = new Sha256CsrfTokenHasher();
        var validator = new CsrfTokenValidator(hasher);
        String token = "csrf_raw_token";
        String hash = hasher.hash(token);

        var result = validator.validate(new ValidateCsrfTokenCommand(
                "workflow-ref-123",
                token,
                hash,
                Instant.parse("2026-01-01T00:01:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"),
                "https://example.com",
                "https://example.com/page",
                "request-123"));

        assertThat(result.valid()).isTrue();
        assertThat(result.status()).isEqualTo(CsrfTokenValidationStatus.VALID);
    }

    @Test
    void validateTokenRejectsExpiredToken() {
        var hasher = new Sha256CsrfTokenHasher();
        var validator = new CsrfTokenValidator(hasher);
        String token = "csrf_raw_token";
        String hash = hasher.hash(token);

        var result = validator.validate(new ValidateCsrfTokenCommand(
                "workflow-ref-123",
                token,
                hash,
                Instant.parse("2026-01-01T00:10:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"),
                null,
                null,
                null));

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo(CsrfTokenValidationStatus.EXPIRED);
    }

    @Test
    void validateTokenRejectsMismatch() {
        var validator = new CsrfTokenValidator(new Sha256CsrfTokenHasher());

        var result = validator.validate(new ValidateCsrfTokenCommand(
                "workflow-ref-123",
                "csrf_submitted",
                new Sha256CsrfTokenHasher().hash("csrf_expected"),
                Instant.parse("2026-01-01T00:01:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"),
                null,
                null,
                null));

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo(CsrfTokenValidationStatus.MISMATCH);
    }

    @Test
    void validateTokenRejectsMissingAndInvalidInput() {
        var validator = new CsrfTokenValidator(new Sha256CsrfTokenHasher());

        var missing = validator.validate(new ValidateCsrfTokenCommand(
                "workflow-ref-123",
                " ",
                "hash",
                Instant.parse("2026-01-01T00:01:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"),
                null,
                null,
                null));
        var invalidInput = validator.validate(new ValidateCsrfTokenCommand(
                "workflow-ref-123",
                "csrf_token",
                "hash",
                null,
                Instant.parse("2026-01-01T00:10:00Z"),
                null,
                null,
                null));

        assertThat(missing.status()).isEqualTo(CsrfTokenValidationStatus.MISSING);
        assertThat(invalidInput.status()).isEqualTo(CsrfTokenValidationStatus.INVALID_INPUT);
    }
}
