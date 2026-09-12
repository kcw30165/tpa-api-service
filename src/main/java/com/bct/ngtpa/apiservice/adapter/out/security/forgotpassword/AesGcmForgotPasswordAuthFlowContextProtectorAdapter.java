package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmForgotPasswordAuthFlowContextProtectorAdapter
    implements ForgotPasswordAuthFlowContextProtectorPort {
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String KEY_ALGORITHM = "AES";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int MIN_SECRET_LENGTH = 32;

  private final String keyVersion;
  private final byte[] key;
  private final ObjectMapper objectMapper;
  private final SecureRandom secureRandom;

  public AesGcmForgotPasswordAuthFlowContextProtectorAdapter(String keyVersion, String secret) {
    this(keyVersion, secret, new ObjectMapper(), new SecureRandom());
  }

  AesGcmForgotPasswordAuthFlowContextProtectorAdapter(
      String keyVersion, String secret, ObjectMapper objectMapper, SecureRandom secureRandom) {
    if (keyVersion == null || keyVersion.isBlank()) {
      throw new IllegalArgumentException(
          "forgot password context protection keyVersion must not be blank");
    }
    if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "forgot password context protection secret is missing or too short");
    }
    this.keyVersion = keyVersion.trim();
    this.key = secret.getBytes(StandardCharsets.UTF_8);
    this.objectMapper = objectMapper;
    this.secureRandom = secureRandom;
  }

  @Override
  public ForgotPasswordAuthFlowEncryptedContext protect(ForgotPasswordAuthFlowContext context) {
    try {
      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key, KEY_ALGORITHM),
          new GCMParameterSpec(TAG_BITS, iv));
      byte[] plaintext = objectMapper.writeValueAsBytes(Payload.from(context));
      byte[] encrypted = cipher.doFinal(plaintext);
      byte[] packed = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, packed, 0, iv.length);
      System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
      return new ForgotPasswordAuthFlowEncryptedContext(
          Base64.getUrlEncoder().withoutPadding().encodeToString(packed),
          keyVersion,
          ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
    } catch (Exception exception) {
      throw new ForgotPasswordAuthFlowContextProtectionException(
          "Unable to protect forgot password context", exception);
    }
  }

  @Override
  public ForgotPasswordAuthFlowContext unprotect(
      ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
    try {
      byte[] packed = Base64.getUrlDecoder().decode(encryptedContext.ciphertext());
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
      byte[] plaintext = cipher.doFinal(encrypted);
      Payload payload = objectMapper.readValue(plaintext, Payload.class);
      return payload.toDomain();
    } catch (Exception exception) {
      throw new ForgotPasswordAuthFlowContextProtectionException(
          "Unable to unprotect forgot password context", exception);
    }
  }

  private record AuthorizationPayload(
      String authorizationId,
      String authFlowSessionId,
      String resetSubjectId,
      String issuedAt,
      String expiresAt,
      String consumedAt) {
    static AuthorizationPayload from(ForgotPasswordResetAuthorization authorization) {
      if (authorization == null) {
        return null;
      }
      return new AuthorizationPayload(
          authorization.authorizationId(),
          authorization.authFlowSessionId(),
          authorization.resetSubjectId(),
          authorization.issuedAt().toString(),
          authorization.expiresAt().toString(),
          authorization.consumedAt() == null ? null : authorization.consumedAt().toString());
    }

    ForgotPasswordResetAuthorization toDomain() {
      return new ForgotPasswordResetAuthorization(
          authorizationId,
          authFlowSessionId,
          resetSubjectId,
          java.time.Instant.parse(issuedAt),
          java.time.Instant.parse(expiresAt),
          consumedAt == null ? null : java.time.Instant.parse(consumedAt));
    }
  }

  private record Payload(
      String normalizedUsername,
      String customerId,
      String identityValue,
      String identityType,
      boolean identityVerified,
      String resetSubjectId,
      String contactChannel,
      String protectedDestination,
      String maskedDestination,
      AuthorizationPayload resetAuthorization,
      String workflowVersion,
      com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context
              .ForgotPasswordProtectedPhase
          phase) {
    static Payload from(ForgotPasswordAuthFlowContext context) {
      return new Payload(
          context.normalizedUsername(),
          context.customerId(),
          context.identityValue(),
          context.identityType(),
          context.identityVerified(),
          context.resetSubjectId(),
          context.contactChannel(),
          context.protectedDestination(),
          context.maskedDestination(),
          AuthorizationPayload.from(context.resetAuthorization()),
          context.workflowVersion(),
          context.phase());
    }

    ForgotPasswordAuthFlowContext toDomain() {
      return new ForgotPasswordAuthFlowContext(
          normalizedUsername,
          customerId,
          identityValue,
          identityType,
          identityVerified,
          resetSubjectId,
          contactChannel,
          protectedDestination,
          maskedDestination,
          resetAuthorization == null ? null : resetAuthorization.toDomain(),
          workflowVersion,
          phase);
    }
  }
}
