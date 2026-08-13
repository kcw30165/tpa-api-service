package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.*;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AesGcmUsernameRecoveryAuthFlowContextProtectorAdapterTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final AuthFlowSessionId id = new AuthFlowSessionId("afs_context-1");
    @Test void roundTripsRandomizedContextWithoutPlaintextLeakage() {
        var p = new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", SECRET);
        var first = p.protect(id, context()); var second = p.protect(id, context());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(p.unprotect(id, first)).isEqualTo(context());
        assertThat(first.ciphertext()).doesNotContain("member@example.com", "username-1", "sch_1");
        assertThat(first.toString()).doesNotContain(first.ciphertext());
    }
    @Test void rejectsTamperWrongKeyAndSessionRebinding() {
        var p = new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", SECRET);
        var encrypted = p.protect(id, context());
        assertThatThrownBy(() -> p.unprotect(id, encrypted.withCiphertext("A" + encrypted.ciphertext().substring(1))))
                .isInstanceOf(UsernameRecoveryAuthFlowContextProtectionException.class);
        assertThatThrownBy(() -> new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(
                "v1", "abcdef0123456789abcdef0123456789").unprotect(id, encrypted))
                .isInstanceOf(UsernameRecoveryAuthFlowContextProtectionException.class);
        assertThatThrownBy(() -> p.unprotect(new AuthFlowSessionId("afs_context-2"), encrypted))
                .isInstanceOf(UsernameRecoveryAuthFlowContextProtectionException.class);
    }
    private UsernameRecoveryAuthFlowContext context() {
        return new UsernameRecoveryAuthFlowContext(UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryVerifiedDestination("member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH, List.of(new UsernameRecoverySessionCandidate(new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_1"), "DB", "DBA", true, "username-1",
                        UsernameRecoveryDeliveryState.AVAILABLE), null, null)));
    }
}
