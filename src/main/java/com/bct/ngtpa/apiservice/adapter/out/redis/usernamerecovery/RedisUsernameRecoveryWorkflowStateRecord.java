package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Minimal persistence record for the protected Username Recovery workflow-state sidecar.
 *
 * <p>Persists only the Model B sidecar fields under
 * {@code MODEL_B_GENERIC_ENVELOPE_TYPED_SIDECAR}: the canonical auth-flow session id, workflow
 * status, workflow-state revision, protected verified destination and channel, language, and the
 * candidate list with Scheme-Ref, account environment, trust code, registration flag, username
 * only when registered, delivery state, requested timestamp, and sender reference. Scenario, CSRF
 * hash, created/updated/expiry timestamps, generic session revision, cookie data, and TTL are owned
 * by the generic envelope and are never persisted here.
 *
 * <p>{@link #toString()} and {@link Candidate#toString()} redact the auth-flow session id,
 * destination, Scheme-Ref, username, and sender reference. Reconstruction builds the
 * {@link UsernameRecoveryWorkflowState} application object whose constructor remains the
 * authoritative invariant validation.
 */
record RedisUsernameRecoveryWorkflowStateRecord(
        String authFlowSessionId,
        String status,
        int revision,
        String destination,
        String destinationChannel,
        String language,
        List<Candidate> candidates) {

    static RedisUsernameRecoveryWorkflowStateRecord from(UsernameRecoveryWorkflowState state) {
        return new RedisUsernameRecoveryWorkflowStateRecord(
                state.authFlowSessionId().value(),
                state.status().name(),
                state.revision().value(),
                state.destination().value(),
                state.destination().channel().name(),
                state.language().toLanguageTag(),
                state.candidates().stream().map(Candidate::from).toList());
    }

    UsernameRecoveryWorkflowState toWorkflowState() {
        return new UsernameRecoveryWorkflowState(
                new AuthFlowSessionId(authFlowSessionId),
                UsernameRecoveryWorkflowStatus.valueOf(status),
                new UsernameRecoveryWorkflowStateRevision(revision),
                new UsernameRecoveryVerifiedDestination(destination,
                        UsernameRecoveryVerifiedDestination.Channel.valueOf(destinationChannel)),
                Locale.forLanguageTag(language),
                candidates.stream().map(Candidate::toSessionCandidate).toList());
    }

    @Override
    public String toString() {
        return "RedisUsernameRecoveryWorkflowStateRecord[authFlowSessionId=<redacted>, status="
                + status + ", revision=" + revision
                + ", destination=<redacted>, destinationChannel=" + destinationChannel
                + ", language=" + language + ", candidateCount=" + candidates.size() + "]";
    }

    record Candidate(
            String schemeRef,
            String accountEnv,
            String trustCode,
            boolean registered,
            String userName,
            String deliveryState,
            String requestedAt,
            String senderReference) {

        static Candidate from(UsernameRecoverySessionCandidate candidate) {
            return new Candidate(
                    candidate.schemeRef().value(),
                    candidate.accountEnv(),
                    candidate.trustCode(),
                    candidate.registered(),
                    candidate.userName(),
                    candidate.deliveryState().name(),
                    candidate.requestedAt() == null ? null : candidate.requestedAt().toString(),
                    candidate.senderReference());
        }

        UsernameRecoverySessionCandidate toSessionCandidate() {
            return new UsernameRecoverySessionCandidate(
                    new UsernameRecoveryCandidate(
                            new UsernameRecoverySchemeRef(schemeRef),
                            accountEnv,
                            trustCode,
                            registered,
                            userName,
                            UsernameRecoveryDeliveryState.valueOf(deliveryState)),
                    requestedAt == null ? null : Instant.parse(requestedAt),
                    senderReference);
        }

        @Override
        public String toString() {
            return "Candidate[schemeRef=<redacted>, accountEnv=" + accountEnv
                    + ", trustCode=" + trustCode + ", registered=" + registered
                    + ", userName=" + (userName == null ? null : "<redacted>")
                    + ", deliveryState=" + deliveryState
                    + ", requestedAt=" + requestedAt
                    + ", senderReference=" + (senderReference == null ? null : "<redacted>") + "]";
        }
    }
}