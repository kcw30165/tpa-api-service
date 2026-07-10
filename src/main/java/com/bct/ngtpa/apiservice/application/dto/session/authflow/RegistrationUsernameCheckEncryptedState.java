package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;

public record RegistrationUsernameCheckEncryptedState(
        String ciphertext,
        String keyVersion,
        String algorithm) {
    public RegistrationUsernameCheckEncryptedState {
        ciphertext = requireText(ciphertext, "ciphertext");
        keyVersion = requireText(keyVersion, "keyVersion");
        algorithm = algorithm == null || algorithm.isBlank()
                ? RegistrationAuthFlowEncryptedContext.DEFAULT_ALGORITHM
                : algorithm.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "RegistrationUsernameCheckEncryptedState[ciphertext=<redacted>, keyVersion=" + keyVersion
                + ", algorithm=" + algorithm + "]";
    }
}
