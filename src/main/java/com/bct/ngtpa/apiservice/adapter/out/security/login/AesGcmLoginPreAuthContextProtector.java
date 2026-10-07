package com.bct.ngtpa.apiservice.adapter.out.security.login;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthDestination;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext;
import com.bct.ngtpa.apiservice.application.port.out.LoginPreAuthContextProtectorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmLoginPreAuthContextProtector implements LoginPreAuthContextProtectorPort {
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;

  private final ObjectMapper mapper;
  private final String keyVersion;
  private final SecretKeySpec key;
  private final SecureRandom secureRandom;

  public AesGcmLoginPreAuthContextProtector(
      ObjectMapper mapper, String keyVersion, String base64Secret) {
    this(mapper, keyVersion, base64Secret, new SecureRandom());
  }

  AesGcmLoginPreAuthContextProtector(
      ObjectMapper mapper, String keyVersion, String base64Secret, SecureRandom secureRandom) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.keyVersion = requireText(keyVersion, "keyVersion");
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(requireText(base64Secret, "secret"));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Login pre-auth secret must be valid Base64", exception);
    }
    if (decoded.length != 32) {
      throw new IllegalArgumentException("Login pre-auth secret must decode to 32 bytes");
    }
    this.key = new SecretKeySpec(decoded, "AES");
  }

  @Override
  public LoginPreAuthEncryptedContext protect(LoginPreAuthContext context) {
    Objects.requireNonNull(context, "context must not be null");
    try {
      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      cipher.updateAAD(keyVersion.getBytes(StandardCharsets.UTF_8));
      byte[] plaintext = mapper.writeValueAsBytes(ProtectedPayload.from(context));
      byte[] encrypted = cipher.doFinal(plaintext);
      return new LoginPreAuthEncryptedContext(
          keyVersion,
          ALGORITHM,
          Base64.getEncoder().encodeToString(iv),
          Base64.getEncoder().encodeToString(encrypted));
    } catch (Exception exception) {
      throw new LoginPreAuthContextProtectionException(
          "Failed to protect login pre-auth context", exception);
    }
  }

  @Override
  public LoginPreAuthContext unprotect(LoginPreAuthEncryptedContext value) {
    Objects.requireNonNull(value, "value must not be null");
    if (!keyVersion.equals(value.keyVersion()) || !ALGORITHM.equals(value.algorithm())) {
      throw new LoginPreAuthContextProtectionException(
          "Unsupported login pre-auth context version or algorithm");
    }
    try {
      byte[] iv = Base64.getDecoder().decode(value.iv());
      if (iv.length != IV_BYTES) {
        throw new IllegalArgumentException("Invalid login pre-auth context IV length");
      }
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      cipher.updateAAD(keyVersion.getBytes(StandardCharsets.UTF_8));
      byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(value.ciphertext()));
      ProtectedPayload payload = mapper.readValue(plaintext, ProtectedPayload.class);
      return payload.toDomain();
    } catch (Exception exception) {
      throw new LoginPreAuthContextProtectionException(
          "Failed to unprotect login pre-auth context", exception);
    }
  }

  private String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  /** Adapter-private wire payload with an explicit ISO-8601 Instant representation. */
  private record ProtectedPayload(
      String normalizedUsername,
      String userRole,
      Map<String, LoginPreAuthDestination> destinationsById,
      String credentialVerifiedAt,
      String workflowVersion,
      String trustCode,
      String schemeName,
      String schemeNameChi) {

    private static ProtectedPayload from(LoginPreAuthContext context) {
      return new ProtectedPayload(
          context.normalizedUsername(),
          context.userRole(),
          context.destinationsById(),
          context.credentialVerifiedAt().toString(),
          context.workflowVersion(),
          context.trustCode(),
          context.schemeName(),
          context.schemeNameChi());
    }

    private LoginPreAuthContext toDomain() {
      return new LoginPreAuthContext(
          normalizedUsername,
          userRole,
          destinationsById,
          Instant.parse(credentialVerifiedAt),
          workflowVersion,
          trustCode,
          schemeName,
          schemeNameChi);
    }
  }
}
