package com.bct.ngtpa.apiservice.adapter.out.security.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckEncryptedState;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmRegistrationUsernameCheckProtectorAdapter
    implements RegistrationUsernameCheckProtectorPort {
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String KEY_ALGORITHM = "AES";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int MIN_SECRET_LENGTH = 32;

  private final String keyVersion;
  private final byte[] key;
  private final ObjectMapper objectMapper;
  private final SecureRandom secureRandom;

  public AesGcmRegistrationUsernameCheckProtectorAdapter(String keyVersion, String secret) {
    this(keyVersion, secret, new ObjectMapper(), new SecureRandom());
  }

  AesGcmRegistrationUsernameCheckProtectorAdapter(
      String keyVersion, String secret, ObjectMapper objectMapper, SecureRandom secureRandom) {
    if (keyVersion == null || keyVersion.isBlank()) {
      throw new IllegalArgumentException(
          "registration username-check protection keyVersion must not be blank");
    }
    if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "registration username-check protection secret is missing or too short");
    }
    this.keyVersion = keyVersion.trim();
    this.key = secret.getBytes(StandardCharsets.UTF_8);
    this.objectMapper = objectMapper;
    this.secureRandom = secureRandom;
  }

  @Override
  public RegistrationUsernameCheckEncryptedState protect(UsernameCheckState usernameCheckState) {
    try {
      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key, KEY_ALGORITHM),
          new GCMParameterSpec(TAG_BITS, iv));
      byte[] plaintext = objectMapper.writeValueAsBytes(Payload.from(usernameCheckState));
      byte[] encrypted = cipher.doFinal(plaintext);
      byte[] packed = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, packed, 0, iv.length);
      System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
      return new RegistrationUsernameCheckEncryptedState(
          Base64.getUrlEncoder().withoutPadding().encodeToString(packed),
          keyVersion,
          RegistrationAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
    } catch (Exception exception) {
      throw new RegistrationAuthFlowContextProtectionException(
          "Unable to protect registration username check", exception);
    }
  }

  @Override
  public UsernameCheckState unprotect(RegistrationUsernameCheckEncryptedState encryptedState) {
    try {
      byte[] packed = Base64.getUrlDecoder().decode(encryptedState.ciphertext());
      if (packed.length <= IV_BYTES) {
        throw new GeneralSecurityException("invalid encrypted payload");
      }
      byte[] iv = new byte[IV_BYTES];
      byte[] encrypted = new byte[packed.length - IV_BYTES];
      System.arraycopy(packed, 0, iv, 0, IV_BYTES);
      System.arraycopy(packed, IV_BYTES, encrypted, 0, encrypted.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, KEY_ALGORITHM),
          new GCMParameterSpec(TAG_BITS, iv));
      Payload payload = objectMapper.readValue(cipher.doFinal(encrypted), Payload.class);
      return payload.toUsernameCheckState();
    } catch (Exception exception) {
      throw new RegistrationAuthFlowContextProtectionException(
          "Unable to unprotect registration username check", exception);
    }
  }

  private record Payload(
      String trustCode, String accountEnv, String username, String checkedAt, String expiresAt) {
    static Payload from(UsernameCheckState state) {
      return new Payload(
          state.selectedTrust().trustCode(),
          state.selectedTrust().accountEnv(),
          state.username(),
          state.checkedAt().toString(),
          state.expiresAt() == null ? null : state.expiresAt().toString());
    }

    UsernameCheckState toUsernameCheckState() {
      return new UsernameCheckState(
          new SelectedTrust(trustCode, accountEnv),
          username,
          Instant.parse(checkedAt),
          expiresAt == null || expiresAt.isBlank() ? null : Instant.parse(expiresAt));
    }
  }
}
