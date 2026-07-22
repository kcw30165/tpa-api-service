package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationOtpChallengeAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.*;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendRejectedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpSendRateLimitedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Core OTP challenge integration. HTTP/service guards are added in 003-002. */
@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationOtpChallengeLifecycleIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private final StatefulChallengePort challenges = new StatefulChallengePort();
    private final RegistrationOtpChallengeAdapter adapter = new RegistrationOtpChallengeAdapter(
            challenges, new Policy(), Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void reset() { challenges.reset(); }

    @Test
    @RegistrationTestCase({"TC-033", "TC-037"})
    void firstResendReplacesInitialChallengeAndLeavesFourAttempts() {
        RegistrationOtpCreationResult initial = adapter.createOtp(
                RegistrationContactType.EMAIL, "member@example.com").block();
        assertThat(initial.remainingResendAttempts()).isEqualTo(5);
        assertThat(challenges.deliveryCount).isEqualTo(1);

        RegistrationOtpCreationResult resend = adapter.resendOtp(
                initial.activeOtpChallengeId(), RegistrationContactType.EMAIL,
                "member@example.com").block();

        assertThat(resend.activeOtpChallengeId()).isNotEqualTo(initial.activeOtpChallengeId());
        assertThat(resend.remainingResendAttempts()).isEqualTo(4);
        assertThat(challenges.deliveryCount).isEqualTo(2);
        assertThat(challenges.previousInvalidated).isTrue();
    }

    @Test
    @RegistrationTestCase("TC-034")
    void fiveResendsProduceSixTotalOtpDeliveries() {
        RegistrationOtpCreationResult current = adapter.createOtp(
                RegistrationContactType.EMAIL, "member@example.com").block();
        int[] remaining = {4, 3, 2, 1, 0};
        for (int expected : remaining) {
            current = adapter.resendOtp(current.activeOtpChallengeId(),
                    RegistrationContactType.EMAIL, "member@example.com").block();
            assertThat(current.remainingResendAttempts()).isEqualTo(expected);
        }
        assertThat(challenges.deliveryCount).isEqualTo(6);
        assertThat(challenges.successfulResends).isEqualTo(5);
    }

    @Test
    @RegistrationTestCase("TC-035")
        void sixthResendIsSendRateLimitedWithoutSeventhDelivery() {
        RegistrationOtpCreationResult current = adapter.createOtp(
                RegistrationContactType.EMAIL, "member@example.com").block();
        for (int i = 0; i < 5; i++) {
            current = adapter.resendOtp(current.activeOtpChallengeId(),
                    RegistrationContactType.EMAIL, "member@example.com").block();
        }
        String active = current.activeOtpChallengeId();
        StepVerifier.create(adapter.resendOtp(active, RegistrationContactType.EMAIL,
                        "member@example.com"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RegistrationOtpSendRateLimitedException.class);
                    assertThat(((RegistrationOtpSendRateLimitedException) error).retryAfterSeconds())
                            .isZero();
                }).verify();
        assertThat(challenges.deliveryCount).isEqualTo(6);
    }

    @Test
    @RegistrationTestCase("TC-036")
    void resendBeforeCooldownIsRejectedWithoutReplacement() {
        RegistrationOtpCreationResult initial = adapter.createOtp(
                RegistrationContactType.EMAIL, "member@example.com").block();
        challenges.cooldownActive = true;
        StepVerifier.create(adapter.resendOtp(initial.activeOtpChallengeId(),
                        RegistrationContactType.EMAIL, "member@example.com"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RegistrationOtpResendRejectedException.class);
                    var rejected = (RegistrationOtpResendRejectedException) error;
                    assertThat(rejected.status()).isEqualTo(ResendRegistrationOtpResultStatus.COOLDOWN_ACTIVE);
                    assertThat(rejected.retryAfterSeconds()).isEqualTo(60);
                }).verify();
        assertThat(challenges.deliveryCount).isEqualTo(1);
    }

    @Test
    @RegistrationTestCase("TC-042")
    void nonReplaceableCurrentChallengeUsesCanonicalInvalidFlowOutcome() {
        RegistrationOtpCreationResult initial = adapter.createOtp(
                RegistrationContactType.EMAIL, "member@example.com").block();
        challenges.forcedOutcome = OtpReplacementOutcome.ALREADY_VERIFIED;
        StepVerifier.create(adapter.resendOtp(initial.activeOtpChallengeId(),
                        RegistrationContactType.EMAIL, "member@example.com"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RegistrationOtpResendRejectedException.class);
                    assertThat(((RegistrationOtpResendRejectedException) error).status())
                            .isEqualTo(ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
                }).verify();
        assertThat(challenges.deliveryCount).isEqualTo(1);
    }

    static final class Policy implements AuthWorkflowPolicyPort {
        public RegistrationAuthWorkflowPolicy registrationPolicy() {
            return new RegistrationAuthWorkflowPolicy(null, null,
                    new OtpPolicy(6, true, 300, 60, 5, 5, true, true,
                            "OTP {code}", new OtpEmailPolicy("OTP", "OTP {code}")),
                    null, null);
        }
        public Optional<String> resolveIdentityTypeSearchScope(String identityType) { return Optional.empty(); }
    }

    static final class StatefulChallengePort implements OtpChallengePort {
        int deliveryCount;
        int successfulResends;
        int resendAttemptsRemaining;
        int sequence;
        String activeId;
        boolean previousInvalidated;
        boolean cooldownActive;
        OtpReplacementOutcome forcedOutcome;

        void reset() {
            deliveryCount = 0; successfulResends = 0; resendAttemptsRemaining = 5;
            sequence = 0; activeId = null; previousInvalidated = false;
            cooldownActive = false; forcedOutcome = null;
        }

        public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand command) {
            activeId = "otp-" + (++sequence);
            deliveryCount++;
            return Mono.just(new CreateOtpChallengeResult(activeId, "123456",
                    command.now().plusSeconds(command.ttlSeconds()), command.ttlSeconds()));
        }

        public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand command) {
            OtpReplacementOutcome outcome = forcedOutcome;
            Integer retry = null;
            if (outcome == null && cooldownActive) { outcome = OtpReplacementOutcome.COOLDOWN_ACTIVE; retry = 60; }
            if (outcome == null && resendAttemptsRemaining == 0) outcome = OtpReplacementOutcome.MAX_RESEND_EXCEEDED;
            if (outcome == null) outcome = OtpReplacementOutcome.REPLACED;

            if (outcome != OtpReplacementOutcome.REPLACED) {
                return Mono.just(new ReplaceOtpChallengeResult(command.currentOtpChallengeId(), null, null,
                        outcome, null, 0, 60, resendAttemptsRemaining, retry));
            }

            previousInvalidated = true;
            successfulResends++;
            resendAttemptsRemaining--;
            String previous = activeId;
            activeId = "otp-" + (++sequence);
            deliveryCount++;
            return Mono.just(new ReplaceOtpChallengeResult(previous, activeId, "654321",
                    OtpReplacementOutcome.REPLACED, command.now().plusSeconds(command.ttlSeconds()),
                    command.ttlSeconds(), command.resendCooldownSeconds(), resendAttemptsRemaining, null));
        }

        public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand command) {
            return Mono.just(new VerifyOtpChallengeResult(command.otpChallengeId(),
                    OtpVerificationOutcome.VALID, 5, null));
        }
        public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand command) { return Mono.empty(); }
    }
}
