package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryEncryptedWorkflowState;
import org.junit.jupiter.api.Test;

class AesGcmUsernameRecoveryWorkflowStateProtectorAdapterTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void roundTripUsesRandomizedAuthenticatedEncryption() {
        var protector = new AesGcmUsernameRecoveryWorkflowStateProtectorAdapter("v1", SECRET);
        var first = protector.protect("{\"protected\":true}", "afs_1", 4);
        var second = protector.protect("{\"protected\":true}", "afs_1", 4);
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(protector.unprotect(first)).isEqualTo("{\"protected\":true}");
    }

    @Test
    void tamperWrongKeyAndWrongBindingFailClosed() {
        var protector = new AesGcmUsernameRecoveryWorkflowStateProtectorAdapter("v1", SECRET);
        var state = protector.protect("payload", "afs_1", 4);
        var tampered = new UsernameRecoveryEncryptedWorkflowState(
                state.authFlowSessionId(), state.revision(), state.keyVersion(), state.algorithm(),
                "A" + state.ciphertext().substring(1));
        assertThatThrownBy(() -> protector.unprotect(tampered))
                .isInstanceOf(UsernameRecoveryWorkflowStateProtectionException.class);
        var wrongKey = new AesGcmUsernameRecoveryWorkflowStateProtectorAdapter(
                "v1", "abcdef0123456789abcdef0123456789");
        assertThatThrownBy(() -> wrongKey.unprotect(state))
                .isInstanceOf(UsernameRecoveryWorkflowStateProtectionException.class);
        var rebound = new UsernameRecoveryEncryptedWorkflowState(
                "afs_2", state.revision(), state.keyVersion(), state.algorithm(), state.ciphertext());
        assertThatThrownBy(() -> protector.unprotect(rebound))
                .isInstanceOf(UsernameRecoveryWorkflowStateProtectionException.class);
    }
}
