package com.bct.ngtpa.apiservice.application.dto.auth.login.preauth;

import java.util.Objects;

public record LoginPreAuthEncryptedContext(
        String keyVersion,
        String algorithm,
        String iv,
        String ciphertext) {
    public LoginPreAuthEncryptedContext {
        Objects.requireNonNull(keyVersion); Objects.requireNonNull(algorithm);
        Objects.requireNonNull(iv); Objects.requireNonNull(ciphertext);
    }
    @Override public String toString() {
        return "LoginPreAuthEncryptedContext[keyVersion=" + keyVersion
                + ", algorithm=" + algorithm + ", iv=<redacted>, ciphertext=<redacted>]";
    }
}
