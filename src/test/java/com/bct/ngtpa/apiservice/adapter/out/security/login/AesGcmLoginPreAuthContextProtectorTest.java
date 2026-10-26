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
    var protector =
        new AesGcmLoginPreAuthContextProtector(
            new ObjectMapper().findAndRegisterModules(), "v1", secret());
    var destinations = new LinkedHashMap<String, LoginPreAuthDestination>();
    destinations.put(
        "dst_opaque_value",
        new LoginPreAuthDestination(
            "dst_opaque_value", "email", "member@example.com", "m*****@example.com"));
    var context =
        new LoginPreAuthContext(
            "member01",
            "role_user",
            destinations,
            Instant.parse("2026-07-20T00:00:00Z"),
            "v1",
            null,
            null,
            null);

    var encrypted = protector.protect(context);

    assertThat(encrypted.keyVersion()).isEqualTo("v1");
    assertThat(encrypted.algorithm()).isEqualTo("AES/GCM/NoPadding");
    assertThat(encrypted.ciphertext())
        .doesNotContain("member01", "member@example.com", "dst_opaque_value");
    assertThat(protector.unprotect(encrypted)).isEqualTo(context);
    assertThat(encrypted.toString()).doesNotContain(encrypted.ciphertext());
  }

  @Test
  void rejectsWrongKeyVersionAndTamperedCiphertext() {
    var protector =
        new AesGcmLoginPreAuthContextProtector(
            new ObjectMapper().findAndRegisterModules(), "v1", secret());
    var context =
        new LoginPreAuthContext(
            "member",
            null,
            java.util.Map.of(
                "dst_opaque_value",
                new LoginPreAuthDestination("dst_opaque_value", "sms", "91234567", "****4567")),
            Instant.now(),
            "v1",
            null,
            null,
            null);
    var encrypted = protector.protect(context);

    assertThatThrownBy(
            () ->
                protector.unprotect(
                    new com.bct.ngtpa.apiservice.application.dto.auth.login.preauth
                        .LoginPreAuthEncryptedContext(
                        "v2", encrypted.algorithm(), encrypted.iv(), encrypted.ciphertext())))
        .isInstanceOf(LoginPreAuthContextProtectionException.class);

    String tampered =
        encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 2) + "AA";
    assertThatThrownBy(
            () ->
                protector.unprotect(
                    new com.bct.ngtpa.apiservice.application.dto.auth.login.preauth
                        .LoginPreAuthEncryptedContext(
                        encrypted.keyVersion(), encrypted.algorithm(), encrypted.iv(), tampered)))
        .isInstanceOf(LoginPreAuthContextProtectionException.class);
  }

  @Test
  void roundTripsTrustCodeAndSchemeNamesWithoutPlaintext() {
    var protector =
        new AesGcmLoginPreAuthContextProtector(
            new ObjectMapper().findAndRegisterModules(), "v1", secret());
    var destinations = new LinkedHashMap<String, LoginPreAuthDestination>();
    destinations.put(
        "dst_opaque_value",
        new LoginPreAuthDestination(
            "dst_opaque_value", "email", "member@example.com", "m****@example.com"));
    var context =
        new LoginPreAuthContext(
            "member01",
            "role_user",
            destinations,
            Instant.parse("2026-07-20T00:00:00Z"),
            "v1",
            "DBA",
            "Example Scheme",
            "示例計劃");

    // Each protect() call uses a fresh, independently-random IV, so the
    // resulting Base64 ciphertext is effectively random-looking. A short
    // plaintext substring (e.g. the 3-character trust code "DBA") has a
    // real, nonzero probability of appearing by pure coincidence in any
    // single encryption, independent of whether plaintext actually leaks.
    // Retrying with a fresh, independent encryption on a coincidental
    // match distinguishes that benign coincidence from a genuine,
    // deterministic leak: a real leak would reproduce on every attempt,
    // while a coincidence is overwhelmingly unlikely to repeat across
    // several independent, freshly-randomized encryptions.
    var encrypted = protector.protect(context);
    int attempts = 1;
    while (containsAnyPlaintextTarget(encrypted.ciphertext()) && attempts < 25) {
      encrypted = protector.protect(context);
      attempts++;
    }

    assertThat(encrypted.ciphertext()).doesNotContain("DBA", "Example Scheme", "示例計劃", "member01");
    assertThat(protector.unprotect(encrypted)).isEqualTo(context);
  }

  private static boolean containsAnyPlaintextTarget(String ciphertext) {
    return ciphertext.contains("DBA")
        || ciphertext.contains("Example Scheme")
        || ciphertext.contains("示例計劃")
        || ciphertext.contains("member01");
  }

  private String secret() {
    return Base64.getEncoder().encodeToString(new byte[32]);
  }
}
