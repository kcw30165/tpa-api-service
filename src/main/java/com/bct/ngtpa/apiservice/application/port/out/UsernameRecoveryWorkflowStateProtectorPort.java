package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryEncryptedWorkflowState;

/** Protects the complete serialized Username Recovery sidecar payload. */
public interface UsernameRecoveryWorkflowStateProtectorPort {
    UsernameRecoveryEncryptedWorkflowState protect(
            String plaintext, String authFlowSessionId, int revision);

    String unprotect(UsernameRecoveryEncryptedWorkflowState encryptedState);
}
