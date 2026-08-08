package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import java.util.Objects;

/** Compare-and-set mutation command for a protected Username Recovery session. */
public record UsernameRecoverySessionMutationCommand(
        UsernameRecoverySessionId sessionId,
        UsernameRecoverySessionRevision expectedRevision,
        UsernameRecoverySession updatedSession) {

    public UsernameRecoverySessionMutationCommand {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(expectedRevision, "expectedRevision must not be null");
        Objects.requireNonNull(updatedSession, "updatedSession must not be null");
        if (!sessionId.equals(updatedSession.sessionId())) {
            throw new IllegalArgumentException("session identity must not change");
        }
        if (expectedRevision.value() == Integer.MAX_VALUE
                || updatedSession.revision().value() != expectedRevision.value() + 1) {
            throw new IllegalArgumentException(
                    "updated session revision must be exactly one greater than expected revision");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoverySessionMutationCommand[sessionId=, expectedRevision="
                + expectedRevision.value() + ", updatedSession=]";
    }
}
