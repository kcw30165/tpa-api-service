package com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsernameRecoveryAuthFlowContextBindingTest {
    @Test void bindsAndLoadsContextWithoutChangingEnvelopeOwnershipFields() {
        var binding = new UsernameRecoveryAuthFlowContextBinding(
                new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(
                        "v1", "0123456789abcdef0123456789abcdef"));
        var session = session(); var context = context();
        var bound = binding.bind(session, context, Instant.parse("2026-08-13T00:01:00Z"));
        assertThat(bound.status()).isEqualTo(AuthFlowStatus.STARTED);
        assertThat(bound.createdAt()).isEqualTo(session.createdAt());
        assertThat(bound.expiresAt()).isEqualTo(session.expiresAt());
        assertThat(bound.data().attributes()).containsOnlyKeys(
                UsernameRecoveryAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT,
                UsernameRecoveryAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION,
                UsernameRecoveryAuthFlowContextAttributeNames.CONTEXT_ALGORITHM);
        assertThat(binding.load(bound)).isEqualTo(context);
    }
    @Test void missingContextFailsClosed() {
        var binding = new UsernameRecoveryAuthFlowContextBinding(
                new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(
                        "v1", "0123456789abcdef0123456789abcdef"));
        assertThatThrownBy(() -> binding.load(session())).isInstanceOf(IllegalStateException.class);
    }
    private static AuthFlowSession session() {
        return new AuthFlowSession(new AuthFlowSessionId("afs_context-1"), AuthFlowScenario.USERNAME_RECOVERY,
                AuthFlowStatus.STARTED, "csrf", null, new AuthFlowSessionData(null, null, Map.of()),
                Instant.parse("2026-08-13T00:00:00Z"), Instant.parse("2026-08-13T00:00:00Z"),
                Instant.parse("2026-08-13T00:10:00Z"));
    }
    private static UsernameRecoveryAuthFlowContext context() {
        return new UsernameRecoveryAuthFlowContext(UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryVerifiedDestination("member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH, List.of(new UsernameRecoverySessionCandidate(new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_1"), "DB", "DBA", true, "username-1",
                        UsernameRecoveryDeliveryState.AVAILABLE), null, null)));
    }
}
