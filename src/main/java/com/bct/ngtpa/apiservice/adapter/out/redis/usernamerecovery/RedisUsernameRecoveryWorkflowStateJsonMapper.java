package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.exception.CacheException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/**
 * JSON serialization boundary for the protected Username Recovery workflow-state sidecar.
 *
 * <p>Serializes only {@link RedisUsernameRecoveryWorkflowStateRecord} sidecar fields and rebuilds
 * the {@link UsernameRecoveryWorkflowState} application object on load. Serialization and
 * deserialization failures are never swallowed: they are wrapped in a {@link CacheException} with
 * a generic safe message (no stored content is disclosed). The mapper has no dependency on the
 * generic Username Recovery session aggregate or session id.
 */
public class RedisUsernameRecoveryWorkflowStateJsonMapper {

    private final ObjectMapper objectMapper;

    public RedisUsernameRecoveryWorkflowStateJsonMapper() {
        this(new ObjectMapper());
    }

    RedisUsernameRecoveryWorkflowStateJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String toJson(UsernameRecoveryWorkflowState state) {
        Objects.requireNonNull(state, "state must not be null");
        try {
            return objectMapper.writeValueAsString(
                    RedisUsernameRecoveryWorkflowStateRecord.from(state));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Username recovery workflow state serialization failed",
                    exception);
        }
    }

    public UsernameRecoveryWorkflowState fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new CacheException("Username recovery workflow state deserialization failed");
        }
        try {
            return objectMapper.readValue(json, RedisUsernameRecoveryWorkflowStateRecord.class)
                    .toWorkflowState();
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new CacheException("Username recovery workflow state deserialization failed",
                    exception);
        }
    }
}