package com.bct.ngtpa.apiservice.application.usecase.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpReplacementOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.Sha256OtpCodeHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultOtpChallengeServiceReplaceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-27T09:00:00Z");

    @Test
    void replaceReturnsNotFoundWhenCurrentChallengeMissing() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        FakeOtpCodeGenerator codeGenerator = new FakeOtpCodeGenerator("654321");

        StepVerifier.create(service(statePort, codeGenerator, new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("missing", 60)))
                .assertNext(result -> {
                    assertThat(result.previousOtpChallengeId()).isEqualTo("missing");
                    assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.NOT_FOUND);
                    assertThat(result.rawCode()).isNull();
                    assertThat(result.newOtpChallengeId()).isNull();
                })
                .verifyComplete();

        assertThat(codeGenerator.invocationCount).isZero();
        assertThat(statePort.updatedChallenges).isEmpty();
        assertThat(statePort.savedChallenges).isEmpty();
    }

    @Test
    void replaceRejectsAlreadyVerifiedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.VERIFIED).withVerifiedAt(FIXED_NOW.minusSeconds(30)));
        FakeOtpCodeGenerator codeGenerator = new FakeOtpCodeGenerator("654321");

        StepVerifier.create(service(statePort, codeGenerator, new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.ALREADY_VERIFIED))
                .verifyComplete();

        assertThat(codeGenerator.invocationCount).isZero();
        assertThat(statePort.updatedChallenges).isEmpty();
        assertThat(statePort.savedChallenges).isEmpty();
    }

    @Test
    void replaceLockedChallengeWithFreshVerificationAllowance() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge()
                .withStatus(OtpStatus.LOCKED)
                .withFailedAttemptCount(5));
        FakeOtpCodeGenerator codeGenerator = new FakeOtpCodeGenerator("654321");

        StepVerifier.create(service(
                        statePort,
                        codeGenerator,
                        new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.REPLACED);
                    assertThat(result.newOtpChallengeId()).isEqualTo("otp-new-001");
                    assertThat(result.rawCode()).isEqualTo("654321");
                })
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.INVALIDATED);
        assertThat(statePort.lastSaved.status()).isEqualTo(OtpStatus.CREATED);
        assertThat(statePort.lastSaved.failedAttemptCount()).isZero();
        assertThat(statePort.lastSaved.maxVerifyAttempts()).isEqualTo(5);
    }

    @Test
    void replaceMarksExpiredCurrentChallengeAndRejects() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withExpiresAt(FIXED_NOW));

        StepVerifier.create(service(statePort, new FakeOtpCodeGenerator("654321"), new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .assertNext(result -> assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.EXPIRED))
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.EXPIRED);
        assertThat(statePort.savedChallenges).isEmpty();
    }

    @Test
    void replaceRejectsWhenCooldownActive() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withLastSentAt(FIXED_NOW.minusSeconds(30)));
        FakeOtpCodeGenerator codeGenerator = new FakeOtpCodeGenerator("654321");

        StepVerifier.create(service(statePort, codeGenerator, new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.COOLDOWN_ACTIVE);
                    assertThat(result.retryAfterSeconds()).isEqualTo(30);
                    assertThat(result.rawCode()).isNull();
                })
                .verifyComplete();

        assertThat(codeGenerator.invocationCount).isZero();
        assertThat(statePort.store.get("otp-001").status()).isEqualTo(OtpStatus.CREATED);
        assertThat(statePort.savedChallenges).isEmpty();
    }

    @Test
    void replaceRejectsWhenMaxResendExceeded() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withResendAttemptCount(3));
        FakeOtpCodeGenerator codeGenerator = new FakeOtpCodeGenerator("654321");

        StepVerifier.create(service(statePort, codeGenerator, new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 0)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.MAX_RESEND_EXCEEDED);
                    assertThat(result.rawCode()).isNull();
                    assertThat(result.remainingResendAttempts()).isZero();
                })
                .verifyComplete();

        assertThat(codeGenerator.invocationCount).isZero();
        assertThat(statePort.updatedChallenges).isEmpty();
        assertThat(statePort.savedChallenges).isEmpty();
    }

    @Test
    void replaceInvalidatesOldChallengeAndCreatesNewChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge());
        FakeOtpCodeGenerator codeGenerator = new FakeOtpCodeGenerator("654321");

        StepVerifier.create(service(statePort, codeGenerator, new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.REPLACED);
                    assertThat(result.previousOtpChallengeId()).isEqualTo("otp-001");
                    assertThat(result.newOtpChallengeId()).isEqualTo("otp-new-001");
                    assertThat(result.rawCode()).isEqualTo("654321");
                    assertThat(result.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(300));
                    assertThat(result.expiresInSeconds()).isEqualTo(300);
                    assertThat(result.remainingResendAttempts()).isEqualTo(2);
                })
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.INVALIDATED);
        assertThat(statePort.lastUpdated.invalidatedAt()).isEqualTo(FIXED_NOW);
        OtpChallenge savedChallenge = statePort.lastSaved;
        assertThat(savedChallenge.otpChallengeId()).isEqualTo("otp-new-001");
        assertThat(savedChallenge.status()).isEqualTo(OtpStatus.CREATED);
        assertThat(savedChallenge.scenario()).isEqualTo(OtpScenario.REGISTRATION);
        assertThat(savedChallenge.workflowRef()).isEqualTo("workflow-001");
        assertThat(savedChallenge.channel()).isEqualTo(OtpChannel.SMS);
        assertThat(savedChallenge.destinationHash()).isEqualTo("destination-hash");
        assertThat(savedChallenge.maxVerifyAttempts()).isEqualTo(5);
        assertThat(savedChallenge.maxResendAttempts()).isEqualTo(3);
        assertThat(savedChallenge.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(savedChallenge.lastSentAt()).isEqualTo(FIXED_NOW);
        assertThat(savedChallenge.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(300));
        assertThat(savedChallenge.codeHash()).isNotEqualTo("654321");
    }

    @Test
    void replaceResetsFailedAttemptCountForNewChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withFailedAttemptCount(2));

        StepVerifier.create(service(statePort, new FakeOtpCodeGenerator("654321"), new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(statePort.lastSaved.failedAttemptCount()).isZero();
    }

    @Test
    void replaceIncrementsResendAttemptCount() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withResendAttemptCount(1));

        StepVerifier.create(service(statePort, new FakeOtpCodeGenerator("654321"), new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .assertNext(result -> assertThat(result.remainingResendAttempts()).isEqualTo(1))
                .verifyComplete();

        assertThat(statePort.lastSaved.resendAttemptCount()).isEqualTo(2);
    }

    @Test
    void replaceReturnsRawCodeOnlyInResultNotPersistedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge());

        StepVerifier.create(service(statePort, new FakeOtpCodeGenerator("654321"), new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 60)))
                .assertNext(result -> assertThat(result.rawCode()).isEqualTo("654321"))
                .verifyComplete();

        assertThat(statePort.lastSaved.codeHash()).isNotEqualTo("654321");
        assertThat(statePort.lastSaved.codeHash()).isEqualTo(new Sha256OtpCodeHasher().hash("654321"));
    }

    @Test
    void replaceDoesNotGenerateRawCodeForRejectedOutcomes() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withResendAttemptCount(3));
        FakeOtpCodeGenerator codeGenerator = new FakeOtpCodeGenerator("654321");

        StepVerifier.create(service(statePort, codeGenerator, new FakeOtpChallengeIdGenerator("otp-new-001"))
                        .replaceChallenge(command("otp-001", 0)))
                .assertNext(result -> {
                    assertThat(result.outcome()).isEqualTo(OtpReplacementOutcome.MAX_RESEND_EXCEEDED);
                    assertThat(result.rawCode()).isNull();
                })
                .verifyComplete();

        assertThat(codeGenerator.invocationCount).isZero();
    }

    private DefaultOtpChallengeAdapter service(
            FakeOtpChallengeStatePort statePort,
            FakeOtpCodeGenerator codeGenerator,
            FakeOtpChallengeIdGenerator otpChallengeIdGenerator) {
        return new DefaultOtpChallengeAdapter(
                statePort,
                codeGenerator,
                new Sha256OtpCodeHasher(),
                otpChallengeIdGenerator,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private ReplaceOtpChallengeCommand command(String currentOtpChallengeId, int resendCooldownSeconds) {
        return new ReplaceOtpChallengeCommand(currentOtpChallengeId, 6, true, 300, resendCooldownSeconds, FIXED_NOW);
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
                FIXED_NOW.plusSeconds(240),
                FIXED_NOW.minusSeconds(120),
                null,
                null));
    }

    private static final class FakeOtpChallengeStatePort implements OtpChallengeStatePort {
        private final Map<String, OtpChallenge> store = new HashMap<>();
        private final List<OtpChallenge> updatedChallenges = new ArrayList<>();
        private final List<OtpChallenge> savedChallenges = new ArrayList<>();
        private OtpChallenge lastUpdated;
        private OtpChallenge lastSaved;

        void store(TestOtpChallenge challenge) {
            store.put(challenge.value.otpChallengeId(), challenge.value);
        }

        @Override
        public Mono<Void> save(OtpChallenge challenge) {
            lastSaved = challenge;
            savedChallenges.add(challenge);
            store.put(challenge.otpChallengeId(), challenge);
            return Mono.empty();
        }

        @Override
        public Mono<Optional<OtpChallenge>> findById(String otpChallengeId) {
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

    private static final class FakeOtpCodeGenerator implements OtpCodeGenerator {
        private final String value;
        private int invocationCount;

        private FakeOtpCodeGenerator(String value) {
            this.value = value;
        }

        @Override
        public String generate(int length, boolean numericOnly) {
            invocationCount++;
            return value;
        }
    }

    private static final class FakeOtpChallengeIdGenerator implements OtpChallengeIdGenerator {
        private final String value;

        private FakeOtpChallengeIdGenerator(String value) {
            this.value = value;
        }

        @Override
        public String generate() {
            return value;
        }
    }

    private static final class TestOtpChallenge {
        private final OtpChallenge value;

        private TestOtpChallenge(OtpChallenge value) {
            this.value = value;
        }

        private TestOtpChallenge withStatus(OtpStatus status) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(), value.destinationHash(),
                    value.codeHash(), status, value.failedAttemptCount(), value.maxVerifyAttempts(), value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(), value.invalidatedAt(), value.verifiedAt()));
        }

        private TestOtpChallenge withVerifiedAt(Instant verifiedAt) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(), value.destinationHash(),
                    value.codeHash(), value.status(), value.failedAttemptCount(), value.maxVerifyAttempts(), value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(), value.invalidatedAt(), verifiedAt));
        }

        private TestOtpChallenge withExpiresAt(Instant expiresAt) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(), value.destinationHash(),
                    value.codeHash(), value.status(), value.failedAttemptCount(), value.maxVerifyAttempts(), value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), expiresAt, value.lastSentAt(), value.invalidatedAt(), value.verifiedAt()));
        }

        private TestOtpChallenge withLastSentAt(Instant lastSentAt) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(), value.destinationHash(),
                    value.codeHash(), value.status(), value.failedAttemptCount(), value.maxVerifyAttempts(), value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), lastSentAt, value.invalidatedAt(), value.verifiedAt()));
        }

        private TestOtpChallenge withResendAttemptCount(int resendAttemptCount) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(), value.destinationHash(),
                    value.codeHash(), value.status(), value.failedAttemptCount(), value.maxVerifyAttempts(), resendAttemptCount,
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(), value.invalidatedAt(), value.verifiedAt()));
        }

        private TestOtpChallenge withFailedAttemptCount(int failedAttemptCount) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(), value.destinationHash(),
                    value.codeHash(), value.status(), failedAttemptCount, value.maxVerifyAttempts(), value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(), value.invalidatedAt(), value.verifiedAt()));
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