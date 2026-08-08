package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Minimal persistence record for the protected Username Recovery session.
 *
 * <p>Persists only the Task 010 minimal protected fields (session id, scenario,
 * status, revision, CSRF hash, protected verified destination and channel,
 * language, timestamps, and candidate list with Scheme-Ref, account environment,
 * trust code, registration flag, username only when registered, delivery state,
 * requested timestamp, and sender reference). Policy numbers, certificate
 * numbers, scheme type, termination data, DOB, raw identity, captcha answers,
 * raw CSRF tokens, HTTP/cookie data, and APIM payload fields are never stored.
 *
 * <p>{@link #toString()} and {@link Candidate#toString()} redact the session id,
 * CSRF hash, destination, Scheme-Ref, username, and sender reference.
 */
record RedisUsernameRecoverySessionRecord(
        String sessionId,
        String scenario,
        String status,
        int revision,
        String csrfHash,
        String destination,
        String destinationChannel,
        String language,
        String createdAt,
        String expiresAt,
        List<Candidate> candidates) {

    static RedisUsernameRecoverySessionRecord from(UsernameRecoverySession session) {
        return new RedisUsernameRecoverySessionRecord(
                session.sessionId().value(),
                session.scenario().name(),
                session.status().name(),
                session.revision().value(),
                session.csrfHash().value(),
                session.destination().value(),
                session.destination().channel().name(),
                session.language().toLanguageTag(),
                session.createdAt().toString(),
                session.expiresAt().toString(),
                session.candidates().stream().map(Candidate::from).toList());
    }

    UsernameRecoverySession toSession() {
        return new UsernameRecoverySession(
                new UsernameRecoverySessionId(sessionId),
                UsernameRecoverySession.Scenario.valueOf(scenario),
                UsernameRecoveryWorkflowStatus.valueOf(status),
                new UsernameRecoverySessionRevision(revision),
                new UsernameRecoveryCsrfHash(csrfHash),
                new UsernameRecoveryVerifiedDestination(destination,
                        UsernameRecoveryVerifiedDestination.Channel.valueOf(destinationChannel)),
                Locale.forLanguageTag(language),
                Instant.parse(createdAt),
                Instant.parse(expiresAt),
                candidates.stream().map(Candidate::toSessionCandidate).toList());
    }

    @Override
    public String toString() {
        return "RedisUsernameRecoverySessionRecord[sessionId=<redacted>, scenario=" + scenario
                + ", status=" + status + ", revision=" + revision
                + ", csrfHash=<redacted>, destination=<redacted>, destinationChannel="
                + destinationChannel + ", language=" + language + ", createdAt=" + createdAt
                + ", expiresAt=" + expiresAt + ", candidateCount=" + candidates.size() + "]";
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
