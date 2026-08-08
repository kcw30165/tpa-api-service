package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import java.util.Objects;

/** Typed load result distinguishing loaded, absent, and infrastructure-failure outcomes. */
public record UsernameRecoverySessionLoadResult(
        UsernameRecoverySessionMutationOutcome outcome,
        UsernameRecoverySession session) {

    public UsernameRecoverySessionLoadResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        boolean loaded = outcome == UsernameRecoverySessionMutationOutcome.LOADED;
        if (loaded && session == null) {
            throw new IllegalArgumentException("loaded outcome requires a session");
        }
        if (!loaded && session != null) {
            throw new IllegalArgumentException("non-loaded outcome must not carry a session");
        }
        if (!loaded
                && outcome != UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND
                && outcome != UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE) {
            throw new IllegalArgumentException("outcome is not valid for load");
        }
    }

    public static UsernameRecoverySessionLoadResult loaded(UsernameRecoverySession session) {
        return new UsernameRecoverySessionLoadResult(
                UsernameRecoverySessionMutationOutcome.LOADED, session);
    }

    public static UsernameRecoverySessionLoadResult notFound() {
        return new UsernameRecoverySessionLoadResult(
                UsernameRecoverySessionMutationOutcome.SESSION_NOT_FOUND, null);
    }

    public static UsernameRecoverySessionLoadResult systemFailure() {
        return new UsernameRecoverySessionLoadResult(
                UsernameRecoverySessionMutationOutcome.SYSTEM_FAILURE, null);
    }

    @Override
    public String toString() {
        return "UsernameRecoverySessionLoadResult[outcome=" + outcome
                + ", sessionPresent=" + (session != null) + "]";
    }
}
