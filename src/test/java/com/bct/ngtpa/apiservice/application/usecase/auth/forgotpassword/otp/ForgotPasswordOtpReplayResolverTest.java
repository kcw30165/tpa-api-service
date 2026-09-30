package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link ForgotPasswordOtpReplayResolver}. */
class ForgotPasswordOtpReplayResolverTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private final ForgotPasswordOtpCommitStatePort commitStates =
      mock(ForgotPasswordOtpCommitStatePort.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final OtpChallengeStatePort challenges = mock(OtpChallengeStatePort.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-replay-1");

  private ForgotPasswordOtpReplayResolver newResolver() {
    return new ForgotPasswordOtpReplayResolver(commitStates, sessions, challenges, clock, 60);
  }

  private ForgotPasswordOtpCommitState commitState(Instant challengeExpiresAt) {
    return new ForgotPasswordOtpCommitState(
        sid.value(), "challenge-1", NOW.minusSeconds(60), challengeExpiresAt);
  }

  private AuthFlowSession sessionWith(
      AuthFlowScenario scenario,
      AuthFlowStatus status,
      String activeChallengeId,
      Map<String, String> attrs) {
    return new AuthFlowSession(
        sid,
        scenario,
        status,
        "csrfhash",
        activeChallengeId,
        new AuthFlowSessionData("email", "j***@example.com", attrs),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private OtpChallenge challengeMatching(ForgotPasswordOtpCommitState state) {
    return new OtpChallenge(
        state.otpChallengeId(),
        OtpScenario.FORGOT_PASSWORD,
        state.authFlowSessionId(),
        OtpChannel.EMAIL,
        "destinationhash",
        "codehash",
        OtpStatus.CREATED,
        0,
        5,
        0,
        5,
        NOW.minusSeconds(120),
        state.challengeExpiresAt(),
        NOW.minusSeconds(90),
        null,
        null);
  }

  // ----- guards ------------------------------------------------------------

  @Test
  void nullRefYieldsSystemError() {
    StepVerifier.create(newResolver().resolve(null))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void blankRefYieldsSystemError() {
    StepVerifier.create(newResolver().resolve("   "))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void commitStateMissingYieldsSystemError() {
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void commitStatesLoadErrorYieldsSystemError() {
    when(commitStates.load(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  // ----- session gate --------------------------------------------------------

  @Test
  void sessionMissingYieldsSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void wrongScenarioYieldsSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.LOGIN,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        Map.of()))));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void wrongStatusYieldsSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.RESET_AUTHORIZED,
                        "challenge-1",
                        Map.of()))));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void challengeIdMismatchYieldsSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "different-challenge",
                        Map.of()))));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  // ----- challenge gate --------------------------------------------------------

  @Test
  void challengeMissingYieldsSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        Map.of()))));
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void challengeBindingMismatchYieldsSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        Map.of()))));
    OtpChallenge mismatched =
        new OtpChallenge(
            "challenge-1",
            OtpScenario.LOGIN,
            sid.value(),
            OtpChannel.EMAIL,
            "destinationhash",
            "codehash",
            OtpStatus.CREATED,
            0,
            5,
            0,
            5,
            NOW.minusSeconds(120),
            state.challengeExpiresAt(),
            NOW.minusSeconds(90),
            null,
            null);
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(mismatched)));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  @Test
  void challengesLoadErrorYieldsSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        Map.of()))));
    when(challenges.findById(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }

  // ----- expiry / happy path --------------------------------------------------

  @Test
  void expiredChallengeYieldsExpired() {
    ForgotPasswordOtpCommitState state = commitState(NOW.minusSeconds(1));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        Map.of()))));
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(challengeMatching(state))));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.EXPIRED))
        .verifyComplete();
  }

  @Test
  void activeChallengeWithSessionResendTimingYieldsActive() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    Map<String, String> attrs =
        Map.of("forgotPasswordOtpResendAvailableAt", NOW.plusSeconds(30).toString());
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        attrs))));
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(challengeMatching(state))));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.ACTIVE))
        .verifyComplete();
  }

  @Test
  void activeChallengeFallsBackToChallengeLastSentAt() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        Map.of()))));
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(challengeMatching(state))));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.ACTIVE))
        .verifyComplete();
  }

  @Test
  void malformedResendTimingFallsBackToSystemError() {
    ForgotPasswordOtpCommitState state = commitState(NOW.plusSeconds(300));
    when(commitStates.load(any())).thenReturn(Mono.just(Optional.of(state)));
    Map<String, String> attrs = Map.of("forgotPasswordOtpResendAvailableAt", "not-a-real-instant");
    when(sessions.load(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    sessionWith(
                        AuthFlowScenario.FORGOT_PASSWORD,
                        AuthFlowStatus.OTP_SENT,
                        "challenge-1",
                        attrs))));
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.of(challengeMatching(state))));
    StepVerifier.create(newResolver().resolve("ref-1"))
        .assertNext(
            r ->
                assertThat(r.status()).isEqualTo(ForgotPasswordOtpReplayResult.Status.SYSTEM_ERROR))
        .verifyComplete();
  }
}
