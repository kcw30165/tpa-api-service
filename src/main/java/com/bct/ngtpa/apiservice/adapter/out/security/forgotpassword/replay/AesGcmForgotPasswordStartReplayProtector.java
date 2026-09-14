package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword.replay;

import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ForgotPasswordStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.idempotency.start.ProtectedForgotPasswordStartReplay;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmForgotPasswordStartReplayProtector
    implements ForgotPasswordStartProtectedReplayProtectorPort {
  private static final int SCHEMA = 1;
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private final String keyVersion;
  private final SecretKeySpec key;
  private final ObjectMapper mapper;
  private final SecureRandom random;

  public AesGcmForgotPasswordStartReplayProtector(String keyVersion, String base64Secret) {
    this(keyVersion, base64Secret, new ObjectMapper(), new SecureRandom());
  }

  AesGcmForgotPasswordStartReplayProtector(
      String keyVersion, String base64Secret, ObjectMapper mapper, SecureRandom random) {
    this.keyVersion = require(keyVersion, "key version");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(require(base64Secret, "secret"));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Forgot Password Start replay secret is invalid");
    }
    if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
      throw new IllegalArgumentException(
          "Forgot Password Start replay secret has invalid key length");
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
  public ProtectedForgotPasswordStartReplay protect(
      ProtectedReplayReference reference, ForgotPasswordStartProtectedReplayState state) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      cipher.updateAAD(aad(reference));
      byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(state));
      return new ProtectedForgotPasswordStartReplay(
          SCHEMA,
          keyVersion,
          ALGORITHM,
          Base64.getUrlEncoder().withoutPadding().encodeToString(iv),
          Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted));
    } catch (Exception exception) {
      throw new IllegalStateException("Forgot Password Start replay protection failed");
    }
  }

  @Override
  public ForgotPasswordStartProtectedReplayState unprotect(
      ProtectedReplayReference reference, ProtectedForgotPasswordStartReplay value) {
    if (!keyVersion.equals(value.keyVersion()) || !ALGORITHM.equals(value.algorithm())) {
      throw new IllegalArgumentException(
          "Forgot Password Start protected replay metadata is invalid");
    }
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(128, Base64.getUrlDecoder().decode(value.iv())));
      cipher.updateAAD(aad(reference));
      byte[] clear = cipher.doFinal(Base64.getUrlDecoder().decode(value.ciphertext()));
      return mapper.readValue(clear, ForgotPasswordStartProtectedReplayState.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Forgot Password Start protected replay is invalid");
    }
  }

  private byte[] aad(ProtectedReplayReference reference) {
    return ("forgot-password|start|" + SCHEMA + "|" + keyVersion + "|" + reference.value())
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String require(String value, String label) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(label + " must not be blank");
    return value.trim();
  }
}
