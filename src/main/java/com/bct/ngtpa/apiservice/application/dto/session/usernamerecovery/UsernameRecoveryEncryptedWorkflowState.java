package com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery;

import java.util.Objects;

/** Authenticated encrypted representation of one Username Recovery workflow-state payload. */
public record UsernameRecoveryEncryptedWorkflowState(
        String authFlowSessionId,
        int revision,
        String keyVersion,
        String algorithm,
        String ciphertext) {

    public static final String ALGORITHM = "AES_GCM_V1";

    public UsernameRecoveryEncryptedWorkflowState {
        requireText(authFlowSessionId, "authFlowSessionId");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be nonnegative");
        }
        requireText(keyVersion, "keyVersion");
        if (!ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("unsupported workflow-state protection algorithm");
        }
        requireText(ciphertext, "ciphertext");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryEncryptedWorkflowState[authFlowSessionId=<redacted>, revision="
                + revision + ", keyVersion=" + keyVersion + ", algorithm=" + algorithm
                + ", ciphertext=<redacted>]";
    }
}
