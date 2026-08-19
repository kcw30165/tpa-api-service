package com.bct.ngtpa.apiservice.infrastructure.security.session;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Protects only sensitive authenticated context; lifecycle metadata is AES-GCM AAD. */
public final class AesGcmAuthenticatedSessionProtector
    implements AuthenticatedSessionProtectorPort {
  private static final String KEY_VERSION = "v1";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int TAG_BYTES = 16;
  private final SecretKeySpec key;
  private final ObjectMapper mapper;
  private final SecureRandom random;

  public AesGcmAuthenticatedSessionProtector(String base64Key, ObjectMapper mapper) {
    this(base64Key, mapper, new SecureRandom());
  }

  AesGcmAuthenticatedSessionProtector(String base64Key, ObjectMapper mapper, SecureRandom random) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.random = Objects.requireNonNull(random, "random must not be null");
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(required(base64Key));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Authenticated session key must be valid Base64");
    }
    if (decoded.length != 32) {
      throw new IllegalArgumentException("Authenticated session key must be 256-bit");
    }
    key = new SecretKeySpec(decoded, "AES");
  }

  @Override
  public ProtectedAuthenticatedSession protect(AuthenticatedSession session) {
    Objects.requireNonNull(session, "session must not be null");
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(aad(session));
      byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(SensitiveContext.from(session)));
      int split = encrypted.length - TAG_BYTES;
      Map<String, String> attributes = new LinkedHashMap<>();
      attributes.put(
          ProtectedAuthenticatedSession.CIPHERTEXT, encode(Arrays.copyOf(encrypted, split)));
      attributes.put(ProtectedAuthenticatedSession.ALGORITHM, TRANSFORMATION);
      attributes.put(ProtectedAuthenticatedSession.NONCE, encode(nonce));
      attributes.put(
          ProtectedAuthenticatedSession.AUTHENTICATION_TAG,
          encode(Arrays.copyOfRange(encrypted, split, encrypted.length)));
      attributes.put(ProtectedAuthenticatedSession.KEY_VERSION, KEY_VERSION);
      return new ProtectedAuthenticatedSession(
          session.authenticatedSessionId().value(),
          session.status(),
          new ProtectedAuthenticatedSession.Data(attributes),
          session.createdAt(),
          session.lastAccessedAt(),
          session.idleExpiresAt(),
          session.absoluteExpiresAt());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to protect authenticated session");
    }
  }

  @Override
  public AuthenticatedSession unprotect(ProtectedAuthenticatedSession envelope) {
    Objects.requireNonNull(envelope, "protectedSession must not be null");
    if (!KEY_VERSION.equals(envelope.keyVersion())
        || !TRANSFORMATION.equals(envelope.algorithm())) {
      throw new IllegalArgumentException("Unsupported authenticated session protection metadata");
    }
    try {
      byte[] ciphertext = decode(envelope.ciphertext());
      byte[] tag = decode(envelope.authenticationTag());
      byte[] nonce = decode(envelope.nonce());
      if (nonce.length != NONCE_BYTES || tag.length != TAG_BYTES) {
        throw new IllegalArgumentException("Invalid authenticated session envelope");
      }
      byte[] combined = new byte[ciphertext.length + tag.length];
      System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
      System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(aad(envelope));
      SensitiveContext sensitive =
          mapper.readValue(cipher.doFinal(combined), SensitiveContext.class);
      return sensitive.toDomain(envelope);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to unprotect authenticated session");
    }
  }

  private static byte[] aad(AuthenticatedSession session) {
    return canonicalAad(
        session.authenticatedSessionId().value(),
        session.status().name(),
        session.createdAt().toString(),
        session.lastAccessedAt().toString(),
        session.idleExpiresAt().toString(),
        session.absoluteExpiresAt().toString());
  }

  private static byte[] aad(ProtectedAuthenticatedSession envelope) {
    return canonicalAad(
        envelope.authenticatedSessionId(),
        envelope.status().name(),
        envelope.createdAt().toString(),
        envelope.updatedAt().toString(),
        envelope.idleExpiresAt().toString(),
        envelope.absoluteExpiresAt().toString());
  }

  private static byte[] canonicalAad(
      String sessionId,
      String status,
      String createdAt,
      String updatedAt,
      String idleExpiresAt,
      String absoluteExpiresAt) {
    String value =
        "authenticated-session|"
            + KEY_VERSION
            + "\n"
            + "algorithm="
            + TRANSFORMATION
            + "\n"
            + "sessionId="
            + sessionId
            + "\n"
            + "status="
            + status
            + "\n"
            + "createdAt="
            + createdAt
            + "\n"
            + "updatedAt="
            + updatedAt
            + "\n"
            + "idleExpiresAt="
            + idleExpiresAt
            + "\n"
            + "absoluteExpiresAt="
            + absoluteExpiresAt;
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Authenticated session key is required");
    }
    return value.trim();
  }

  private static String encode(byte[] value) {
    return Base64.getEncoder().encodeToString(value);
  }

  private static byte[] decode(String value) {
    return Base64.getDecoder().decode(value);
  }

  /** Sensitive context only. Lifecycle metadata deliberately stays outside ciphertext. */
  private record SensitiveContext(
      String actorUserId,
      ActorUserRole actorUserRole,
      List<WireAccount> accounts,
      String csrfTokenHash) {
    static SensitiveContext from(AuthenticatedSession session) {
      return new SensitiveContext(
          session.actorContext().actorUserId(),
          session.actorContext().actorUserRole(),
          session.accounts().stream().map(WireAccount::from).toList(),
          session.csrfTokenHash());
    }

    AuthenticatedSession toDomain(ProtectedAuthenticatedSession envelope) {
      return new AuthenticatedSession(
          new AuthenticatedSessionId(envelope.authenticatedSessionId()),
          new ActorContext(actorUserId, actorUserRole),
          accounts.stream().map(WireAccount::toDomain).toList(),
          envelope.status(),
          csrfTokenHash,
          envelope.createdAt(),
          envelope.updatedAt(),
          envelope.idleExpiresAt(),
          envelope.absoluteExpiresAt());
    }
  }

  private record WireAccount(
      String accountRef,
      String accountEnv,
      String policyNo,
      String certNo,
      String trustCode,
      String schemeType,
      String termStatus,
      String termCompletionDate) {
    static WireAccount from(AuthenticatedSessionAccount account) {
      return new WireAccount(
          account.accountRef().value(),
          account.accountEnv(),
          account.policyNo(),
          account.certNo(),
          account.trustCode(),
          account.schemeType(),
          account.termStatus(),
          account.termCompletionDate() == null ? null : account.termCompletionDate().toString());
    }

    AuthenticatedSessionAccount toDomain() {
      return new AuthenticatedSessionAccount(
          new AuthenticatedAccountRef(accountRef),
          accountEnv,
          policyNo,
          certNo,
          trustCode,
          schemeType,
          termStatus,
          termCompletionDate == null ? null : LocalDate.parse(termCompletionDate));
    }
  }
}
