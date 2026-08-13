package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryWorkflowStateProtectorAdapter;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RedisUsernameRecoveryWorkflowStateJsonMapperTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final RedisUsernameRecoveryWorkflowStateJsonMapper mapper =
            new RedisUsernameRecoveryWorkflowStateJsonMapper(
                    new AesGcmUsernameRecoveryWorkflowStateProtectorAdapter("v1", SECRET));

    @Test
    void encryptedRoundTripDoesNotPersistProtectedValues() {
        UsernameRecoveryWorkflowState state = state();
        String json = mapper.toJson(state);
        assertThat(mapper.fromJson(json)).isEqualTo(state);
        assertThat(json).contains("\"revision\":0", "\"keyVersion\":\"v1\"",
                "\"algorithm\":\"AES_GCM_V1\"", "\"ciphertext\"");
        assertThat(json).doesNotContain("member@example.com", "user-sensitive", "sch_sensitive", "trust-sensitive");
    }

    @Test
    void identicalStatesUseDifferentCiphertext() {
        assertThat(mapper.toJson(state())).isNotEqualTo(mapper.toJson(state()));
    }

    @Test
    void plaintextLegacyRecordAndTamperingFailClosed() {
        assertThatThrownBy(() -> mapper.fromJson("{\"revision\":0,\"destination\":\"member@example.com\"}"))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed")
                .hasMessageNotContaining("member@example.com");
        String encrypted = mapper.toJson(state());
        String tampered = encrypted.replaceFirst("ciphertext\":\".", "ciphertext\":\"A");
        assertThatThrownBy(() -> mapper.fromJson(tampered))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed");
    }

    private static UsernameRecoveryWorkflowState state() {
        return new UsernameRecoveryWorkflowState(
                new AuthFlowSessionId("afs_sensitive"), UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.ENGLISH,
                List.of(new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(new UsernameRecoverySchemeRef("sch_sensitive"),
                                "DB", "trust-sensitive", true, "user-sensitive",
                                UsernameRecoveryDeliveryState.AVAILABLE), null, null)));
    }
}
