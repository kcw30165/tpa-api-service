package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.replay;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.ProtectedUsernameRecoveryStartReplay;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayState;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmUsernameRecoveryStartReplayProtector
    implements UsernameRecoveryStartProtectedReplayProtectorPort {
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private final String keyVersion;
  private final SecretKeySpec key;
  private final ObjectMapper mapper;
  private final SecureRandom random;

  public AesGcmUsernameRecoveryStartReplayProtector(String keyVersion, String base64Secret) {
    this(keyVersion, base64Secret, new ObjectMapper(), new SecureRandom());
  }

  AesGcmUsernameRecoveryStartReplayProtector(
      String keyVersion, String base64Secret, ObjectMapper mapper, SecureRandom random) {
    this.keyVersion = require(keyVersion, "key version");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(require(base64Secret, "secret"));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Username Recovery START replay secret is invalid");
    }
    if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
      throw new IllegalArgumentException(
          "Username Recovery START replay secret has invalid key length");
    }
    this.key = new SecretKeySpec(decoded, "AES");
    this.mapper =
        Objects.requireNonNull(mapper)
            .copy()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.random = Objects.requireNonNull(random);
  }

  @Override
  public ProtectedUsernameRecoveryStartReplay protect(
      ProtectedReplayReference reference, UsernameRecoveryStartProtectedReplayState state) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      cipher.updateAAD(aad(reference));
      byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(state));
      return new ProtectedUsernameRecoveryStartReplay(
          ProtectedUsernameRecoveryStartReplay.CURRENT_SCHEMA_VERSION,
          keyVersion,
          ALGORITHM,
          Base64.getUrlEncoder().withoutPadding().encodeToString(iv),
          Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted));
    } catch (Exception exception) {
      throw new IllegalStateException("Username Recovery START replay protection failed");
    }
  }

  @Override
  public UsernameRecoveryStartProtectedReplayState unprotect(
      ProtectedReplayReference reference, ProtectedUsernameRecoveryStartReplay value) {
    if (value.schemaVersion() != ProtectedUsernameRecoveryStartReplay.CURRENT_SCHEMA_VERSION
        || !keyVersion.equals(value.keyVersion())
        || !ALGORITHM.equals(value.algorithm())) {
      throw new IllegalArgumentException("Username Recovery START replay metadata is invalid");
    }
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(128, Base64.getUrlDecoder().decode(value.iv())));
      cipher.updateAAD(aad(reference));
      byte[] clear = cipher.doFinal(Base64.getUrlDecoder().decode(value.ciphertext()));
      return mapper.readValue(clear, UsernameRecoveryStartProtectedReplayState.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Username Recovery START protected replay is invalid");
    }
  }

  private byte[] aad(ProtectedReplayReference reference) {
    Objects.requireNonNull(reference, "reference must not be null");
    return ("username-recovery|start|1|" + keyVersion + "|" + reference.value())
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return value.trim();
  }
}
