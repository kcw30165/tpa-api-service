package com.bct.ngtpa.apiservice.adapter.out.security.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckEncryptedState;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link AesGcmRegistrationUsernameCheckProtectorAdapter}. Performs REAL
 * AES-GCM protect/unprotect round-trips (no mocking) since this is a self-contained crypto
 * adapter; all fixture types (UsernameCheckState/SelectedTrust) verified verbatim (round 80).
 */
class AesGcmRegistrationUsernameCheckProtectorAdapterTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";

  private AesGcmRegistrationUsernameCheckProtectorAdapter newAdapter() {
    return new AesGcmRegistrationUsernameCheckProtectorAdapter("kv1", SECRET);
  }

  private UsernameCheckState state() {
    return new UsernameCheckState(
        new SelectedTrust("trust1", "env1"), "myusername", Instant.parse("2026-09-14T00:00:00Z"),
        Instant.parse("2026-09-14T01:00:00Z"));
  }

  // ----- constructor guards ----------------------------------------------------

  @Test
  void blankKeyVersionThrows() {
    assertThatThrownBy(() -> new AesGcmRegistrationUsernameCheckProtectorAdapter("", SECRET))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tooShortSecretThrows() {
    assertThatThrownBy(() -> new AesGcmRegistrationUsernameCheckProtectorAdapter("kv1", "short"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- round-trip --------------------------------------------------------------

  @Test
  void protectThenUnprotectRoundTripsSuccessfully() {
    AesGcmRegistrationUsernameCheckProtectorAdapter adapter = newAdapter();
    UsernameCheckState original = state();
    RegistrationUsernameCheckEncryptedState encrypted = adapter.protect(original);
    assertThat(encrypted.keyVersion()).isEqualTo("kv1");
    assertThat(encrypted.ciphertext()).isNotBlank();
    UsernameCheckState roundTripped = adapter.unprotect(encrypted);
    assertThat(roundTripped.username()).isEqualTo(original.username());
    assertThat(roundTripped.selectedTrust().trustCode()).isEqualTo(original.selectedTrust().trustCode());
    assertThat(roundTripped.selectedTrust().accountEnv()).isEqualTo(original.selectedTrust().accountEnv());
    assertThat(roundTripped.checkedAt()).isEqualTo(original.checkedAt());
    assertThat(roundTripped.expiresAt()).isEqualTo(original.expiresAt());
  }

  @Test
  void protectThenUnprotectWithNullExpiresAtRoundTrips() {
    AesGcmRegistrationUsernameCheckProtectorAdapter adapter = newAdapter();
    UsernameCheckState original =
        new UsernameCheckState(
            new SelectedTrust("trust1", "env1"), "myusername", Instant.parse("2026-09-14T00:00:00Z"),
            null);
    RegistrationUsernameCheckEncryptedState encrypted = adapter.protect(original);
    UsernameCheckState roundTripped = adapter.unprotect(encrypted);
    assertThat(roundTripped.expiresAt()).isNull();
  }

  @Test
  void twoEncryptionsOfSameStateProduceDifferentCiphertext() {
    AesGcmRegistrationUsernameCheckProtectorAdapter adapter = newAdapter();
    UsernameCheckState original = state();
    RegistrationUsernameCheckEncryptedState first = adapter.protect(original);
    RegistrationUsernameCheckEncryptedState second = adapter.protect(original);
    assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
  }

  // ----- failure paths --------------------------------------------------------------

  @Test
  void unprotectWithCorruptedCiphertextThrowsProtectionException() {
    AesGcmRegistrationUsernameCheckProtectorAdapter adapter = newAdapter();
    RegistrationUsernameCheckEncryptedState corrupted =
        new RegistrationUsernameCheckEncryptedState("not-valid-base64-!!!", "kv1", "AES_GCM_V1");
    assertThatThrownBy(() -> adapter.unprotect(corrupted))
        .isInstanceOf(RegistrationAuthFlowContextProtectionException.class);
  }

  @Test
  void unprotectWithTooShortPayloadThrowsProtectionException() {
    AesGcmRegistrationUsernameCheckProtectorAdapter adapter = newAdapter();
    String tooShort = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[5]);
    RegistrationUsernameCheckEncryptedState corrupted =
        new RegistrationUsernameCheckEncryptedState(tooShort, "kv1", "AES_GCM_V1");
    assertThatThrownBy(() -> adapter.unprotect(corrupted))
        .isInstanceOf(RegistrationAuthFlowContextProtectionException.class);
  }

  @Test
  void unprotectWithWrongKeyThrowsProtectionException() {
    AesGcmRegistrationUsernameCheckProtectorAdapter adapter = newAdapter();
    RegistrationUsernameCheckEncryptedState encrypted = adapter.protect(state());
    AesGcmRegistrationUsernameCheckProtectorAdapter differentKeyAdapter =
        new AesGcmRegistrationUsernameCheckProtectorAdapter("kv2", "fedcba9876543210fedcba9876543210");
    assertThatThrownBy(() -> differentKeyAdapter.unprotect(encrypted))
        .isInstanceOf(RegistrationAuthFlowContextProtectionException.class);
  }
}
