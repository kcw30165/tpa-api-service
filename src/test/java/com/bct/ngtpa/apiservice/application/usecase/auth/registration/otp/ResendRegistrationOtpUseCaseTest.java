package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ResendRegistrationOtpUseCaseTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final FakeProtectedContextService protectedContext = new FakeProtectedContextService();
    private final RecordingHashGenerator hashGenerator = new RecordingHashGenerator();
    private final FakeIdempotencyPort idempotency = new FakeIdempotencyPort();
    private final FakeOtpGateway otp = new FakeOtpGateway();
    private final FakeSessionGateway session = new FakeSessionGateway();
    private final FakeMessageGateway message = new FakeMessageGateway();
    private final ResendRegistrationOtpUseCase useCase = new ResendRegistrationOtpService(
            protectedContext,
            hashGenerator,
            idempotency,
            otp,
            session,
            message,
            Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void resendOtpHappyPathReplacesOtpUpdatesSessionDeliversMessageAndMarksCompleted() {
        StepVerifier.create(useCase.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.RESENT);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
                    assertThat(result.maskedDestination()).isEqualTo("m****r@example.com");
                    assertThat(result.expiresInSeconds()).isEqualTo(300);
                    assertThat(result.resendCooldownSeconds()).isEqualTo(60);
                    assertThat(result.remainingResendAttempts()).isEqualTo(4);
                    assertThat(result.toString()).doesNotContain("123456", "otp_new", "member@example.com", "afs_secret");
                })
                .verifyComplete();

        assertThat(otp.activeOtpChallengeId).isEqualTo("otp_old");
        assertThat(session.updatedOtpChallengeId).isEqualTo("otp_new");
        assertThat(message.recipient).isEqualTo("member@example.com");
        assertThat(idempotency.completed).isTrue();
        assertThat(idempotency.completedSnapshot.serializedBody())
                .contains("VERIFY_OTP", "m****r@example.com")
                .doesNotContain("otp_new", "otp_old", "123456", "member@example.com", "afs_secret", "csrf_secret");
    }

    @Test
    void invalidAuthFlowDoesNotStartIdempotencyOrCreateOtp() {
        protectedContext.result = RegistrationProtectedAuthFlowContextResult.invalid(
                RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS);

        StepVerifier.create(useCase.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
                    assertThat(result.errors()).singleElement()
                            .satisfies(error -> assertThat(error.code()).isEqualTo("err.registration.authFlow.invalid"));
                })
                .verifyComplete();

        assertThat(idempotency.started).isFalse();
        assertThat(otp.called).isFalse();
        assertThat(message.delivered).isFalse();
    }

    @Test
    void csrfInvalidDoesNotStartIdempotencyOrCreateOtp() {
        protectedContext.result = RegistrationProtectedAuthFlowContextResult.invalid(
                RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);

        StepVerifier.create(useCase.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.CSRF_INVALID);
                    assertThat(result.errors()).singleElement()
                            .satisfies(error -> assertThat(error.code()).isEqualTo("err.registration.csrf.invalid"));
                })
                .verifyComplete();

        assertThat(idempotency.started).isFalse();
        assertThat(otp.called).isFalse();
        assertThat(message.delivered).isFalse();
    }

    @Test
    void duplicateInProgressReturnsWaitWithoutSideEffects() {
        idempotency.outcome = RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;

        StepVerifier.create(useCase.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.WAIT);
                })
                .verifyComplete();

        assertThat(otp.called).isFalse();
        assertThat(session.updatedOtpChallengeId).isNull();
        assertThat(message.delivered).isFalse();
    }

    @Test
    void idempotencyKeyReuseReturnsBlockingErrorWithoutSideEffects() {
        idempotency.outcome = RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;

        StepVerifier.create(useCase.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED);
                    assertThat(result.errors()).singleElement()
                            .satisfies(error -> assertThat(error.code()).isEqualTo("err.workflow.idempotency.keyReused"));
                })
                .verifyComplete();

        assertThat(otp.called).isFalse();
        assertThat(message.delivered).isFalse();
    }

    @Test
    void requestHashIncludesOtpResendActionAndAuthFlowSessionIdOnly() {
        useCase.resend(command()).block();

        assertThat(hashGenerator.action).isEqualTo(RegistrationIdempotencyAction.OTP_RESEND);
        assertThat(hashGenerator.parts).containsExactly("afs_secret");
        assertThat(hashGenerator.parts).doesNotContain("member@example.com", "csrf_secret", "corr", "en");
    }

    @Test
    void messageDeliveryFailureReturnsMessageDeliveryError() {
        message.accepted = false;

        StepVerifier.create(useCase.resend(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED);
                    assertThat(result.errors()).singleElement()
                            .satisfies(error -> assertThat(error.code()).isEqualTo("err.registration.message.deliveryFailed"));
                })
                .verifyComplete();

        assertThat(idempotency.failed).isTrue();
    }

    @Test
        void resendRateLimitedReturnsRestartFlow() {
        otp.error = new RegistrationOtpSendRateLimitedException();

        StepVerifier.create(useCase.resend(command()))
                .assertNext(result -> {
                assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.SEND_RATE_LIMITED);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
                assertThat(result.retryAfterSeconds()).isZero();
                assertThat(result.remainingResendAttempts()).isZero();
                    assertThat(result.errors()).singleElement()
                            .satisfies(error -> assertThat(error.code())
                        .isEqualTo(RegistrationErrorKey.OTP_SEND_RATE_LIMITED.value()));
                })
                .verifyComplete();

        assertThat(idempotency.failed).isTrue();
    }

    private static ResendRegistrationOtpCommand command() {
        return new ResendRegistrationOtpCommand(
                new AuthFlowSessionId("afs_secret"),
                "csrf_secret",
                null,
                null,
                new RegistrationIdempotencyKey("idem_secret"),
                "en",
                "corr",
                Duration.ofMinutes(5));
    }

    private static final class FakeProtectedContextService implements RegistrationProtectedAuthFlowContextLoader {
        RegistrationProtectedAuthFlowContextResult result = RegistrationProtectedAuthFlowContextResult.valid(
                "otp_old",
                "m****r@example.com",
                new RegistrationAuthFlowContext(
                        RegistrationIdentityType.ID,
                        "3024102",
                        "04/04/1982",
                        RegistrationContactType.EMAIL,
                        "member@example.com"));

        @Override
        public Mono<RegistrationProtectedAuthFlowContextResult> load(RegistrationProtectedAuthFlowContextCommand command) {
            return Mono.just(result);
        }
    }

    private static final class RecordingHashGenerator implements RegistrationRequestHashGenerator {
        RegistrationIdempotencyAction action;
        List<String> parts;
        @Override
        public RegistrationRequestHash hash(RegistrationIdempotencyAction action, List<String> normalizedParts) {
            this.action = action;
            this.parts = List.copyOf(normalizedParts);
            return new RegistrationRequestHash(HASH);
        }
    }

    private static final class FakeIdempotencyPort implements RegistrationIdempotencyPort {
        RegistrationIdempotencyStartOutcome outcome = RegistrationIdempotencyStartOutcome.STARTED;
        boolean started;
        boolean completed;
        boolean failed;
        RegistrationIdempotencyResponseSnapshot completedSnapshot;
        @Override
        public Mono<RegistrationIdempotencyStartResult> startProcessing(RegistrationIdempotencyStartCommand command) {
            started = true;
            return Mono.just(new RegistrationIdempotencyStartResult(outcome, processingRecord()));
        }
        @Override
        public Mono<RegistrationIdempotencyRecord> markCompleted(RegistrationIdempotencyCompleteCommand command) {
            completed = true;
            completedSnapshot = command.responseSnapshot();
            return Mono.just(completedRecord(command.responseSnapshot()));
        }
        @Override
        public Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand command) {
            failed = true;
            return Mono.just(processingRecord());
        }
        @Override
        public Mono<Optional<RegistrationIdempotencyRecord>> load(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return Mono.just(Optional.empty()); }
        @Override
        public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return Mono.empty(); }
        private static RegistrationIdempotencyRecord processingRecord() {
            return new RegistrationIdempotencyRecord(RegistrationIdempotencyAction.OTP_RESEND, new RegistrationIdempotencyKey("idem_secret"), new RegistrationRequestHash(HASH), RegistrationIdempotencyStatus.PROCESSING, null, Instant.parse("2026-07-05T00:00:00Z"), Instant.parse("2026-07-05T00:00:00Z"), Instant.parse("2026-07-05T00:05:00Z"));
        }
        private static RegistrationIdempotencyRecord completedRecord(RegistrationIdempotencyResponseSnapshot snapshot) {
            return new RegistrationIdempotencyRecord(RegistrationIdempotencyAction.OTP_RESEND, new RegistrationIdempotencyKey("idem_secret"), new RegistrationRequestHash(HASH), RegistrationIdempotencyStatus.COMPLETED, snapshot, Instant.parse("2026-07-05T00:00:00Z"), Instant.parse("2026-07-05T00:00:00Z"), Instant.parse("2026-07-05T00:05:00Z"));
        }
    }

    private static final class FakeOtpGateway implements RegistrationOtpPort {
        boolean called;
        String activeOtpChallengeId;
        RuntimeException error;
        @Override public Mono<RegistrationOtpCreationResult> createOtp(RegistrationContactType type, String destination) { throw new UnsupportedOperationException(); }
        @Override public Mono<RegistrationOtpCreationResult> resendOtp(String activeOtpChallengeId, RegistrationContactType type, String destination) {
            called = true;
            this.activeOtpChallengeId = activeOtpChallengeId;
            if (error != null) {
                return Mono.error(error);
            }
            return Mono.just(new RegistrationOtpCreationResult("otp_new", "123456", 300, 60, 4));
        }
    }

    private static final class FakeSessionGateway implements RegistrationSessionPort {
        String updatedOtpChallengeId;
        @Override public Mono<String> createRegistrationSession(String maskedDestination, RegistrationContactType contactType) { return Mono.just("afs_secret"); }
        @Override public Mono<Void> updateOtpChallenge(String sid, String otpId, RegistrationContactType type, String maskedDestination) {
            updatedOtpChallengeId = otpId;
            return Mono.empty();
        }
    }

    private static final class FakeMessageGateway implements RegistrationMessagePort {
        boolean accepted = true;
        boolean delivered;
        String recipient;
        @Override public Mono<Boolean> deliverRegistrationOtp(RegistrationContactType type, String recipient, String otpCode, int ttl, String lang, String corr) {
            delivered = true;
            this.recipient = recipient;
            return Mono.just(accepted);
        }
    }
}
