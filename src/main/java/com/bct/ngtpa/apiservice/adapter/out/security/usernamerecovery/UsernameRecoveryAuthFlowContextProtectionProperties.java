package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

/** Mandatory protection settings for the Username Recovery auth-flow context. */
public record UsernameRecoveryAuthFlowContextProtectionProperties(
        boolean enabled, String keyVersion, String secret) {
    public UsernameRecoveryAuthFlowContextProtectionProperties {
        if (!enabled) {
            throw new IllegalStateException(
                    "Username Recovery auth-flow context protection must remain enabled");
        }
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalStateException(
                    "Username Recovery auth-flow context protection key version is required");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "Username Recovery auth-flow context protection secret must contain at least 32 characters");
        }
    }
}
