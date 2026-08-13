package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryEncryptedWorkflowState;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateProtectorPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM protector with capability/session/revision authenticated binding. */
public final class AesGcmUsernameRecoveryWorkflowStateProtectorAdapter
        implements UsernameRecoveryWorkflowStateProtectorPort {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String CAPABILITY = "username-recovery-workflow-state";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String keyVersion;
    private final byte[] key;
    private final SecureRandom random;

    public AesGcmUsernameRecoveryWorkflowStateProtectorAdapter(String keyVersion, String secret) {
        this(keyVersion, secret, new SecureRandom());
    }

    AesGcmUsernameRecoveryWorkflowStateProtectorAdapter(
            String keyVersion, String secret, SecureRandom random) {
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalArgumentException("keyVersion must not be blank");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("secret must contain at least 32 characters");
        }
        this.keyVersion = keyVersion.trim();
        this.key = sha256(secret);
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public UsernameRecoveryEncryptedWorkflowState protect(
            String plaintext, String authFlowSessionId, int revision) {
        try {
            if (plaintext == null || plaintext.isBlank()) {
                throw new IllegalArgumentException("plaintext must not be blank");
            }
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(authFlowSessionId, revision, keyVersion));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return new UsernameRecoveryEncryptedWorkflowState(
                    authFlowSessionId, revision, keyVersion,
                    UsernameRecoveryEncryptedWorkflowState.ALGORITHM,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(packed));
        } catch (Exception exception) {
            throw new UsernameRecoveryWorkflowStateProtectionException(
                    "Unable to protect Username Recovery workflow state", exception);
        }
    }

    @Override
    public String unprotect(UsernameRecoveryEncryptedWorkflowState encryptedState) {
        try {
            Objects.requireNonNull(encryptedState, "encryptedState must not be null");
            if (!keyVersion.equals(encryptedState.keyVersion())) {
                throw new IllegalArgumentException("unknown workflow-state protection key version");
            }
            byte[] packed = Base64.getUrlDecoder().decode(encryptedState.ciphertext());
            if (packed.length <= IV_BYTES) {
                throw new IllegalArgumentException("invalid encrypted workflow-state payload");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[packed.length - IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            System.arraycopy(packed, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(encryptedState.authFlowSessionId(), encryptedState.revision(),
                    encryptedState.keyVersion()));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new UsernameRecoveryWorkflowStateProtectionException(
                    "Unable to unprotect Username Recovery workflow state", exception);
        }
    }

    private static byte[] aad(String authFlowSessionId, int revision, String version) {
        String value = CAPABILITY + "|" + version + "|" + authFlowSessionId + "|" + revision;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
