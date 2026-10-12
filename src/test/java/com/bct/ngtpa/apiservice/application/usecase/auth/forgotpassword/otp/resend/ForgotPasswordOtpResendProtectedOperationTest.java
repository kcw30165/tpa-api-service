package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordInitialOtpPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link ForgotPasswordOtpResendProtectedOperation}. Targets the
 * high-value branches of the resend eligibility/dispatch tree (~85-90% scope, per agreed plan).
 * {@code masker} and {@code refs} are mocked directly - injecting them is a tracked refactor, not
 * done here. All Mono-returning methods on {@code otp} and {@code challengeState} are defensively
 * stubbed in {@link #givenEligible()} to eliminate unstubbed-null-Mono NPEs, regardless of which
 * specific method the production code's private helpers invoke.
 */
class ForgotPasswordOtpResendProtectedOperationTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final Duration lockLease = Duration.ofSeconds(30);
  private final Duration idempotencyTtl = Duration.ofMinutes(10);
  private static final String ALLOWED_ORIGIN = "https://portal.bcthk.com";
  private static final String RAW_CSRF = "csrf-raw-token";
  private static final String PROTECTED_DESTINATION = "john@example.com";
  private static final String MASKED_DESTINATION = "j***@example.com";

  private final SecureForgotPasswordOtpCommitRefGenerator refs =
      mock(SecureForgotPasswordOtpCommitRefGenerator.class);
  private final AuthFlowSessionMutationLockPort locks = mock(AuthFlowSessionMutationLockPort.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final ForgotPasswordAuthFlowContextProtectorPort protector =
      mock(ForgotPasswordAuthFlowContextProtectorPort.class);
  private final EvaluateForgotPasswordOtpResendUseCase foundation =
      mock(EvaluateForgotPasswordOtpResendUseCase.class);
  private final OtpChallengePort otp = mock(OtpChallengePort.class);
  private final OtpChallengeStatePort challengeState = mock(OtpChallengeStatePort.class);
  private final MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
  private final MaskingService masker = mock(MaskingService.class);
  private final ForgotPasswordOtpCommitStatePort commitStates =
      mock(ForgotPasswordOtpCommitStatePort.class);
  private final ForgotPasswordOtpCommitRetentionPolicy retention =
      new ForgotPasswordOtpCommitRetentionPolicy(Duration.ofMinutes(5), Duration.ofHours(1));
  private final NoticeTemplateRegistryPort registry = mock(NoticeTemplateRegistryPort.class);
  private final ForgotPasswordInitialOtpPolicy otpPolicy =
      ForgotPasswordInitialOtpPolicy.approved();

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-otp-1");
  private final String csrfHash = sha256(RAW_CSRF);

  // ----- log capture -------------------------------------------------------

  private ListAppender<ILoggingEvent> logAppender;
  private Logger operationLogger;

  @BeforeEach
  void attachLogAppender() {
    operationLogger =
        (Logger) LoggerFactory.getLogger(ForgotPasswordOtpResendProtectedOperation.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    operationLogger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    operationLogger.detachAppender(logAppender);
  }

  private void assertLoggedFailure(String stage) {
    assertThat(logAppender.list).hasSize(1);
    ILoggingEvent event = logAppender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage())
        .contains(
            "event=forgot-password.resend.failed",
            "requestId=",
            "stage=" + stage,
            "exceptionType=IllegalStateException")
        .doesNotContain("boom");
  }

  // ----- construction helpers ---------------------------------------------

  private ForgotPasswordOtpResendProtectedOperation newOp() {
    return new ForgotPasswordOtpResendProtectedOperation(
        refs,
        locks,
        sessions,
        protector,
        foundation,
        otp,
        challengeState,
        delivery,
        masker,
        commitStates,
        retention,
        registry,
        otpPolicy,
        clock,
        idempotencyTtl,
        lockLease,
        List.of(ALLOWED_ORIGIN));
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private AuthFlowSession sessionWith(
      AuthFlowScenario scenario,
      AuthFlowStatus status,
      Instant expiresAt,
      String activeChallengeId) {
    return new AuthFlowSession(
        sid,
        scenario,
        status,
        csrfHash,
        activeChallengeId,
        new AuthFlowSessionData(
            "email",
            MASKED_DESTINATION,
            Map.of(
                CONTEXT_CIPHERTEXT, "ciphertext-1",
                CONTEXT_KEY_VERSION, "kv-1",
                CONTEXT_ALGORITHM, ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        expiresAt);
  }

  private AuthFlowSession validSession() {
    return sessionWith(
        AuthFlowScenario.FORGOT_PASSWORD,
        AuthFlowStatus.OTP_SENT,
        NOW.plusSeconds(1800),
        "challenge-1");
  }

  private AuthFlowSessionSnapshot snapshotOf(AuthFlowSession session) {
    return new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
  }

  private ForgotPasswordAuthFlowContext validContext() {
    return new ForgotPasswordAuthFlowContext(
        "john",
        "idv-1",
        "CUSTID",
        true,
        "subj-1",
        "email",
        PROTECTED_DESTINATION,
        MASKED_DESTINATION,
        null,
        null,
        null,
        null,
        "v1",
        ForgotPasswordProtectedPhase.OTP_SENT);
  }

  private OtpChallenge validChallenge() {
    return new OtpChallenge(
        "challenge-1",
        OtpScenario.FORGOT_PASSWORD,
        sid.value(),
        OtpChannel.EMAIL,
        sha256(PROTECTED_DESTINATION),
        "codehash",
        OtpStatus.CREATED,
        0,
        5,
        0,
        5,
        NOW.minusSeconds(120),
        NOW.plusSeconds(180),
        NOW.minusSeconds(120),
        null,
        null);
  }

  private ResendForgotPasswordOtpCommand command(String csrfToken, String origin) {
    return new ResendForgotPasswordOtpCommand(
        sid, csrfToken, origin, null, "req-1", "en", "idem-1");
  }

  private ResendForgotPasswordOtpCommand validCommand() {
    return command(RAW_CSRF, ALLOWED_ORIGIN);
  }

  // ----- shared stubbing helpers -------------------------------------------

  private void givenLockAcquired() {
    when(locks.acquire(any(), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock-key", "lock-token")));
    when(locks.release(any())).thenReturn(Mono.just(true));
  }

  private void givenSession(AuthFlowSession session) {
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.of(snapshotOf(session))));
  }

  private void givenContext() {
    when(protector.unprotect(any())).thenReturn(validContext());
    when(masker.maskEmail(any())).thenReturn(MASKED_DESTINATION);
  }

  private void givenChallenge() {
    when(challengeState.findById(any())).thenReturn(Mono.just(Optional.of(validChallenge())));
  }

  private void givenSessionMutationApplied() {
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation ->
                Mono.just(
                    new AuthFlowSessionMutationResult(
                        AuthFlowSessionMutationOutcome.APPLIED, snapshotOf(validSession()))));
  }

  // ----- tests: guards ------------------------------------------------------

  @Test
  void constructorRejectsNullCollaborator() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new ForgotPasswordOtpResendProtectedOperation(
                    null,
                    locks,
                    sessions,
                    protector,
                    foundation,
                    otp,
                    challengeState,
                    delivery,
                    masker,
                    commitStates,
                    retention,
                    registry,
                    otpPolicy,
                    clock,
                    idempotencyTtl,
                    lockLease,
                    List.of(ALLOWED_ORIGIN)))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- tests: lock / session ----------------------------------------------

  @Test
  void lockNotAcquiredYieldsSystemFailed() {
    when(locks.acquire(any(), any())).thenReturn(Mono.just(SessionLockHandle.notAcquired()));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void sessionMissingYieldsAuthFlowInvalid() {
    givenLockAcquired();
    when(sessions.loadSnapshot(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void wrongScenarioYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(
        sessionWith(
            AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, NOW.plusSeconds(1800), "challenge-1"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void wrongStatusYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.RESET_AUTHORIZED,
            NOW.plusSeconds(1800),
            "challenge-1"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void expiredSessionYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.OTP_SENT,
            NOW.minusSeconds(1),
            "challenge-1"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // ----- tests: security ------------------------------------------------------

  @Test
  void invalidCsrfYieldsSecurityRejected() {
    givenLockAcquired();
    givenSession(validSession());
    StepVerifier.create(newOp().execute(command("wrong-token", ALLOWED_ORIGIN)))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SECURITY_REJECTED))
        .verifyComplete();
  }

  @Test
  void disallowedOriginYieldsSecurityRejected() {
    givenLockAcquired();
    givenSession(validSession());
    StepVerifier.create(newOp().execute(command(RAW_CSRF, "https://evil.example.com")))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SECURITY_REJECTED))
        .verifyComplete();
  }

  // ----- tests: context / binding ----------------------------------------------

  @Test
  void decryptFailureYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(validSession());
    when(protector.unprotect(any())).thenThrow(new IllegalStateException("boom"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void decryptFailureYieldsAuthFlowInvalidAndLogsTheFailure() {
    givenLockAcquired();
    givenSession(validSession());
    when(protector.unprotect(any())).thenThrow(new IllegalStateException("boom"));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
    assertLoggedFailure("decrypt");
  }

  @Test
  void maskerMismatchYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(validSession());
    when(protector.unprotect(any())).thenReturn(validContext());
    when(masker.maskEmail(any())).thenReturn("different-mask");
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void noActiveChallengeBlankYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(
        sessionWith(
            AuthFlowScenario.FORGOT_PASSWORD,
            AuthFlowStatus.OTP_SENT,
            NOW.plusSeconds(1800),
            null));
    givenContext();
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void challengeNotFoundYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    when(challengeState.findById(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  @Test
  void challengeBindingMismatchYieldsAuthFlowInvalid() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    OtpChallenge mismatched =
        new OtpChallenge(
            "challenge-1",
            OtpScenario.LOGIN,
            sid.value(),
            OtpChannel.EMAIL,
            sha256(PROTECTED_DESTINATION),
            "codehash",
            OtpStatus.CREATED,
            0,
            5,
            0,
            5,
            NOW.minusSeconds(120),
            NOW.plusSeconds(180),
            NOW.minusSeconds(120),
            null,
            null);
    when(challengeState.findById(any())).thenReturn(Mono.just(Optional.of(mismatched)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.AUTH_FLOW_INVALID))
        .verifyComplete();
  }

  // ----- tests: eligibility mapping -------------------------------------------

  @Test
  void cooldownActiveIsReported() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    givenChallenge();
    when(foundation.evaluate(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendResult(
                    ForgotPasswordOtpResendEligibilityStatus.COOLDOWN_ACTIVE, 30, 1)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.COOLDOWN_ACTIVE))
        .verifyComplete();
  }

  @Test
  void resendLimitReachedIsReported() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    givenChallenge();
    when(foundation.evaluate(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendResult(
                    ForgotPasswordOtpResendEligibilityStatus.RESEND_LIMIT_REACHED, null, 5)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.RESEND_LIMIT_REACHED))
        .verifyComplete();
  }

  @Test
  void unknownOutcomeBlockedIsReported() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    givenChallenge();
    when(foundation.evaluate(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendResult(
                    ForgotPasswordOtpResendEligibilityStatus.UNKNOWN_OUTCOME_BLOCKED, null, 0)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  @Test
  void foundationErrorYieldsSystemFailed() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    givenChallenge();
    when(foundation.evaluate(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void foundationErrorYieldsSystemFailedAndLogsTheFailure() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    givenChallenge();
    when(foundation.evaluate(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
    assertLoggedFailure("foundation");
  }

  @Test
  void loadSnapshotErrorYieldsSystemFailed() {
    givenLockAcquired();
    when(sessions.loadSnapshot(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void loadSnapshotErrorYieldsSystemFailedAndLogsTheFailure() {
    givenLockAcquired();
    when(sessions.loadSnapshot(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
    assertLoggedFailure("execute");
  }

  private AuthFlowSession validSessionWithResendCount(String count) {
    return new AuthFlowSession(
        sid,
        AuthFlowScenario.FORGOT_PASSWORD,
        AuthFlowStatus.OTP_SENT,
        csrfHash,
        "challenge-1",
        new AuthFlowSessionData(
            "email",
            MASKED_DESTINATION,
            Map.of(
                CONTEXT_CIPHERTEXT, "ciphertext-1",
                CONTEXT_KEY_VERSION, "kv-1",
                CONTEXT_ALGORITHM, ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM,
                "forgotPasswordOtpSuccessfulResendCount", count)),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  @Test
  void challengeLookupErrorYieldsAResultAndLogsTheFailure() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    when(challengeState.findById(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand())).expectNextCount(1).verifyComplete();
    assertLoggedFailure("eligibility");
  }

  @Test
  void unparsableResendCountIsLogged() {
    givenLockAcquired();
    givenSession(validSessionWithResendCount("not-a-number"));
    givenContext();
    givenChallenge();
    when(foundation.evaluate(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newOp().execute(validCommand())).expectNextCount(1).verifyComplete();
    List<ILoggingEvent> counted =
        logAppender.list.stream()
            .filter(event -> event.getFormattedMessage().contains("stage=resend-count"))
            .toList();
    assertThat(counted).hasSize(1);
    assertThat(counted.get(0).getLevel()).isEqualTo(Level.ERROR);
    assertThat(counted.get(0).getThrowableProxy()).isNull();
    assertThat(counted.get(0).getFormattedMessage())
        .contains(
            "event=forgot-password.resend.failed",
            "requestId=",
            "exceptionType=NumberFormatException")
        .doesNotContain("not-a-number");
  }

  // ----- tests: eligible -> dispatch -------------------------------------------

  private void givenEligible() {
    givenLockAcquired();
    givenSession(validSession());
    givenContext();
    givenChallenge();
    givenSessionMutationApplied();
    when(refs.generate()).thenReturn("pending-ref-1234567890");
    when(foundation.evaluate(any()))
        .thenReturn(
            Mono.just(
                new ForgotPasswordOtpResendResult(
                    ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE, null, 0)));
    when(otp.createChallenge(any()))
        .thenReturn(
            Mono.just(
                new CreateOtpChallengeResult("candidate-1", "123456", NOW.plusSeconds(300), 300)));
    when(protector.protect(any()))
        .thenReturn(new ForgotPasswordAuthFlowEncryptedContext("ct-2", "kv-2", "AES_GCM_V1"));
    when(registry.resolve(any(), any(), any()))
        .thenReturn(
            new NoticeTemplateDefinition(
                MessageDeliveryType.FORGOT_PASSWORD_OTP,
                MessageDeliveryChannel.EMAIL,
                "en",
                "BCT forgot password verification code",
                "Your BCT forgot password verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                java.util.Set.of("code", "ttlValue", "ttlUnit"),
                "minutes"));

    // --- Defensive stubbing: ALL Mono-returning methods on `otp` and
    // `challengeState`. Mockito returns null (not Mono.empty()) for
    // unstubbed reference-type methods; if reactor subscribes to a null
    // Mono, it throws an NPE that this operation's onErrorReturn(
    // SYSTEM_FAILED) silently swallows. Stubbing every method here
    // eliminates that whole failure class regardless of which specific
    // port method the private invalidateChallenge(...)/similar helpers
    // actually call. ---
    when(otp.invalidateChallenge(any())).thenReturn(Mono.empty());
    when(otp.verifyChallenge(any())).thenReturn(Mono.just(mock(VerifyOtpChallengeResult.class)));
    when(otp.replaceChallenge(any())).thenReturn(Mono.just(mock(ReplaceOtpChallengeResult.class)));
    when(challengeState.save(any())).thenReturn(Mono.empty());
    when(challengeState.update(any())).thenReturn(Mono.empty());
    when(challengeState.delete(any())).thenReturn(Mono.empty());
  }

  @Test
  void eligibleWithDeliveryAcceptedYieldsResent() {
    givenEligible();
    when(delivery.deliver(any()))
        .thenReturn(
            Mono.just(
                new MessageDeliveryResult(
                    MessageDeliveryStatus.ACCEPTED,
                    MessageDeliveryChannel.EMAIL,
                    "provider-1",
                    null,
                    null)));
    when(commitStates.saveIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
    when(commitStates.load(any()))
        .thenAnswer(
            invocation ->
                Mono.just(
                    Optional.of(
                        new ForgotPasswordOtpCommitState(
                            sid.value(), "candidate-1", NOW, NOW.plusSeconds(300)))));
    when(commitStates.delete(any())).thenReturn(Mono.empty());
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r -> {
              assertThat(r.result().status()).isEqualTo(ResendForgotPasswordOtpStatus.RESENT);
              assertThat(r.replayReference()).isNotNull();
            })
        .verifyComplete();
    verify(registry).resolve(any(), any(), any());
  }

  @Test
  void eligibleWithDeliveryFailedYieldsDeliveryFailed() {
    givenEligible();
    when(delivery.deliver(any()))
        .thenReturn(
            Mono.just(
                new MessageDeliveryResult(
                    MessageDeliveryStatus.FAILED,
                    MessageDeliveryChannel.EMAIL,
                    null,
                    "err",
                    "boom")));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.DELIVERY_FAILED))
        .verifyComplete();
  }

  @Test
  void eligibleWithDeliveryUnknownYieldsSystemFailed() {
    givenEligible();
    when(delivery.deliver(any()))
        .thenReturn(
            Mono.just(
                new MessageDeliveryResult(
                    MessageDeliveryStatus.UNKNOWN_OUTCOME,
                    MessageDeliveryChannel.EMAIL,
                    null,
                    null,
                    null)));
    StepVerifier.create(newOp().execute(validCommand()))
        .assertNext(
            r ->
                assertThat(r.result().status())
                    .isEqualTo(ResendForgotPasswordOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
  }
}
