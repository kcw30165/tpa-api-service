package com.bct.ngtpa.apiservice.adapter.out.security.login.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayPayload;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginDestinationChoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class AesGcmLoginStartReplayProtectorTest {
        private final AesGcmLoginStartReplayProtector protector = new AesGcmLoginStartReplayProtector(
                        new ObjectMapper(), "v1", Base64.getEncoder().encodeToString(new byte[32]));

        @Test
        void roundTripsSensitiveReplayPayloadWithoutPlaintextCiphertext() {
                var payload = new LoginStartReplayPayload(
                                "afs_secret_session",
                                "csrf_secret_token",
                                900,
                                List.of(new LoginDestinationChoice("dst_opaque", "email", "m*****@example.com")),
                                Instant.parse("2026-07-20T00:15:00Z"));

                var encrypted = protector.protect(payload);

                assertThat(encrypted.ciphertext()).doesNotContain(
                                "afs_secret_session", "csrf_secret_token", "m*****@example.com");
                assertThat(protector.unprotect(encrypted)).isEqualTo(payload);
                assertThat(encrypted.toString()).doesNotContain(encrypted.ciphertext());
        }

        @Test
        void rejectsTamperingAndKeyVersionMismatch() {
                var payload = new LoginStartReplayPayload(
                                "afs_secret_session", "csrf_secret_token", 900, List.of(), Instant.now());
                var encrypted = protector.protect(payload);

                assertThatThrownBy(() -> protector.unprotect(encrypted.withKeyVersion("v2")))
                                .isInstanceOf(LoginStartReplayProtectionException.class);
                assertThatThrownBy(() -> protector.unprotect(encrypted.withCiphertext(
                                encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 2) + "AA")))
                                .isInstanceOf(LoginStartReplayProtectionException.class);
        }
}
