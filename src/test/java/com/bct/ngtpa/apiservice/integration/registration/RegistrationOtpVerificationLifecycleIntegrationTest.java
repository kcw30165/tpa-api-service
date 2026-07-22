package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

/** Executes the real OTP verification orchestration with controlled external ports. */
@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationOtpVerificationLifecycleIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private final ContextLoader context = new ContextLoader();
    private final IdempotencyStore idempotency = new IdempotencyStore();
    private final OtpGateway otp = new OtpGateway();
    private final SessionGateway session = new SessionGateway();
    private final LookupGateway lookup = new LookupGateway();
    private final VerifyRegistrationOtpService service = new VerifyRegistrationOtpService(
            context, new HashGenerator(), idempotency, otp, session, lookup,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void reset() {
        context.reset(); idempotency.reset(); otp.reset(); session.reset(); lookup.reset();
    }

    @Test
    @RegistrationTestCase("TC-044")
    void correctOtpWithOneTrustTransitionsToUsernameCheck() {
        VerifyRegistrationOtpResult result = service.verify(command("idem-one", "123456")).block();
        assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
        assertThat(result.nextStep().name()).isEqualTo("USERNAME_CHECK");
        assertThat(result.unregisteredTrusts()).singleElement().satisfies(trust -> {
            assertThat(trust.accountEnv()).isEqualTo("OI");
            assertThat(trust.trustCode()).isEqualTo("JPM");
        });
        assertThat(session.verifiedTransitions).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("123456", "member@example.com", "3024102");
    }

    @Test
    @RegistrationTestCase("TC-045")
    void correctOtpWithMultipleTrustsTransitionsToTrustSelection() {
        lookup.accounts = List.of(
                new RegistrationLookupAccount("member@example.com", "91234567", "OI", "JPM"),
                new RegistrationLookupAccount("member@example.com", "91234567", "DB", "DBA"));
        VerifyRegistrationOtpResult result = service.verify(command("idem-many", "123456")).block();
        assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
        assertThat(result.nextStep().name()).isEqualTo("SELECT_TRUST");
        assertThat(result.unregisteredTrusts()).hasSize(2);
    }

    @Test
    @RegistrationTestCase("TC-046")
    void noEligibleTrustsReturnsCanonicalBusinessFailure() {
        lookup.status = RegistrationLookupStatus.NOT_ELIGIBLE;
        lookup.accounts = List.of();
        VerifyRegistrationOtpResult result = service.verify(command("idem-none", "123456")).block();
        assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS);
        assertThat(result.errors()).singleElement().satisfies(error ->
                assertThat(error.code()).isEqualTo("err.registration.trustLookup.noEligibleTrusts"));
    }

    @Test
    @RegistrationTestCase("TC-047")
    void wrongOtpReturnsRemainingPerChallengeAttemptsWithoutLookup() {
        otp.result = RegistrationOtpVerificationResult.invalid(4);
        VerifyRegistrationOtpResult result = service.verify(command("idem-wrong", "111111")).block();
        assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_INVALID);
        assertThat(result.remainingVerifyAttempts()).isEqualTo(4);
        assertThat(lookup.calls).isZero();
        assertThat(session.verifiedTransitions).isZero();
    }

    @Test
    @RegistrationTestCase("TC-048")
    void fifthFailureLocksCurrentChallengeAndSixthRemainsLocked() {
        for (int remaining = 4; remaining >= 1; remaining--) {
            otp.result = RegistrationOtpVerificationResult.invalid(remaining);
            VerifyRegistrationOtpResult result = service.verify(
                    command("idem-fail-" + remaining, "111111")).block();
            assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_INVALID);
        }
        otp.result = RegistrationOtpVerificationResult.locked();
        VerifyRegistrationOtpResult fifth = service.verify(command("idem-fifth", "111111")).block();
        VerifyRegistrationOtpResult sixth = service.verify(command("idem-sixth", "111111")).block();
        assertThat(fifth.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_LOCKED);
        assertThat(sixth.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_LOCKED);
        assertThat(fifth.nextStep().name()).isEqualTo("RESTART_FLOW");
        assertThat(sixth.nextStep().name()).isEqualTo("RESTART_FLOW");
        assertThat(fifth.remainingVerifyAttempts()).isZero();
        assertThat(lookup.calls).isZero();
    }

    @Test
    @RegistrationTestCase("TC-049")
    void replacementChallengeHasFreshFiveAttemptAllowance() {
        otp.result = RegistrationOtpVerificationResult.locked();
        VerifyRegistrationOtpResult old = service.verify(command("idem-old", "111111")).block();
        assertThat(old.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_LOCKED);

        context.activeOtpId = "otp-new";
        otp.result = RegistrationOtpVerificationResult.invalid(4);
        VerifyRegistrationOtpResult firstFailureOnReplacement = service.verify(
                command("idem-new", "111111")).block();
        assertThat(otp.lastChallengeId).isEqualTo("otp-new");
        assertThat(firstFailureOnReplacement.remainingVerifyAttempts()).isEqualTo(4);
        assertThat(otp.maxAttemptsForActiveChallenge).isEqualTo(5);
    }

    @Test
    @RegistrationTestCase("TC-050")
    void expiredOtpNeverTransitionsSessionOrLooksUpTrusts() {
        otp.result = RegistrationOtpVerificationResult.expired();
        VerifyRegistrationOtpResult result = service.verify(command("idem-expired", "123456")).block();
        assertThat(result.status()).isEqualTo(VerifyRegistrationOtpResultStatus.OTP_EXPIRED);
        assertThat(session.verifiedTransitions).isZero();
        assertThat(lookup.calls).isZero();
    }

    @Test
    @RegistrationTestCase("TC-051")
    void completedVerifyReplayHasNoDuplicateOtpLookupOrSessionMutation() {
        VerifyRegistrationOtpResult first = service.verify(command("idem-replay", "123456")).block();
        assertThat(first.status()).isEqualTo(VerifyRegistrationOtpResultStatus.VERIFIED);
        int otpCalls = otp.calls;
        int lookupCalls = lookup.calls;
        int transitions = session.verifiedTransitions;

        VerifyRegistrationOtpResult replay = service.verify(command("idem-replay", "123456")).block();
        assertThat(replay.status()).isEqualTo(VerifyRegistrationOtpResultStatus.COMPLETED_REPLAY);
        assertThat(replay.replaySnapshot()).isNotNull();
        assertThat(otp.calls).isEqualTo(otpCalls);
        assertThat(lookup.calls).isEqualTo(lookupCalls);
        assertThat(session.verifiedTransitions).isEqualTo(transitions);
    }

    @Test
    @RegistrationTestCase("TC-052")
    void sessionCsrfAndOriginGuardsStopBeforeOtpVerification() {
        context.result = RegistrationProtectedAuthFlowContextResult.invalid(
                RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION);
        assertThat(service.verify(command("idem-session", "123456")).block().status())
                .isEqualTo(VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
        assertNoVerificationSideEffects();

        reset();
        context.result = RegistrationProtectedAuthFlowContextResult.invalid(
                RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);
        assertThat(service.verify(command("idem-csrf", "123456")).block().status())
                .isEqualTo(VerifyRegistrationOtpResultStatus.CSRF_INVALID);
        assertNoVerificationSideEffects();

        reset();
        context.rejectOrigin = true;
        assertThat(service.verify(command("idem-origin", "123456",
                "https://attacker.invalid", null)).block().status())
                .isEqualTo(VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
        assertNoVerificationSideEffects();
    }

    private VerifyRegistrationOtpCommand command(String key, String code) {
        return command(key, code, "https://portal.test", "https://portal.test/registration");
    }

    private VerifyRegistrationOtpCommand command(String key, String code, String origin, String referer) {
        return new VerifyRegistrationOtpCommand(new AuthFlowSessionId("afs-safe-001"),
                "csrf-secret", code, origin, referer, new RegistrationIdempotencyKey(key),
                "en", "req-003-003", Duration.ofMinutes(5));
    }

    private void assertNoVerificationSideEffects() {
        assertThat(idempotency.starts).isZero();
        assertThat(otp.calls).isZero();
        assertThat(session.verifiedTransitions).isZero();
        assertThat(lookup.calls).isZero();
    }

    static final class ContextLoader implements RegistrationProtectedAuthFlowContextLoader {
        RegistrationProtectedAuthFlowContextResult result;
        String activeOtpId;
        boolean rejectOrigin;
        void reset() {
            activeOtpId = "otp-old"; rejectOrigin = false;
            result = valid();
        }
        private RegistrationProtectedAuthFlowContextResult valid() {
            return RegistrationProtectedAuthFlowContextResult.valid(activeOtpId,
                    "m****r@example.com", new RegistrationAuthFlowContext(
                            RegistrationIdentityType.ID, "3024102", "04/04/1982",
                            RegistrationContactType.EMAIL, "member@example.com"));
        }
        public Mono<RegistrationProtectedAuthFlowContextResult> load(
                RegistrationProtectedAuthFlowContextCommand command) {
            if (rejectOrigin && !"https://portal.test".equals(command.origin())) {
                return Mono.just(RegistrationProtectedAuthFlowContextResult.invalid(
                        RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS));
            }
            if (result != null && result.isValid()) return Mono.just(valid());
            return Mono.just(result);
        }
    }

    static final class HashGenerator implements RegistrationRequestHashGenerator {
        public RegistrationRequestHash hash(RegistrationIdempotencyAction action, List<String> parts) {
            return new RegistrationRequestHash("2".repeat(64));
        }
    }

    static final class IdempotencyStore implements RegistrationIdempotencyPort {
        private final Map<String, RegistrationIdempotencyRecord> records = new HashMap<>();
        int starts;
        void reset() { records.clear(); starts = 0; }
        public Mono<RegistrationIdempotencyStartResult> startProcessing(RegistrationIdempotencyStartCommand command) {
            starts++;
            RegistrationIdempotencyRecord existing = records.get(command.idempotencyKey().value());
            if (existing != null) return Mono.just(new RegistrationIdempotencyStartResult(
                    RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY, existing));
            RegistrationIdempotencyRecord processing = record(command.idempotencyKey(), command.requestHash(),
                    RegistrationIdempotencyStatus.PROCESSING, null);
            records.put(command.idempotencyKey().value(), processing);
            return Mono.just(new RegistrationIdempotencyStartResult(
                    RegistrationIdempotencyStartOutcome.STARTED, processing));
        }
        public Mono<RegistrationIdempotencyRecord> markCompleted(RegistrationIdempotencyCompleteCommand command) {
            RegistrationIdempotencyRecord completed = record(command.idempotencyKey(), command.requestHash(),
                    RegistrationIdempotencyStatus.COMPLETED, command.responseSnapshot());
            records.put(command.idempotencyKey().value(), completed); return Mono.just(completed);
        }
        public Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand command) {
            return Mono.just(records.get(command.idempotencyKey().value()));
        }
        public Mono<Optional<RegistrationIdempotencyRecord>> load(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) {
            return Mono.just(Optional.ofNullable(records.get(key.value())));
        }
        public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) {
            records.remove(key.value()); return Mono.empty();
        }
        private RegistrationIdempotencyRecord record(RegistrationIdempotencyKey key,
                RegistrationRequestHash hash, RegistrationIdempotencyStatus status,
                RegistrationIdempotencyResponseSnapshot snapshot) {
            return new RegistrationIdempotencyRecord(RegistrationIdempotencyAction.OTP_VERIFY,
                    key, hash, status, snapshot, NOW, NOW, NOW.plusSeconds(300));
        }
    }

    static final class OtpGateway implements RegistrationOtpPort {
        RegistrationOtpVerificationResult result;
        int calls;
        String lastChallengeId;
        int maxAttemptsForActiveChallenge;
        void reset() { result = RegistrationOtpVerificationResult.verified(); calls = 0; lastChallengeId = null; maxAttemptsForActiveChallenge = 5; }
        public Mono<RegistrationOtpCreationResult> createOtp(RegistrationContactType type, String destination) { throw new UnsupportedOperationException(); }
        public Mono<RegistrationOtpVerificationResult> verifyOtp(String activeId, String code) {
            calls++; lastChallengeId = activeId; return Mono.just(result);
        }
    }

    static final class SessionGateway implements RegistrationSessionPort {
        int verifiedTransitions;
        void reset() { verifiedTransitions = 0; }
        public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) { throw new UnsupportedOperationException(); }
        public Mono<Void> updateOtpChallenge(String sid, String otpId, RegistrationContactType type, String masked) { return Mono.empty(); }
        public Mono<Void> markOtpVerified(String sid) { verifiedTransitions++; return Mono.empty(); }
    }

    static final class LookupGateway implements RegistrationLookupPort {
        RegistrationLookupStatus status;
        List<RegistrationLookupAccount> accounts;
        int calls;
        void reset() {
            status = RegistrationLookupStatus.ELIGIBLE;
            accounts = List.of(new RegistrationLookupAccount(
                    "member@example.com", "91234567", "OI", "JPM"));
            calls = 0;
        }
        public Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command) {
            calls++; return Mono.just(new RegistrationLookupResult(status, accounts, null));
        }
    }
}
