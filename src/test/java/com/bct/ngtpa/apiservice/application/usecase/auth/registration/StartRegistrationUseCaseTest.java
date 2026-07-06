package com.bct.ngtpa.apiservice.application.usecase.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import org.mockito.Mockito;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class StartRegistrationUseCaseTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final FakeIdempotencyPort idempotency = new FakeIdempotencyPort();
    private final RecordingHashGenerator hashGenerator = new RecordingHashGenerator();
    private final FakeLookupPort lookup = new FakeLookupPort();
    private final FakeSessionGateway session = new FakeSessionGateway();
    private final FakeCsrfGateway csrf = new FakeCsrfGateway();
    private final FakeOtpGateway otp = new FakeOtpGateway();
    private final FakeMessageGateway message = new FakeMessageGateway();
    private final AuthFlowSessionPort authFlowSessionPort = Mockito.mock(AuthFlowSessionPort.class);
    private final OtpChallengeStatePort otpChallengeStatePort = Mockito.mock(OtpChallengeStatePort.class);
    private final StartRegistrationService useCase = new StartRegistrationService(
            new RegistrationStartValidator(), hashGenerator, idempotency, lookup, session, csrf, otp, message,
            authFlowSessionPort,
            otpChallengeStatePort,
            Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void startRegistrationValidRequestCompletesHappyPath() {
        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
                    assertThat(result.maskedDestination()).isEqualTo("m****r@example.com");
                    assertThat(result.csrfToken()).isEqualTo("csrf_token_safe");
                    assertThat(result.authFlowSessionId()).isEqualTo("afs_session_for_cookie");
                    assertThat(result.errors()).isEmpty();
                })
                .verifyComplete();
        assertThat(idempotency.completed).isTrue();
        assertThat(session.createdAfterLookup).isTrue();
        assertThat(session.updatedOtpChallengeId).isEqualTo("otp_challenge_internal");
        assertThat(otp.destination).isEqualTo("member@example.com");
        assertThat(message.recipient).isEqualTo("member@example.com");
    }

    @Test
    void validationFailureDoesNotCallApimOrCreateSession() {
        var invalid = new StartRegistrationCommand(new RegistrationIdempotencyKey("idem_abc123"), "BAD", "x", "31/02/1980", null, null, null, null, "en", "corr", Duration.ofMinutes(5));

        StepVerifier.create(useCase.start(invalid))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.VALIDATION_FAILED);
                    assertThat(result.errors()).isNotEmpty();
                })
                .verifyComplete();
        assertThat(lookup.called).isFalse();
        assertThat(session.createdAfterLookup).isFalse();
        assertThat(otp.created).isFalse();
        assertThat(message.delivered).isFalse();
    }

    @Test
    void idempotencyDuplicateInProgressReturnsWaitWithoutSideEffects() {
        idempotency.outcome = RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.WAIT);
                })
                .verifyComplete();
        assertThat(lookup.called).isFalse();
        assertThat(session.createdAfterLookup).isFalse();
    }

    @Test
    void idempotencyCompletedReplayReturnsReplayResultWithoutSideEffects() {
        idempotency.outcome = RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY;
        idempotency.record = idempotency.completedRecord();

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.COMPLETED_REPLAY);
                    assertThat(result.replaySnapshot()).isNotNull();
                })
                .verifyComplete();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void idempotencyKeyReuseReturnsBlockingErrorWithoutSideEffects() {
        idempotency.outcome = RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED);
                    assertThat(result.errors()).anySatisfy(error -> assertThat(error.errorKey()).isEqualTo(RegistrationWorkflowErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED));
                })
                .verifyComplete();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void lookupNotFoundMapsToVerificationFailure() {
        lookup.result = new RegistrationLookupResult(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE, List.of(), "err.tpa.member.not.found");

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.VERIFICATION_FAILED);
                    assertThat(result.errors()).anySatisfy(error -> assertThat(error.errorKey()).isEqualTo(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED));
                    assertThat(result.toString()).doesNotContain("00000008802", "apim@example.com", "49998888");
                })
                .verifyComplete();
        assertThat(session.createdAfterLookup).isFalse();
        assertThat(idempotency.failed).isTrue();
    }

    @Test
    void lookupBusinessRejectedMapsToVerificationFailedError() {
        lookup.result = new RegistrationLookupResult(RegistrationLookupStatus.UPSTREAM_BUSINESS_REJECTED, List.of(), "err.tpa.other");

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> assertThat(result.errors())
                        .anySatisfy(error -> assertThat(error.errorKey()).isEqualTo(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED)))
                .verifyComplete();
        assertThat(session.createdAfterLookup).isFalse();
    }

    @Test
    void messageDeliveryFailureReturnsMessageDeliveryError() {
        message.accepted = false;

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED);
                    assertThat(result.errors()).anySatisfy(error -> assertThat(error.errorKey()).isEqualTo(RegistrationWorkflowErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED));
                })
                .verifyComplete();
        assertThat(idempotency.failed).isTrue();
    }

    @Test
    void requestHashIncludesRegistrationStartActionAndNormalizedPayload() {
        useCase.start(command()).block();

        assertThat(hashGenerator.action).isEqualTo(RegistrationIdempotencyAction.REGISTRATION_START);
        assertThat(hashGenerator.parts).containsExactly("GPFS", "1234567890", "31/01/1980", "email", "member@example.com", "cap_secret");
        assertThat(hashGenerator.parts).doesNotContain("corr-secret", "en");
    }


    @Test
    void memberNotFoundErrMessageReturnsVerificationFailedCodeWithoutApplicationMessage() {
        lookup.result = new RegistrationLookupResult(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE, List.of(), "err.tpa.member.not.found");

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.VERIFICATION_FAILED);
                    assertThat(result.errors()).anySatisfy(error -> {
                        assertThat(error.errorKey()).isEqualTo(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED);
                        assertThat(error.message()).isNull();
                    });
                })
                .verifyComplete();
    }

    @Test
    void memberNotFoundErrMessageReturnsVerificationFailedCodeWithoutApplicationMessageForChinese() {
        lookup.result = new RegistrationLookupResult(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE, List.of(), "err.tpa.member.not.found");
        var base = command();
        var zhCommand = new StartRegistrationCommand(base.idempotencyKey(), base.identityType(), base.identityValue(), base.dateOfBirth(), base.email(), base.mobile(), base.captchaChallengeId(), base.authFlowSessionId(), "zh-HK", base.correlationId(), base.idempotencyTtl());

        StepVerifier.create(useCase.start(zhCommand))
                .assertNext(result -> assertThat(result.errors())
                        .anySatisfy(error -> assertThat(error.message()).isNull()))
                .verifyComplete();
    }

    @Test
    void otherApimBusinessErrorReturnsVerificationFailedCodeWithoutLocalizedMemberNotFoundText() {
        lookup.result = new RegistrationLookupResult(RegistrationLookupStatus.UPSTREAM_BUSINESS_REJECTED, List.of(), "err.tpa.other");

        StepVerifier.create(useCase.start(command()))
                .assertNext(result -> assertThat(result.errors())
                        .anySatisfy(error -> {
                            assertThat(error.errorKey()).isEqualTo(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED);
                            assertThat(error.message()).isNull();
                        }))
                .verifyComplete();
    }

    private static StartRegistrationCommand command() {
        return new StartRegistrationCommand(new RegistrationIdempotencyKey("idem_abc123"), "GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap_secret", null, "en", "corr-secret", Duration.ofMinutes(5));
    }

    private static final class RecordingHashGenerator implements RegistrationRequestHashGenerator {
        RegistrationIdempotencyAction action;
        List<String> parts;
        @Override public RegistrationRequestHash hash(RegistrationIdempotencyAction action, List<String> normalizedParts) {
            this.action = action; this.parts = List.copyOf(normalizedParts); return new RegistrationRequestHash(HASH);
        }
    }

    private static final class FakeIdempotencyPort implements RegistrationIdempotencyPort {
        RegistrationIdempotencyStartOutcome outcome = RegistrationIdempotencyStartOutcome.STARTED;
        RegistrationIdempotencyRecord record = processingRecord();
        boolean completed;
        boolean failed;
        @Override public Mono<RegistrationIdempotencyStartResult> startProcessing(RegistrationIdempotencyStartCommand command) { return Mono.just(new RegistrationIdempotencyStartResult(outcome, record)); }
        @Override public Mono<RegistrationIdempotencyRecord> markCompleted(RegistrationIdempotencyCompleteCommand command) { completed = true; assertThat(command.responseSnapshot().safeHeaders()).doesNotContainKeys("Set-Cookie", "Cookie", "Authorization"); return Mono.just(completedRecord()); }
        @Override public Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand command) { failed = true; return Mono.just(processingRecord()); }
        @Override public Mono<Optional<RegistrationIdempotencyRecord>> load(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return Mono.just(Optional.empty()); }
        @Override public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return Mono.empty(); }
        private static RegistrationIdempotencyRecord processingRecord() { return new RegistrationIdempotencyRecord(RegistrationIdempotencyAction.REGISTRATION_START, new RegistrationIdempotencyKey("idem_abc123"), new RegistrationRequestHash(HASH), RegistrationIdempotencyStatus.PROCESSING, null, Instant.parse("2026-07-03T00:00:00Z"), Instant.parse("2026-07-03T00:00:00Z"), Instant.parse("2026-07-03T00:05:00Z")); }
        private RegistrationIdempotencyRecord completedRecord() { return new RegistrationIdempotencyRecord(RegistrationIdempotencyAction.REGISTRATION_START, new RegistrationIdempotencyKey("idem_abc123"), new RegistrationRequestHash(HASH), RegistrationIdempotencyStatus.COMPLETED, new RegistrationIdempotencyResponseSnapshot("200", "{\"status\":\"STARTED\"}", Map.of()), Instant.parse("2026-07-03T00:00:00Z"), Instant.parse("2026-07-03T00:00:00Z"), Instant.parse("2026-07-03T00:05:00Z")); }
    }

    private static final class FakeLookupPort implements RegistrationLookupPort {
        boolean called;
        RegistrationLookupResult result = new RegistrationLookupResult(RegistrationLookupStatus.ELIGIBLE, List.of(new RegistrationLookupAccount("apim@example.com", "49998888", "00000008802", "2", "OI", "JPM")), null);
        @Override public Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command) { called = true; return Mono.just(result); }
    }
    private static final class FakeSessionGateway implements RegistrationSessionGateway {
        boolean createdAfterLookup; String updatedOtpChallengeId;
        @Override public Mono<String> createRegistrationSession(String maskedDestination, RegistrationContactType contactType) { createdAfterLookup = true; return Mono.just("afs_session_for_cookie"); }
        @Override public Mono<Void> updateOtpChallenge(String sid, String otpId, RegistrationContactType type, String maskedDestination) { updatedOtpChallengeId = otpId; return Mono.empty(); }
    }
    private static final class FakeCsrfGateway implements RegistrationCsrfGateway { @Override public Mono<String> bindCsrfToken(String sid) { return Mono.just("csrf_token_safe"); } }
    private static final class FakeOtpGateway implements RegistrationOtpGateway { boolean created; String destination; @Override public Mono<RegistrationOtpCreationResult> createOtp(RegistrationContactType type, String dest) { created = true; destination = dest; return Mono.just(new RegistrationOtpCreationResult("otp_challenge_internal", "123456", 300, 60, 3)); } }
    private static final class FakeMessageGateway implements RegistrationMessageGateway { boolean accepted = true; boolean delivered; String recipient; @Override public Mono<Boolean> deliverRegistrationOtp(RegistrationContactType type, String recipient, String otpCode, int ttl, String lang, String corr) { delivered = true; this.recipient = recipient; return Mono.just(accepted); } }
}
