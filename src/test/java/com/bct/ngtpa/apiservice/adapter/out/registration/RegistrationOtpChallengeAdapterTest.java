package com.bct.ngtpa.apiservice.adapter.out.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.OtpPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpReplacementOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpVerificationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpResendRejectedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpSendRateLimitedException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.RegistrationOtpVerificationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link RegistrationOtpChallengeAdapter}. */
class RegistrationOtpChallengeAdapterTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final OtpChallengePort otp = mock(OtpChallengePort.class);
  private final AuthWorkflowPolicyPort policyPort = mock(AuthWorkflowPolicyPort.class);

  private RegistrationOtpChallengeAdapter newAdapter() {
    return new RegistrationOtpChallengeAdapter(otp, policyPort, clock);
  }

  // ----- constructor guards ----------------------------------------------------

  @Test
  void constructorRejectsNullOtpChallengePort() {
    assertThatThrownBy(() -> new RegistrationOtpChallengeAdapter(null, policyPort, clock))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNullAuthWorkflowPolicyPort() {
    assertThatThrownBy(() -> new RegistrationOtpChallengeAdapter(otp, null, clock))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRejectsNullClock() {
    assertThatThrownBy(() -> new RegistrationOtpChallengeAdapter(otp, policyPort, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- otpPolicy() guard, shared by all 3 public methods -------------------------

  @Test
  void createOtpWithNullRegistrationPolicyThrows() {
    when(policyPort.registrationPolicy()).thenReturn(null);
    assertThatThrownBy(
            () -> newAdapter().createOtp(RegistrationContactType.EMAIL, "user@example.com").block())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void createOtpWithNullOtpPolicyThrows() {
    RegistrationAuthWorkflowPolicy policy = mock(RegistrationAuthWorkflowPolicy.class);
    when(policy.otp()).thenReturn(null);
    when(policyPort.registrationPolicy()).thenReturn(policy);
    assertThatThrownBy(
            () -> newAdapter().createOtp(RegistrationContactType.EMAIL, "user@example.com").block())
        .isInstanceOf(IllegalStateException.class);
  }

  private void givenPolicy() {
    OtpPolicy otpPolicy = mock(OtpPolicy.class);
    when(otpPolicy.length()).thenReturn(6);
    when(otpPolicy.numericOnly()).thenReturn(true);
    when(otpPolicy.ttlSeconds()).thenReturn(300);
    when(otpPolicy.maxVerifyAttempts()).thenReturn(5);
    when(otpPolicy.maxResendAttempts()).thenReturn(3);
    when(otpPolicy.resendCooldownSeconds()).thenReturn(60);
    when(otpPolicy.resendLimitEnabled()).thenReturn(true);
    RegistrationAuthWorkflowPolicy policy = mock(RegistrationAuthWorkflowPolicy.class);
    when(policy.otp()).thenReturn(otpPolicy);
    when(policyPort.registrationPolicy()).thenReturn(policy);
  }

  // ----- createOtp(): channel routing + success ---------------------------------------

  @Test
  void createOtpMobileContactTypeSucceeds() {
    givenPolicy();
    when(otp.createChallenge(any()))
        .thenReturn(
            Mono.just(
                new CreateOtpChallengeResult(
                    "challenge-1", "raw-code", NOW.plusSeconds(300), 300)));
    StepVerifier.create(newAdapter().createOtp(RegistrationContactType.MOBILE, "91234567"))
        .assertNext(result -> assertThat(result.activeOtpChallengeId()).isEqualTo("challenge-1"))
        .verifyComplete();
  }

  @Test
  void createOtpEmailContactTypeSucceeds() {
    givenPolicy();
    when(otp.createChallenge(any()))
        .thenReturn(
            Mono.just(
                new CreateOtpChallengeResult(
                    "challenge-1", "raw-code", NOW.plusSeconds(300), 300)));
    StepVerifier.create(newAdapter().createOtp(RegistrationContactType.EMAIL, "user@example.com"))
        .assertNext(result -> assertThat(result.activeOtpChallengeId()).isEqualTo("challenge-1"))
        .verifyComplete();
  }

  @Test
  void createOtpWithNullDestinationSucceeds() {
    givenPolicy();
    when(otp.createChallenge(any()))
        .thenReturn(
            Mono.just(
                new CreateOtpChallengeResult(
                    "challenge-1", "raw-code", NOW.plusSeconds(300), 300)));
    StepVerifier.create(newAdapter().createOtp(RegistrationContactType.EMAIL, null))
        .assertNext(result -> assertThat(result.activeOtpChallengeId()).isEqualTo("challenge-1"))
        .verifyComplete();
  }

  // ----- resendOtp(): each OtpReplacementOutcome switch bucket ----------------------

  private ReplaceOtpChallengeResult replaced() {
    return new ReplaceOtpChallengeResult(
        "old-1",
        "new-1",
        "raw-code",
        OtpReplacementOutcome.REPLACED,
        NOW.plusSeconds(300),
        300,
        60,
        2,
        null);
  }

  @Test
  void resendOtpReplacedSucceeds() {
    givenPolicy();
    when(otp.replaceChallenge(any())).thenReturn(Mono.just(replaced()));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .assertNext(result -> assertThat(result.activeOtpChallengeId()).isEqualTo("new-1"))
        .verifyComplete();
  }

  private ReplaceOtpChallengeResult withOutcome(OtpReplacementOutcome outcome) {
    return new ReplaceOtpChallengeResult("old-1", null, null, outcome, null, 0, 60, 2, 30);
  }

  @Test
  void resendOtpCooldownActiveErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.COOLDOWN_ACTIVE)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpResendRejectedException.class)
        .verify();
  }

  @Test
  void resendOtpMaxResendExceededErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.MAX_RESEND_EXCEEDED)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpSendRateLimitedException.class)
        .verify();
  }

  @Test
  void resendOtpLockedErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.LOCKED)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpResendRejectedException.class)
        .verify();
  }

  @Test
  void resendOtpExpiredErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.EXPIRED)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpResendRejectedException.class)
        .verify();
  }

  @Test
  void resendOtpInvalidatedErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.INVALIDATED)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpResendRejectedException.class)
        .verify();
  }

  @Test
  void resendOtpAlreadyVerifiedErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.ALREADY_VERIFIED)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpResendRejectedException.class)
        .verify();
  }

  @Test
  void resendOtpNotFoundErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.NOT_FOUND)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpResendRejectedException.class)
        .verify();
  }

  @Test
  void resendOtpNotReplaceableErrors() {
    givenPolicy();
    when(otp.replaceChallenge(any()))
        .thenReturn(Mono.just(withOutcome(OtpReplacementOutcome.NOT_REPLACEABLE)));
    StepVerifier.create(
            newAdapter().resendOtp("old-1", RegistrationContactType.EMAIL, "user@example.com"))
        .expectError(RegistrationOtpResendRejectedException.class)
        .verify();
  }

  // ----- verifyOtp(): each OtpVerificationOutcome switch bucket ---------------------

  private VerifyOtpChallengeResult verifyResult(OtpVerificationOutcome outcome) {
    return new VerifyOtpChallengeResult("challenge-1", outcome, 2, null);
  }

  @Test
  void verifyOtpValidYieldsVerified() {
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(verifyResult(OtpVerificationOutcome.VALID)));
    StepVerifier.create(newAdapter().verifyOtp("challenge-1", "123456"))
        .assertNext(
            result -> assertThat(result).isEqualTo(RegistrationOtpVerificationResult.verified()))
        .verifyComplete();
  }

  @Test
  void verifyOtpExpiredYieldsExpired() {
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(verifyResult(OtpVerificationOutcome.EXPIRED)));
    StepVerifier.create(newAdapter().verifyOtp("challenge-1", "123456"))
        .assertNext(
            result -> assertThat(result).isEqualTo(RegistrationOtpVerificationResult.expired()))
        .verifyComplete();
  }

  @Test
  void verifyOtpLockedYieldsLocked() {
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(verifyResult(OtpVerificationOutcome.LOCKED)));
    StepVerifier.create(newAdapter().verifyOtp("challenge-1", "123456"))
        .assertNext(
            result -> assertThat(result).isEqualTo(RegistrationOtpVerificationResult.locked()))
        .verifyComplete();
  }

  @Test
  void verifyOtpInvalidYieldsInvalidWithRemainingAttempts() {
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(verifyResult(OtpVerificationOutcome.INVALID)));
    StepVerifier.create(newAdapter().verifyOtp("challenge-1", "wrong-code"))
        .assertNext(
            result -> assertThat(result).isEqualTo(RegistrationOtpVerificationResult.invalid(2)))
        .verifyComplete();
  }

  @Test
  void verifyOtpNotFoundYieldsInvalid() {
    when(otp.verifyChallenge(any()))
        .thenReturn(Mono.just(verifyResult(OtpVerificationOutcome.NOT_FOUND)));
    StepVerifier.create(newAdapter().verifyOtp("challenge-1", "123456"))
        .assertNext(
            result -> assertThat(result).isEqualTo(RegistrationOtpVerificationResult.invalid(2)))
        .verifyComplete();
  }
}
