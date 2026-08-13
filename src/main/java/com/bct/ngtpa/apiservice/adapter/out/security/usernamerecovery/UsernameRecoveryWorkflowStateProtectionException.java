package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

/** Safe failure raised when Username Recovery workflow-state protection fails. */
public final class UsernameRecoveryWorkflowStateProtectionException extends RuntimeException {
    public UsernameRecoveryWorkflowStateProtectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
