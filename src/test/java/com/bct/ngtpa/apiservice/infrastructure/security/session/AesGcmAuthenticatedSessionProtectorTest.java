package com.bct.ngtpa.apiservice.infrastructure.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class AesGcmAuthenticatedSessionProtectorTest {
    private final AesGcmAuthenticatedSessionProtector protector =
            new AesGcmAuthenticatedSessionProtector(
                    Base64.getEncoder().encodeToString(new byte[32]),
                    new ObjectMapper().findAndRegisterModules());

    @Test
    void roundTripPreservesSensitiveContextAndReadableLifecycleMetadata() {
        var session = session();
        var protectedValue = protector.protect(session);

        assertThat(protectedValue.keyVersion()).isEqualTo("v1");
        assertThat(protectedValue.status()).isEqualTo(session.status());
        assertThat(protectedValue.createdAt()).isEqualTo(session.createdAt());
        assertThat(protectedValue.updatedAt()).isEqualTo(session.lastAccessedAt());
        assertThat(protectedValue.idleExpiresAt()).isEqualTo(session.idleExpiresAt());
        assertThat(protectedValue.absoluteExpiresAt()).isEqualTo(session.absoluteExpiresAt());
        assertThat(protectedValue.toString())
                .doesNotContain("member01", "00000008802", "csrf_hash");
        assertThat(protector.unprotect(protectedValue)).isEqualTo(session);
    }

    @Test
    void sameSessionUsesFreshNonceAndCiphertext() {
        var first = protector.protect(session());
        var second = protector.protect(session());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void ciphertextTamperingFailsClosedWithoutSecretLeakage() {
        var value = protector.protect(session());
        var attributes = new LinkedHashMap<>(value.data().attributes());
        attributes.put(ProtectedAuthenticatedSession.CIPHERTEXT, value.ciphertext() + "A");
        var tampered = new ProtectedAuthenticatedSession(
                value.authenticatedSessionId(), value.status(),
                new ProtectedAuthenticatedSession.Data(attributes),
                value.createdAt(), value.updatedAt(), value.idleExpiresAt(), value.absoluteExpiresAt());

        assertThatThrownBy(() -> protector.unprotect(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to unprotect authenticated session")
                .hasMessageNotContaining("member01")
                .hasMessageNotContaining("00000008802")
                .hasMessageNotContaining("csrf_hash");
    }

    @Test
    void lifecycleMetadataTamperingFailsAadValidation() {
        var value = protector.protect(session());
        var tampered = new ProtectedAuthenticatedSession(
                value.authenticatedSessionId(), AuthenticatedSessionStatus.LOGGED_OUT,
                value.data(), value.createdAt(), value.updatedAt(),
                value.idleExpiresAt(), value.absoluteExpiresAt());

        assertThatThrownBy(() -> protector.unprotect(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to unprotect authenticated session");
    }

    @Test
    void invalidKeyLengthAndProtectionMetadataFailClosed() {
        assertThatThrownBy(() -> new AesGcmAuthenticatedSessionProtector(
                Base64.getEncoder().encodeToString(new byte[16]), new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class);

        var value = protector.protect(session());
        var attributes = new LinkedHashMap<>(value.data().attributes());
        attributes.put(ProtectedAuthenticatedSession.KEY_VERSION, "v2");
        var unsupported = new ProtectedAuthenticatedSession(
                value.authenticatedSessionId(), value.status(),
                new ProtectedAuthenticatedSession.Data(attributes),
                value.createdAt(), value.updatedAt(), value.idleExpiresAt(), value.absoluteExpiresAt());

        assertThatThrownBy(() -> protector.unprotect(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported authenticated session protection metadata");
    }

    private AuthenticatedSession session() {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        return new AuthenticatedSession(
                new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
                new ActorContext("member01", ActorUserRole.ROLE_USER),
                List.of(new AuthenticatedSessionAccount(
                        new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                        "DB", "00000008802", "0001", "DBA", "OE", null, null)),
                AuthenticatedSessionStatus.ACTIVE,
                "csrf_hash",
                now,
                now,
                now.plusSeconds(1800),
                now.plusSeconds(28800));
    }
}
