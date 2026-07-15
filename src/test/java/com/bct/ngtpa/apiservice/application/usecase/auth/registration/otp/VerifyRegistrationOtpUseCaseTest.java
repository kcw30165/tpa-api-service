package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
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

class VerifyRegistrationOtpUseCaseTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final FakeProtectedContextService protectedContext = new FakeProtectedContextService();
    private final RecordingHashGenerator hashGenerator = new RecordingHashGenerator();
    private final FakeIdempotencyPort idempotency = new FakeIdempotencyPort();
    private final FakeOtpGateway otp = new FakeOtpGateway();
    private final FakeSessionGateway session = new FakeSessionGateway();
    private final FakeRegistrationLookupPort lookup = new FakeRegistrationLookupPort();
    private final VerifyRegistrationOtpUseCase useCase = new VerifyRegistrationOtpService(
            protectedContext, hashGenerator, idempotency, otp, session, lookup,
            Clock.fixed(Instant.parse("2026-07-05T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void verifyOtpHappyPathSingleTrustReturnsUsernameCheck() {
        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
                    assertThat(result.unregisteredTrusts()).hasSize(1);
                    assertThat(result.toString()).doesNotContain("123456", "otp_old", "afs_secret", "member@example.com", "3024102");
                })
                .verifyComplete();

        assertThat(otp.called).isTrue();
        assertThat(otp.activeOtpChallengeId).isEqualTo("otp_old");
        assertThat(session.markedOtpVerified).isTrue();
        assertThat(lookup.called).isTrue();
        assertThat(idempotency.completed).isTrue();
        assertThat(idempotency.completedSnapshot.serializedBody()).doesNotContain("123456", "otp_old", "afs_secret", "member@example.com", "3024102", "04/04/1982");
    }

    @Test
    void verifyOtpHappyPathMultipleTrustsReturnsSelectTrust() {
        lookup.accounts = List.of(
                new RegistrationLookupAccount("DB", "DBA", null, null),
                new RegistrationLookupAccount("DB", "DBB", null, null));

        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.SELECT_TRUST);
                    assertThat(result.unregisteredTrusts()).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void verifyOtpUsesDecryptedContextForRegistrationLookup() {
        useCase.verify(command("123456")).block();
        assertThat(lookup.command.identityType()).isEqualTo(RegistrationIdentityType.ID);
        assertThat(lookup.command.identityValue()).isEqualTo("3024102");
        assertThat(lookup.command.dateOfBirth()).isEqualTo("04/04/1982");
        assertThat(lookup.command.contactType()).isEqualTo(RegistrationContactType.EMAIL);
        assertThat(lookup.command.contactValue()).isEqualTo("member@example.com");
    }

    @Test
    void blankOtpCodeReturnsValidationErrorWithoutVerifyingOtp() {
        StepVerifier.create(useCase.verify(command(" ")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VALIDATION_FAILED);
                    assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("err.registration.otp.invalid"));
                })
                .verifyComplete();
        assertThat(otp.called).isFalse();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void invalidAuthFlowDoesNotStartIdempotencyOrVerifyOtp() {
        protectedContext.result = RegistrationProtectedAuthFlowContextResult.invalid(RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS);
        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW))
                .verifyComplete();
        assertThat(idempotency.started).isFalse();
        assertThat(otp.called).isFalse();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void csrfInvalidDoesNotStartIdempotencyOrVerifyOtp() {
        protectedContext.result = RegistrationProtectedAuthFlowContextResult.invalid(RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);
        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.CSRF_INVALID))
                .verifyComplete();
        assertThat(idempotency.started).isFalse();
        assertThat(otp.called).isFalse();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void duplicateInProgressReturnsWaitWithoutSideEffects() {
        idempotency.outcome = RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS;
        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS);
                    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.WAIT);
                })
                .verifyComplete();
        assertThat(otp.called).isFalse();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void idempotencyKeyReuseReturnsBlockingErrorWithoutSideEffects() {
        idempotency.outcome = RegistrationIdempotencyStartOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST;
        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED);
                    assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("err.workflow.idempotency.keyReused"));
                })
                .verifyComplete();
        assertThat(otp.called).isFalse();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void requestHashIncludesOtpVerifyActionSessionAndSafeOtpHash() {
        useCase.verify(command("123456")).block();
        assertThat(hashGenerator.action).isEqualTo(RegistrationIdempotencyAction.OTP_VERIFY);
        assertThat(hashGenerator.parts).hasSize(2);
        assertThat(hashGenerator.parts).contains("afs_secret");
        assertThat(hashGenerator.parts).doesNotContain("123456", "member@example.com", "en", "corr");
    }

    @Test
    void otpInvalidReturnsInvalidOtpErrorAndRemainingAttempts() {
        otp.result = RegistrationOtpVerificationResult.invalid(3);
        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_INVALID);
                    assertThat(result.remainingVerifyAttempts()).isEqualTo(3);
                    assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("err.registration.otp.invalid"));
                })
                .verifyComplete();
        assertThat(lookup.called).isFalse();
    }

    @Test
    void noEligibleTrustsReturnsNoEligibleTrustsError() {
        lookup.accounts = List.of();
        StepVerifier.create(useCase.verify(command("123456")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS);
                    assertThat(result.errors()).singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("err.registration.trustLookup.noEligibleTrusts"));
                })
                .verifyComplete();
    }

    private static VerifyRegistrationOtpCommand command(String otpCode) {
        return new VerifyRegistrationOtpCommand(new AuthFlowSessionId("afs_secret"), "csrf_secret", otpCode, null, null, new RegistrationIdempotencyKey("idem_secret"), "en", "corr", Duration.ofMinutes(5));
    }

    private static final class FakeProtectedContextService implements RegistrationProtectedAuthFlowContextLoader {
        RegistrationProtectedAuthFlowContextResult result = RegistrationProtectedAuthFlowContextResult.valid(
                "otp_old", "m****r@example.com", new RegistrationAuthFlowContext(RegistrationIdentityType.ID, "3024102", "04/04/1982", RegistrationContactType.EMAIL, "member@example.com"));
        @Override public Mono<RegistrationProtectedAuthFlowContextResult> load(RegistrationProtectedAuthFlowContextCommand command) { return Mono.just(result); }
    }

    private static final class RecordingHashGenerator implements RegistrationRequestHashGenerator {
        RegistrationIdempotencyAction action; List<String> parts;
        @Override public RegistrationRequestHash hash(RegistrationIdempotencyAction action, List<String> normalizedParts) { this.action = action; this.parts = List.copyOf(normalizedParts); return new RegistrationRequestHash(HASH); }
    }

    private static final class FakeIdempotencyPort implements RegistrationIdempotencyPort {
        RegistrationIdempotencyStartOutcome outcome = RegistrationIdempotencyStartOutcome.STARTED; boolean started; boolean completed; RegistrationIdempotencyResponseSnapshot completedSnapshot;
        @Override public Mono<RegistrationIdempotencyStartResult> startProcessing(RegistrationIdempotencyStartCommand command) { started = true; return Mono.just(new RegistrationIdempotencyStartResult(outcome, record(null, RegistrationIdempotencyStatus.PROCESSING))); }
        @Override public Mono<RegistrationIdempotencyRecord> markCompleted(RegistrationIdempotencyCompleteCommand command) { completed = true; completedSnapshot = command.responseSnapshot(); return Mono.just(record(command.responseSnapshot(), RegistrationIdempotencyStatus.COMPLETED)); }
        @Override public Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand command) { return Mono.just(record(null, RegistrationIdempotencyStatus.FAILED)); }
        @Override public Mono<Optional<RegistrationIdempotencyRecord>> load(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return Mono.just(Optional.empty()); }
        @Override public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return Mono.empty(); }
        private static RegistrationIdempotencyRecord record(RegistrationIdempotencyResponseSnapshot s, RegistrationIdempotencyStatus st) { return new RegistrationIdempotencyRecord(RegistrationIdempotencyAction.OTP_VERIFY, new RegistrationIdempotencyKey("idem_secret"), new RegistrationRequestHash(HASH), st, s, Instant.parse("2026-07-05T00:00:00Z"), Instant.parse("2026-07-05T00:00:00Z"), Instant.parse("2026-07-05T00:05:00Z")); }
    }

    private static final class FakeOtpGateway implements RegistrationOtpPort {
        boolean called; String activeOtpChallengeId; RegistrationOtpVerificationResult result = RegistrationOtpVerificationResult.verified();
        @Override public Mono<RegistrationOtpCreationResult> createOtp(RegistrationContactType type, String destination) { throw new UnsupportedOperationException(); }
        @Override public Mono<RegistrationOtpVerificationResult> verifyOtp(String activeOtpChallengeId, String submittedOtpCode) { called = true; this.activeOtpChallengeId = activeOtpChallengeId; return Mono.just(result); }
    }

    private static final class FakeSessionGateway implements RegistrationSessionPort {
        boolean markedOtpVerified;
        @Override public Mono<String> createRegistrationSession(String maskedDestination, RegistrationContactType contactType) { return Mono.just("afs_secret"); }
        @Override public Mono<Void> updateOtpChallenge(String sid, String otpId, RegistrationContactType type, String maskedDestination) { return Mono.empty(); }
        @Override public Mono<Void> markOtpVerified(String sid) { markedOtpVerified = true; return Mono.empty(); }
    }

    private static final class FakeRegistrationLookupPort implements RegistrationLookupPort {
        boolean called; RegistrationLookupCommand command; List<RegistrationLookupAccount> accounts = List.of(new RegistrationLookupAccount("DB", "DBA", null, null));
        @Override public Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command) { called = true; this.command = command; return Mono.just(new RegistrationLookupResult(RegistrationLookupStatus.ELIGIBLE, accounts, null)); }
    }
}
