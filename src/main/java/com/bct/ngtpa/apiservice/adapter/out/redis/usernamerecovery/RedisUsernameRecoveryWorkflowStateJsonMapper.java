package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryWorkflowStateProtectorAdapter;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryEncryptedWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryWorkflowStateProtectorPort;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.UUID;

/** Encrypting JSON boundary for the Username Recovery workflow-state sidecar. */
public class RedisUsernameRecoveryWorkflowStateJsonMapper {
    private final ObjectMapper objectMapper;
    private final UsernameRecoveryWorkflowStateProtectorPort protector;

    /** Test-friendly constructor. Production wiring always supplies configured protection. */
    public RedisUsernameRecoveryWorkflowStateJsonMapper() {
        this(new ObjectMapper(), new AesGcmUsernameRecoveryWorkflowStateProtectorAdapter(
                "test-v1", "test-only-" + UUID.randomUUID() + "-secret"));
    }

    public RedisUsernameRecoveryWorkflowStateJsonMapper(
            UsernameRecoveryWorkflowStateProtectorPort protector) {
        this(new ObjectMapper(), protector);
    }

    RedisUsernameRecoveryWorkflowStateJsonMapper(
            ObjectMapper objectMapper, UsernameRecoveryWorkflowStateProtectorPort protector) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.protector = Objects.requireNonNull(protector, "protector must not be null");
    }

    public String toJson(UsernameRecoveryWorkflowState state) {
        Objects.requireNonNull(state, "state must not be null");
        try {
            String plaintext = objectMapper.writeValueAsString(
                    RedisUsernameRecoveryWorkflowStateRecord.from(state));
            UsernameRecoveryEncryptedWorkflowState encrypted = protector.protect(
                    plaintext, state.authFlowSessionId().value(), state.revision().value());
            return objectMapper.writeValueAsString(
                    RedisUsernameRecoveryEncryptedWorkflowStateRecord.from(encrypted));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Username recovery workflow state serialization failed", exception);
        }
    }

    public UsernameRecoveryWorkflowState fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new CacheException("Username recovery workflow state deserialization failed");
        }
        try {
            RedisUsernameRecoveryEncryptedWorkflowStateRecord wrapper = objectMapper.readValue(
                    json, RedisUsernameRecoveryEncryptedWorkflowStateRecord.class);
            String plaintext = protector.unprotect(wrapper.toEncryptedState());
            UsernameRecoveryWorkflowState state = objectMapper.readValue(
                    plaintext, RedisUsernameRecoveryWorkflowStateRecord.class).toWorkflowState();
            if (!state.authFlowSessionId().value().equals(wrapper.authFlowSessionId())
                    || state.revision().value() != wrapper.revision()) {
                throw new IllegalStateException("workflow-state wrapper binding mismatch");
            }
            return state;
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Username recovery workflow state deserialization failed", exception);
        }
    }
}
