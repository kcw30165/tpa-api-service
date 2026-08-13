package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

/** Fail-closed Username Recovery sidecar-protection configuration. */
public record UsernameRecoveryWorkflowStateProtectionProperties(
        boolean enabled, String keyVersion, String secret) {

    public UsernameRecoveryWorkflowStateProtectionProperties {
        if (!enabled) {
            throw new IllegalStateException("Username Recovery workflow-state protection must remain enabled");
        }
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalStateException("Username Recovery workflow-state protection key version is required");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "Username Recovery workflow-state protection secret must contain at least 32 characters");
        }
    }
}
