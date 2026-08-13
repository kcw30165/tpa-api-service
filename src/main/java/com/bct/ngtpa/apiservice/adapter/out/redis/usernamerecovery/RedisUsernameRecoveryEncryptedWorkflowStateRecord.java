package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryEncryptedWorkflowState;

/** Minimal Redis wrapper. Protected workflow data exists only inside ciphertext. */
record RedisUsernameRecoveryEncryptedWorkflowStateRecord(
        String authFlowSessionId,
        int revision,
        String keyVersion,
        String algorithm,
        String ciphertext) {

    static RedisUsernameRecoveryEncryptedWorkflowStateRecord from(
            UsernameRecoveryEncryptedWorkflowState state) {
        return new RedisUsernameRecoveryEncryptedWorkflowStateRecord(
                state.authFlowSessionId(), state.revision(), state.keyVersion(),
                state.algorithm(), state.ciphertext());
    }

    UsernameRecoveryEncryptedWorkflowState toEncryptedState() {
        return new UsernameRecoveryEncryptedWorkflowState(
                authFlowSessionId, revision, keyVersion, algorithm, ciphertext);
    }

    @Override
    public String toString() {
        return "RedisUsernameRecoveryEncryptedWorkflowStateRecord[authFlowSessionId=<redacted>, revision="
                + revision + ", keyVersion=" + keyVersion + ", algorithm=" + algorithm
                + ", ciphertext=<redacted>]";
    }
}
