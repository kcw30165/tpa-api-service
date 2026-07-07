package com.bct.ngtpa.apiservice.application.usecase.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpChallengeIdGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.otp.Sha256OtpCodeHasher;
import java.lang.reflect.Constructor;
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

class DefaultOtpChallengeServiceCreateInvalidateTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-27T12:00:00Z");

    @Test
    void createChallengeGeneratesAndPersistsCreatedChallenge() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();

        StepVerifier.create(service(statePort).createChallenge(validCreateCommand()))
                .assertNext(result -> {
                    assertThat(result.otpChallengeId()).isEqualTo("otp-created-001");
                    assertThat(result.rawCode()).isEqualTo("123456");
                    assertThat(result.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(300));
                    assertThat(result.expiresInSeconds()).isEqualTo(300);
                })
                .verifyComplete();

        assertThat(statePort.savedChallenges).hasSize(1);
        OtpChallenge savedChallenge = statePort.lastSaved;
        assertThat(savedChallenge.status()).isEqualTo(OtpStatus.CREATED);
        assertThat(savedChallenge.scenario()).isEqualTo(OtpScenario.REGISTRATION);
        assertThat(savedChallenge.workflowRef()).isEqualTo("workflow-001");
        assertThat(savedChallenge.channel()).isEqualTo(OtpChannel.SMS);
        assertThat(savedChallenge.destinationHash()).isEqualTo("destination-hash");
        assertThat(savedChallenge.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(savedChallenge.lastSentAt()).isEqualTo(FIXED_NOW);
        assertThat(savedChallenge.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(300));
    }

    @Test
    void createChallengeStoresHashNotRawCode() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();

        StepVerifier.create(service(statePort).createChallenge(validCreateCommand()))
                .assertNext(result -> {
                    assertThat(statePort.lastSaved.codeHash()).isNotBlank();
                    assertThat(statePort.lastSaved.codeHash()).isNotEqualTo(result.rawCode());
                })
                .verifyComplete();
    }

    @Test
    void createChallengeInitializesAttemptCounters() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();

        StepVerifier.create(service(statePort).createChallenge(validCreateCommand()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(statePort.lastSaved.failedAttemptCount()).isZero();
        assertThat(statePort.lastSaved.resendAttemptCount()).isZero();
        assertThat(statePort.lastSaved.maxVerifyAttempts()).isEqualTo(5);
        assertThat(statePort.lastSaved.maxResendAttempts()).isEqualTo(3);
    }

    @Test
    void createChallengeRejectsInvalidCommand() {
        assertCreateRejected(new CreateOtpChallengeCommand(null, "workflow-001", OtpChannel.SMS, "destination-hash", 6,
                true, 300, 5, 3, FIXED_NOW));
        assertCreateRejected(new CreateOtpChallengeCommand(OtpScenario.REGISTRATION, "   ", OtpChannel.SMS,
                "destination-hash", 6, true, 300, 5, 3, FIXED_NOW));
        assertCreateRejected(new CreateOtpChallengeCommand(OtpScenario.REGISTRATION, "workflow-001", null,
                "destination-hash", 6, true, 300, 5, 3, FIXED_NOW));
        assertCreateRejected(new CreateOtpChallengeCommand(OtpScenario.REGISTRATION, "workflow-001", OtpChannel.SMS,
                "   ", 6, true, 300, 5, 3, FIXED_NOW));
        assertCreateRejected(new CreateOtpChallengeCommand(OtpScenario.REGISTRATION, "workflow-001", OtpChannel.SMS,
                "destination-hash", 0, true, 300, 5, 3, FIXED_NOW));
        assertCreateRejected(new CreateOtpChallengeCommand(OtpScenario.REGISTRATION, "workflow-001", OtpChannel.SMS,
                "destination-hash", 6, true, 0, 5, 3, FIXED_NOW));
        assertCreateRejected(new CreateOtpChallengeCommand(OtpScenario.REGISTRATION, "workflow-001", OtpChannel.SMS,
                "destination-hash", 6, true, 300, 0, 3, FIXED_NOW));
        assertCreateRejected(new CreateOtpChallengeCommand(OtpScenario.REGISTRATION, "workflow-001", OtpChannel.SMS,
                "destination-hash", 6, true, 300, 5, -1, FIXED_NOW));
    }

    @Test
    void createChallengeDoesNotCallMessageDelivery() {
        assertThat(DefaultOtpChallengeAdapter.class.getDeclaredFields())
                .allMatch(field -> !field.getType().getSimpleName().contains("MessageDelivery"));
        assertThat(DefaultOtpChallengeAdapter.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(parameterTypeNames(constructor))
                        .noneMatch(name -> name.contains("MessageDelivery")));
    }

    @Test
    void invalidateMissingChallengeIsIdempotentNoOp() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();

        StepVerifier
                .create(service(statePort)
                        .invalidateChallenge(new InvalidateOtpChallengeCommand("missing", null, FIXED_NOW)))
                .verifyComplete();

        assertThat(statePort.updatedChallenges).isEmpty();
    }

    @Test
    void invalidateCreatedChallengeMarksInvalidated() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.CREATED));

        StepVerifier
                .create(service(statePort)
                        .invalidateChallenge(new InvalidateOtpChallengeCommand("otp-001", null, FIXED_NOW)))
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.INVALIDATED);
        assertThat(statePort.lastUpdated.invalidatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void invalidateVerifiedChallengeMarksInvalidated() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(sampleChallenge().withStatus(OtpStatus.VERIFIED).withVerifiedAt(FIXED_NOW.minusSeconds(30)));

        StepVerifier
                .create(service(statePort)
                        .invalidateChallenge(new InvalidateOtpChallengeCommand("otp-001", null, FIXED_NOW)))
                .verifyComplete();

        assertThat(statePort.lastUpdated.status()).isEqualTo(OtpStatus.INVALIDATED);
        assertThat(statePort.lastUpdated.invalidatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void invalidateAlreadyInvalidatedChallengeIsIdempotent() {
        FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
        statePort.store(
                sampleChallenge().withStatus(OtpStatus.INVALIDATED).withInvalidatedAt(FIXED_NOW.minusSeconds(20)));

        StepVerifier
                .create(service(statePort)
                        .invalidateChallenge(new InvalidateOtpChallengeCommand("otp-001", null, FIXED_NOW)))
                .verifyComplete();

        assertThat(statePort.updatedChallenges).isEmpty();
    }

    @Test
    void invalidateExpiredLockedOrConsumedChallengeIsSafe() {
        for (OtpStatus status : new OtpStatus[] { OtpStatus.EXPIRED, OtpStatus.LOCKED, OtpStatus.CONSUMED }) {
            FakeOtpChallengeStatePort statePort = new FakeOtpChallengeStatePort();
            statePort.store(sampleChallenge().withStatus(status));

            StepVerifier
                    .create(service(statePort)
                            .invalidateChallenge(new InvalidateOtpChallengeCommand("otp-001", null, FIXED_NOW)))
                    .verifyComplete();

            assertThat(statePort.updatedChallenges).isEmpty();
            assertThat(statePort.savedChallenges).isEmpty();
        }
    }

    private void assertCreateRejected(CreateOtpChallengeCommand command) {
        StepVerifier.create(service(new FakeOtpChallengeStatePort()).createChallenge(command))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private List<String> parameterTypeNames(Constructor<?> constructor) {
        List<String> names = new ArrayList<>();
        for (Class<?> parameterType : constructor.getParameterTypes()) {
            names.add(parameterType.getSimpleName());
        }
        return names;
    }

    private DefaultOtpChallengeAdapter service(FakeOtpChallengeStatePort statePort) {
        return new DefaultOtpChallengeAdapter(
                statePort,
                new FakeOtpCodeGenerator("123456"),
                new Sha256OtpCodeHasher(),
                new FakeOtpChallengeIdGenerator("otp-created-001"),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private CreateOtpChallengeCommand validCreateCommand() {
        return new CreateOtpChallengeCommand(
                OtpScenario.REGISTRATION,
                "workflow-001",
                OtpChannel.SMS,
                "destination-hash",
                6,
                true,
                300,
                5,
                3,
                FIXED_NOW);
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
                FIXED_NOW.minusSeconds(60),
                null,
                null));
    }

    private static final class FakeOtpChallengeStatePort implements OtpChallengeStatePort {
        private final Map<String, OtpChallenge> store = new HashMap<>();
        private final List<OtpChallenge> savedChallenges = new ArrayList<>();
        private final List<OtpChallenge> updatedChallenges = new ArrayList<>();
        private OtpChallenge lastSaved;
        private OtpChallenge lastUpdated;

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

        private FakeOtpCodeGenerator(String value) {
            this.value = value;
        }

        @Override
        public String generate(int length, boolean numericOnly) {
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
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(),
                    value.destinationHash(),
                    value.codeHash(), status, value.failedAttemptCount(), value.maxVerifyAttempts(),
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

        private TestOtpChallenge withInvalidatedAt(Instant invalidatedAt) {
            return new TestOtpChallenge(copy(
                    value.otpChallengeId(), value.scenario(), value.workflowRef(), value.channel(),
                    value.destinationHash(),
                    value.codeHash(), value.status(), value.failedAttemptCount(), value.maxVerifyAttempts(),
                    value.resendAttemptCount(),
                    value.maxResendAttempts(), value.createdAt(), value.expiresAt(), value.lastSentAt(), invalidatedAt,
                    value.verifiedAt()));
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