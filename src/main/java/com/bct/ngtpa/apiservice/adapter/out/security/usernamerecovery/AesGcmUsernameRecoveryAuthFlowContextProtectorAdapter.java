package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter
        implements UsernameRecoveryAuthFlowContextProtectorPort {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String CAPABILITY = "username-recovery-auth-flow-context";
    private static final int IV_BYTES = 12;
    private final String keyVersion;
    private final byte[] key;
    private final ObjectMapper mapper;
    private final SecureRandom random;

    public AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(String keyVersion, String secret) {
        this(keyVersion, secret, new ObjectMapper(), new SecureRandom());
    }
    AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(
            String keyVersion, String secret, ObjectMapper mapper, SecureRandom random) {
        if (keyVersion == null || keyVersion.isBlank()) throw new IllegalArgumentException("keyVersion must not be blank");
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("secret must contain at least 32 characters");
        this.keyVersion = keyVersion.trim();
        this.key = sha256(secret);
        this.mapper = mapper;
        this.random = random;
    }
    @Override public UsernameRecoveryAuthFlowEncryptedContext protect(
            AuthFlowSessionId id, UsernameRecoveryAuthFlowContext context) {
        try {
            byte[] iv = new byte[IV_BYTES]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(id));
            byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(Payload.from(context)));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return new UsernameRecoveryAuthFlowEncryptedContext(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(packed), keyVersion, TRANSFORMATION);
        } catch (Exception e) { throw new UsernameRecoveryAuthFlowContextProtectionException("Unable to protect Username Recovery context", e); }
    }
    @Override public UsernameRecoveryAuthFlowContext unprotect(
            AuthFlowSessionId id, UsernameRecoveryAuthFlowEncryptedContext value) {
        try {
            if (!keyVersion.equals(value.keyVersion()) || !TRANSFORMATION.equals(value.algorithm()))
                throw new IllegalArgumentException("unsupported protected context metadata");
            byte[] packed = Base64.getUrlDecoder().decode(value.ciphertext());
            if (packed.length <= IV_BYTES) throw new IllegalArgumentException("invalid protected context payload");
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, IV_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(id));
            return mapper.readValue(cipher.doFinal(encrypted), Payload.class).toContext();
        } catch (Exception e) { throw new UsernameRecoveryAuthFlowContextProtectionException("Unable to unprotect Username Recovery context", e); }
    }
    private byte[] aad(AuthFlowSessionId id) {
        return (CAPABILITY + "|" + keyVersion + "|" + id.value()).getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] sha256(String secret) {
        try { return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private record Candidate(String schemeRef, String accountEnv, String trustCode, boolean registered,
            String userName, String deliveryState, String requestedAt, String senderReference) {
        static Candidate from(UsernameRecoverySessionCandidate c) {
            return new Candidate(c.schemeRef().value(), c.accountEnv(), c.trustCode(), c.registered(), c.userName(),
                    c.deliveryState().name(), c.requestedAt() == null ? null : c.requestedAt().toString(), c.senderReference());
        }
        UsernameRecoverySessionCandidate toDomain() {
            return new UsernameRecoverySessionCandidate(new UsernameRecoveryCandidate(
                    new UsernameRecoverySchemeRef(schemeRef), accountEnv, trustCode, registered, userName,
                    UsernameRecoveryDeliveryState.valueOf(deliveryState)),
                    requestedAt == null ? null : Instant.parse(requestedAt), senderReference);
        }
    }
    private record Payload(String workflowStatus, String destination, String channel, String language,
            List<Candidate> candidates) {
        static Payload from(UsernameRecoveryAuthFlowContext c) {
            return new Payload(c.workflowStatus().name(), c.verifiedDestination().value(),
                    c.verifiedDestination().channel().name(), c.language().toLanguageTag(),
                    c.candidates().stream().map(Candidate::from).toList());
        }
        UsernameRecoveryAuthFlowContext toContext() {
            return new UsernameRecoveryAuthFlowContext(UsernameRecoveryWorkflowStatus.valueOf(workflowStatus),
                    new UsernameRecoveryVerifiedDestination(destination,
                            UsernameRecoveryVerifiedDestination.Channel.valueOf(channel)),
                    Locale.forLanguageTag(language), candidates.stream().map(Candidate::toDomain).toList());
        }
    }
}
