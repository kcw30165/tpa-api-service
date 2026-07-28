package com.bct.ngtpa.apiservice.adapter.out.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthDestination;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class AesGcmLoginPreAuthContextProtectorTest {

    @Test
    void protectsCompleteContextAndRoundTripsWithoutPlaintext() {
        var protector = new AesGcmLoginPreAuthContextProtector(
                new ObjectMapper().findAndRegisterModules(),
                "v1",
                secret());
        var destinations = new LinkedHashMap<String, LoginPreAuthDestination>();
        destinations.put("dst_opaque_value", new LoginPreAuthDestination(
                "dst_opaque_value", "email", "member@example.com", "m*****@example.com"));
        var context = new LoginPreAuthContext(
                "member01", "MBR", "role_user", destinations,
                Instant.parse("2026-07-20T00:00:00Z"), "v1");

        var encrypted = protector.protect(context);

        assertThat(encrypted.keyVersion()).isEqualTo("v1");
        assertThat(encrypted.algorithm()).isEqualTo("AES/GCM/NoPadding");
        assertThat(encrypted.ciphertext()).doesNotContain(
                "member01", "member@example.com", "dst_opaque_value");
        assertThat(protector.unprotect(encrypted)).isEqualTo(context);
        assertThat(encrypted.toString()).doesNotContain(encrypted.ciphertext());
    }

    @Test
    void rejectsWrongKeyVersionAndTamperedCiphertext() {
        var protector = new AesGcmLoginPreAuthContextProtector(
                new ObjectMapper().findAndRegisterModules(), "v1", secret());
        var context = new LoginPreAuthContext(
                "member", "MBR", null, java.util.Map.of(
                        "dst_opaque_value", new LoginPreAuthDestination(
                                "dst_opaque_value", "sms", "91234567", "****4567")),
                Instant.now(), "v1");
        var encrypted = protector.protect(context);

        assertThatThrownBy(() -> protector.unprotect(
                new com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext(
                        "v2", encrypted.algorithm(), encrypted.iv(), encrypted.ciphertext())))
                .isInstanceOf(LoginPreAuthContextProtectionException.class);

        String tampered = encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 2) + "AA";
        assertThatThrownBy(() -> protector.unprotect(
                new com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext(
                        encrypted.keyVersion(), encrypted.algorithm(), encrypted.iv(), tampered)))
                .isInstanceOf(LoginPreAuthContextProtectionException.class);
    }

    private String secret() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
