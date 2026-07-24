package com.bct.ngtpa.apiservice.infrastructure.security.session;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmAuthenticatedSessionProtector implements AuthenticatedSessionProtectorPort {
    private static final String VERSION = "v1";
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
        this.key = new SecretKeySpec(decoded, "AES");
    }

    @Override
    public ProtectedAuthenticatedSession protect(AuthenticatedSession session) {
        Objects.requireNonNull(session, "session must not be null");
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(WireSession.from(session)));
            int split = encrypted.length - TAG_BYTES;
            return new ProtectedAuthenticatedSession(
                    VERSION,
                    encode(Arrays.copyOf(encrypted, split)),
                    encode(nonce),
                    encode(Arrays.copyOfRange(encrypted, split, encrypted.length)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect authenticated session");
        }
    }

    @Override
    public AuthenticatedSession unprotect(ProtectedAuthenticatedSession protectedSession) {
        Objects.requireNonNull(protectedSession, "protectedSession must not be null");
        if (!VERSION.equals(protectedSession.version())) {
            throw new IllegalArgumentException("Unsupported authenticated session version");
        }
        try {
            byte[] ciphertext = decode(protectedSession.ciphertext());
            byte[] tag = decode(protectedSession.authenticationTag());
            byte[] nonce = decode(protectedSession.nonce());
            if (nonce.length != NONCE_BYTES || tag.length != TAG_BYTES) {
                throw new IllegalArgumentException("Invalid authenticated session envelope");
            }
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));
            return mapper.readValue(cipher.doFinal(combined), WireSession.class).toDomain();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to unprotect authenticated session");
        }
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

    /** Adapter-private wire model with explicit ISO-8601 temporal values. */
    private record WireSession(
            String sessionId,
            String actorUserId,
            String actorUserRole,
            List<WireAccount> accounts,
            String status,
            String csrfTokenHash,
            String createdAt,
            String lastAccessedAt,
            String idleExpiresAt,
            String absoluteExpiresAt) {
        static WireSession from(AuthenticatedSession session) {
            return new WireSession(
                    session.authenticatedSessionId().value(),
                    session.actorContext().actorUserId(),
                    session.actorContext().actorUserRole(),
                    session.accounts().stream().map(WireAccount::from).toList(),
                    session.status().name(),
                    session.csrfTokenHash(),
                    session.createdAt().toString(),
                    session.lastAccessedAt().toString(),
                    session.idleExpiresAt().toString(),
                    session.absoluteExpiresAt().toString());
        }

        AuthenticatedSession toDomain() {
            return new AuthenticatedSession(
                    new AuthenticatedSessionId(sessionId),
                    new ActorContext(actorUserId, actorUserRole),
                    accounts.stream().map(WireAccount::toDomain).toList(),
                    AuthenticatedSessionStatus.valueOf(status),
                    csrfTokenHash,
                    Instant.parse(createdAt),
                    Instant.parse(lastAccessedAt),
                    Instant.parse(idleExpiresAt),
                    Instant.parse(absoluteExpiresAt));
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
