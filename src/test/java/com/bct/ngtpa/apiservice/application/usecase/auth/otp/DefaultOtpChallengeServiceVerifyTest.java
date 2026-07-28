package com.bct.ngtpa.apiservice.application.usecase.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.service.auth.otp.Sha256OtpCodeHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultOtpChallengeServiceVerifyTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-26T11:00:00Z");

    @Test
    void verifyReturnsNotFoundWhenChallengeMissing() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("missing", "123456", FIXED_NOW)))
                .assertNext(result -> {
                    assertThat(result.otpChallengeId()).isEqualTo("missing");
                    assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.NOT_FOUND);
                    assertThat(result.remainingAttempts()).isZero();
                })
                .verifyComplete();

        assertThat(statePort.updatedChallenges).isEmpty();
    }

    @Test
    void verifyRejectsExpiredChallengeAndMarksExpired() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.CREATED).withExpiresAt(FIXED_NOW.minusSeconds(1)));

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "123456", FIXED_NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.EXPIRED))
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.EXPIRED);
    }

    @Test
    void verifyRejectsLockedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.LOCKED).withFailedAttemptCount(5));

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "123456", FIXED_NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.LOCKED))
                .verifyComplete();

        assertThat(statePort.updatedChallenges).isEmpty();
    }

    @Test
    void verifyRejectsInvalidatedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.INVALIDATED));

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "123456", FIXED_NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.INVALIDATED))
                .verifyComplete();

        assertThat(statePort.updatedChallenges).isEmpty();
    }

    @Test
    void verifyReturnsAlreadyVerifiedForVerifiedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.VERIFIED).withVerifiedAt(FIXED_NOW.minusSeconds(30)));

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "123456", FIXED_NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.ALREADY_VERIFIED))
                .verifyComplete();

        assertThat(statePort.updatedChallenges).isEmpty();
    }

    @Test
    void verifyReturnsAlreadyVerifiedForConsumedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.CONSUMED));

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "123456", FIXED_NOW)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.ALREADY_VERIFIED))
                .verifyComplete();

        assertThat(statePort.updatedChallenges).isEmpty();
    }

    @Test
    void verifyValidCodeMarksChallengeVerified() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge());

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "123456", FIXED_NOW)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.VALID);
                    assertThat(result.remainingAttempts()).isEqualTo(5);
                })
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.VERIFIED);
        assertThat(statePort.lastUpdated.verifiedAt()).isEqualTo(FIXED_NOW);
        assertThat(statePort.lastUpdated.failedAttemptCount()).isZero();
    }

    @Test
    void verifyInvalidCodeIncrementsFailedAttemptCount() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge());

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "000000", FIXED_NOW)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.INVALID);
                    assertThat(result.remainingAttempts()).isEqualTo(4);
                })
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.CREATED);
        assertThat(statePort.lastUpdated.failedAttemptCount()).isEqualTo(1);
    }

    @Test
    void verifyInvalidCodeLocksWhenMaxAttemptsReached() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withFailedAttemptCount(4));

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "000000", FIXED_NOW)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpVerificationOutcome.LOCKED);
                    assertThat(result.remainingAttempts()).isZero();
                })
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.LOCKED);
        assertThat(statePort.lastUpdated.failedAttemptCount()).isEqualTo(5);
    }

    @Test
    void verifyRejectsBlankSubmittedCodeSafely() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();

        StepVerifier
                .create(service(statePort).verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "   ", FIXED_NOW)))
                .expectError(IllegalArgumentException.class)
                .verify();

        assertThat(statePort.findCalls).isZero();
    }

    @Test
    void verifyDoesNotExposeRawOtpInPersistedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge());

        StepVerifier
                .create(service(statePort)
                        .verifyChallenge(new VerifyOtpChallengeCommand("otp-001", "000000", FIXED_NOW)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(statePort.lastUpdated.codeHash()).isNotEqualTo("000000");
        assertThat(statePort.lastUpdated.codeHash()).isNotEqualTo("123456");
    }

    private DefaultOtpChallengeAdapter service(FakeOtpChallengeStatePort statePort) {
        return new DefaultOtpChallengeAdapter(
                statePort,
                new Sha256OtpCodeHasher(),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private TestOtpChallenge sampleChallenge() {
        return new TestOtpChallenge(new OtpChallenge(
                "otp-001",
                OtpScenario.REGISTRATION,
                "workflow-001",
                OtpChannel.SMS,
                "destination-hash",
                new Sha256OtpCodeHasher().hash("123456"),
                OtpStatus.CREATED,
                0,
                5,
                0,
                3,
                FIXED_NOW.minusSeconds(60),
                FIXED_NOW.plusSeconds(300),
                FIXED_NOW.minusSeconds(60),
                null,
                null));
    }

    private static final class FakeOtpChallengeStatePort implements OtpChallengeStatePort {
        private final Map<String, OtpChallenge> store = new HashMap<>();
        private final java.util.List<OtpChallenge> updatedChallenges = new java.util.ArrayList<>();
        private OtpChallenge lastUpdated;
        private int findCalls;

        void store(TestOtpChallenge challenge) {
            store.put(challenge.value.otpChallengeId(), challenge.value);
        }

        @Override
        public Mono<Void> save(OtpChallenge challenge) {
            store.put(challenge.otpChallengeId(), challenge);
            return Mono.empty();
        }

        @Override
        public Mono<Optional<OtpChallenge>> findById(String otpChallengeId) {
            findCalls++;
            return Mono.just(Optional.ofNullable(store.get(otpChallengeId)));
        }

        @Override
        public Mono<Void> update(OtpChallenge challenge) {
            lastUpdated = challenge;
            updatedChallenges.add(challenge);
            store.put(challenge.otpChallengeId(), challenge);
            return Mono.empty();
        }

        @Override
        public Mono<Void> delete(String otpChallengeId) {
            store.remove(otpChallengeId);
            return Mono.empty();
        }
    }

    private static final class TestOtpChallenge {
        private final OtpChallenge value;

        private TestOtpChallenge(OtpChallenge value) {
            this.value = value;
        }

        private TestOtpChallenge withStatus(OtpStatus status) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(),
                    value.destinationHash(),
                    value.codeHash(), status, value.failedAttemptCount(), value.maxVerifyAttempts(),
                    value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(),
                    value.invalidatedAt(), value.verifiedAt()));
        }

        private TestOtpChallenge withExpiresAt(Instant expiresAt) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(),
                    value.destinationHash(),
                    value.codeHash(), value.status(), value.failedAttemptCount(), value.maxVerifyAttempts(),
                    value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), expiresAt, value.lastSentAt(), value.invalidatedAt(),
                    value.verifiedAt()));
        }

        private TestOtpChallenge withFailedAttemptCount(int failedAttemptCount) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(),
                    value.destinationHash(),
                    value.codeHash(), value.status(), failedAttemptCount, value.maxVerifyAttempts(),
                    value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(),
                    value.invalidatedAt(), value.verifiedAt()));
        }

        private TestOtpChallenge withVerifiedAt(Instant verifiedAt) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(),
                    value.destinationHash(),
                    value.codeHash(), value.status(), value.failedAttemptCount(), value.maxVerifyAttempts(),
                    value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(),
                    value.invalidatedAt(), verifiedAt));
        }

        private OtpChallenge copy(
                String otpChallengeId,
                OtpScenario scenario,
                String workflowRef,
                OtpChannel channel,
                String destinationHash,
                String codeHash,
                OtpStatus status,
                int failedAttemptCount,
                int maxVerifyAttempts,
                int resendAttemptCount,
                int maxResendAttempts,
                Instant createdAt,
                Instant expiresAt,
                Instant lastSentAt,
                Instant invalidatedAt,
                Instant verifiedAt) {
            return new OtpChallenge(
                    otpChallengeId,
                    scenario,
                    workflowRef,
                    channel,
                    destinationHash,
                    codeHash,
                    status,
                    failedAttemptCount,
                    maxVerifyAttempts,
                    resendAttemptCount,
                    maxResendAttempts,
                    createdAt,
                    expiresAt,
                    lastSentAt,
                    invalidatedAt,
                    verifiedAt);
        }
    }
}