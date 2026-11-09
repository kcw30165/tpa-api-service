package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionPreDispatchException;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationUsernameCheckContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link SharedCompleteRegistrationService}. All 9 constructor
 * collaborators are mocked directly - their internals are exercised by their own dedicated tests
 * elsewhere, not here.
 */
class SharedCompleteRegistrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private final RegistrationProtectedAuthFlowContextLoader contexts =
      mock(RegistrationProtectedAuthFlowContextLoader.class);
  private final AuthFlowSessionExpiryRefreshPort expiry =
      mock(AuthFlowSessionExpiryRefreshPort.class);
  private final RegistrationUsernameCheckContextLoader usernames =
      mock(RegistrationUsernameCheckContextLoader.class);
  private final RegistrationPasswordValidator passwords = mock(RegistrationPasswordValidator.class);
  private final IdempotencyExecutor executor = mock(IdempotencyExecutor.class);
  private final IdempotencyOperationId operationId = mock(IdempotencyOperationId.class);
  private final IdempotencyFingerprint fingerprint = mock(IdempotencyFingerprint.class);
  private final RegistrationCompleteProtectedOperation operation =
      mock(RegistrationCompleteProtectedOperation.class);
  private final RegistrationCompleteSharedExecutionBridge responses =
      mock(RegistrationCompleteSharedExecutionBridge.class);
  private final RegistrationSharedIdempotencyIdentityFactory identities =
      mock(RegistrationSharedIdempotencyIdentityFactory.class);
  private final RegistrationSharedFingerprintFactory fingerprints =
      mock(RegistrationSharedFingerprintFactory.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-reg-complete-1");

  private SharedCompleteRegistrationService newService() {
    return new SharedCompleteRegistrationService(
        contexts,
        expiry,
        usernames,
        passwords,
        executor,
        operation,
        responses,
        identities,
        fingerprints,
        clock);
  }

  // ----- fixtures ------------------------------------------------------------

  private RegistrationAuthFlowContext registrationContext() {
    return new RegistrationAuthFlowContext(
        MemberIdentityType.GPN,
        "G12345678",
        "01/01/1990",
        RegistrationContactType.EMAIL,
        "test@example.com");
  }

  private AuthFlowSession sessionWith(AuthFlowStatus status) {
    return sessionWith(sid, status);
  }

  private AuthFlowSession sessionWith(AuthFlowSessionId sessionId, AuthFlowStatus status) {
    return new AuthFlowSession(
        sessionId,
        com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario.REGISTRATION,
        status,
        "csrfhash",
        null,
        new AuthFlowSessionData("email", "j***@example.com", Map.of()),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private CompleteRegistrationCommand command() {
    return command(sid);
  }

  private CompleteRegistrationCommand command(AuthFlowSessionId sessionId) {
    return new CompleteRegistrationCommand(
        sessionId,
        "csrf-token",
        new RegistrationIdempotencyKey("reg-complete-e2e-001"),
        "Passw0rd!",
        "Passw0rd!",
        "https://portal.bcthk.com",
        "https://portal.bcthk.com/complete",
        "en",
        "req-1",
        Duration.ofMinutes(5));
  }

  private void givenIdempotencyIdentityBuildable() {
    when(identities.create(any(), any(), any())).thenReturn(operationId);
    when(fingerprints.create(any(), any())).thenReturn(fingerprint);
  }

  private void givenContextValid() {
    givenContextValid(sessionWith(AuthFlowStatus.USERNAME_VERIFIED));
  }

  private void givenContextValid(AuthFlowSession session) {
    when(contexts.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.valid(
                    session, "challenge-1", "j***@example.com", registrationContext())));
  }

  private void givenUsernameLoadsOk() {
    UsernameCheckState username = mock(UsernameCheckState.class);
    when(username.username()).thenReturn("myusername");
    when(usernames.load(any())).thenReturn(username);
  }

  private void givenPasswordValid() {
    RegistrationPasswordValidationResult result = mock(RegistrationPasswordValidationResult.class);
    when(result.valid()).thenReturn(true);
    when(passwords.validate(any(), any(), any())).thenReturn(result);
  }

  private void givenExecutorReturns(IdempotencyReservationOutcome outcome, boolean withResponse) {
    givenIdempotencyIdentityBuildable();
    HydratedIdempotentResponse response =
        withResponse ? mock(HydratedIdempotentResponse.class) : null;
    when(executor.execute(any()))
        .thenReturn(Mono.just(new IdempotentExecutionResult(outcome, response)));
  }

  // ----- guards / context gate ------------------------------------------------

  @Test
  void nullCommandThrowsImmediately() {
    assertThatThrownBy(() -> newService().complete(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void contextCsrfInvalidYieldsCsrfInvalid() {
    when(contexts.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.invalid(
                    RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID)));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(CompleteRegistrationResultStatus.CSRF_INVALID))
        .verifyComplete();
    verify(executor, never()).replayCompleted(any(), any(), any());
    verify(executor, never()).execute(any());
  }

  @Test
  void contextOtherInvalidYieldsInvalidAuthFlow() {
    when(contexts.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.invalid(
                    RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION)));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
    verify(executor, never()).replayCompleted(any(), any(), any());
    verify(executor, never()).execute(any());
  }

  @Test
  void expiredContextYieldsInvalidAuthFlowWithoutInspectingReplay() {
    when(contexts.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.invalid(
                    RegistrationProtectedAuthFlowContextStatus.EXPIRED_OR_TERMINAL_SESSION)));

    StepVerifier.create(newService().complete(command()))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW))
        .verifyComplete();

    verify(executor, never()).replayCompleted(any(), any(), any());
    verify(executor, never()).execute(any());
  }

  @Test
  void expiryRefreshErrorPropagates() {
    givenContextValid();
    when(expiry.refreshUsernameVerified(any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().complete(command()))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- session status / username / password gates --------------------------

  @Test
  void wrongSessionStatusYieldsInvalidState() {
    givenContextValid(sessionWith(AuthFlowStatus.OTP_SENT));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(CompleteRegistrationResultStatus.INVALID_STATE))
        .verifyComplete();
  }

  @Test
  void usernameLoadThrowingYieldsInvalidAuthFlow() {
    givenContextValid();
    when(expiry.refreshUsernameVerified(any(), any()))
        .thenReturn(Mono.just(sessionWith(AuthFlowStatus.USERNAME_VERIFIED)));
    when(usernames.load(any())).thenThrow(new IllegalStateException("missing context"));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW))
        .verifyComplete();
  }

  @Test
  void passwordValidationFailureYieldsValidationFailed() {
    givenContextValid();
    when(expiry.refreshUsernameVerified(any(), any()))
        .thenReturn(Mono.just(sessionWith(AuthFlowStatus.USERNAME_VERIFIED)));
    givenUsernameLoadsOk();
    RegistrationPasswordValidationResult invalidResult =
        mock(RegistrationPasswordValidationResult.class);
    when(invalidResult.valid()).thenReturn(false);
    when(invalidResult.errors()).thenReturn(List.of());
    when(passwords.validate(any(), any(), any())).thenReturn(invalidResult);
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(CompleteRegistrationResultStatus.VALIDATION_FAILED))
        .verifyComplete();
  }

  // ----- executor outcome routing ---------------------------------------------

  private void givenReadyForExecute() {
    givenContextValid();
    when(expiry.refreshUsernameVerified(any(), any()))
        .thenReturn(Mono.just(sessionWith(AuthFlowStatus.USERNAME_VERIFIED)));
    givenUsernameLoadsOk();
    givenPasswordValid();
  }

  @Test
  void acquiredOutcomeDelegatesToResponsesFromTransientResponse() {
    givenReadyForExecute();
    givenExecutorReturns(IdempotencyReservationOutcome.ACQUIRED, true);
    CompleteRegistrationResult expected =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    when(responses.fromTransientResponse(any())).thenReturn(expected);
    StepVerifier.create(newService().complete(command())).expectNext(expected).verifyComplete();
    verify(executor).execute(any());
    verify(executor, never()).replayCompleted(any(), any(), any());
  }

  @Test
  void completedReplayOutcomeDelegatesToResponsesFromTransientResponse() {
    givenReadyForExecute();
    givenExecutorReturns(IdempotencyReservationOutcome.COMPLETED_REPLAY, true);
    CompleteRegistrationResult expected =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.REGISTERED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            true,
            true,
            List.of(),
            List.of());
    when(responses.fromTransientResponse(any())).thenReturn(expected);
    StepVerifier.create(newService().complete(command())).expectNext(expected).verifyComplete();
    verify(executor).execute(any());
    verify(executor, never()).replayCompleted(any(), any(), any());
  }

  @Test
  void authorizedCompletingSessionReplaysWithoutExecutingCompletion() {
    givenContextValid(sessionWith(AuthFlowStatus.COMPLETING));
    givenUsernameLoadsOk();
    givenPasswordValid();
    givenIdempotencyIdentityBuildable();
    CompleteRegistrationResult expected =
        new CompleteRegistrationResult(
            CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED,
            WorkflowNextStep.RETURN_TO_LOGIN,
            false,
            false,
            List.of(),
            List.of());
    when(executor.replayCompleted(any(), any(), any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new IdempotentExecutionResult(
                        IdempotencyReservationOutcome.COMPLETED_REPLAY,
                        mock(HydratedIdempotentResponse.class)))));
    when(responses.fromTransientResponse(any())).thenReturn(expected);

    StepVerifier.create(newService().complete(command())).expectNext(expected).verifyComplete();

    verify(executor, never()).execute(any());
    verify(executor).replayCompleted(eq(operationId), eq(fingerprint), any());
    verify(operation, never()).execute(any());
    verify(expiry, never()).refreshUsernameVerified(any(), any());
    verify(identities).create(any(), eq(command().idempotencyKey().value()), eq(sid.value()));
  }

  @Test
  void completingSessionWithoutEligibleReplayReturnsInvalidAuthFlow() {
    givenContextValid(sessionWith(AuthFlowStatus.COMPLETING));
    givenUsernameLoadsOk();
    givenPasswordValid();
    givenIdempotencyIdentityBuildable();
    when(executor.replayCompleted(any(), any(), any())).thenReturn(Mono.just(Optional.empty()));

    StepVerifier.create(newService().complete(command()))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW);
              assertThat(result.errors())
                  .singleElement()
                  .satisfies(
                      error ->
                          assertThat(error.code())
                              .isEqualTo(RegistrationErrorKey.AUTH_FLOW_INVALID.value()));
            })
        .verifyComplete();

    verify(executor, never()).execute(any());
    verify(operation, never()).execute(any());
    verify(expiry, never()).refreshUsernameVerified(any(), any());
  }

  @Test
  void completingSessionPropagatesReplayStorageFailureWithoutExecuting() {
    givenContextValid(sessionWith(AuthFlowStatus.COMPLETING));
    givenUsernameLoadsOk();
    givenPasswordValid();
    givenIdempotencyIdentityBuildable();
    IllegalStateException failure = new IllegalStateException("storage failure");
    when(executor.replayCompleted(any(), any(), any())).thenReturn(Mono.error(failure));

    StepVerifier.create(newService().complete(command()))
        .expectErrorMatches(error -> error == failure)
        .verify();

    verify(executor, never()).execute(any());
    verify(operation, never()).execute(any());
  }

  @Test
  void completingSessionValidatesPasswordBeforeInspectingReplay() {
    givenContextValid(sessionWith(AuthFlowStatus.COMPLETING));
    givenUsernameLoadsOk();
    RegistrationPasswordValidationResult invalidResult =
        mock(RegistrationPasswordValidationResult.class);
    when(invalidResult.valid()).thenReturn(false);
    when(invalidResult.errors()).thenReturn(List.of());
    when(passwords.validate(any(), any(), any())).thenReturn(invalidResult);

    StepVerifier.create(newService().complete(command()))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CompleteRegistrationResultStatus.VALIDATION_FAILED))
        .verifyComplete();

    verify(executor, never()).replayCompleted(any(), any(), any());
    verify(executor, never()).execute(any());
    verify(operation, never()).execute(any());
  }

  @Test
  void completingReplayUsesTheCurrentSessionAsOperationScope() {
    AuthFlowSessionId otherSessionId = new AuthFlowSessionId("afs-reg-complete-2");
    givenContextValid(sessionWith(otherSessionId, AuthFlowStatus.COMPLETING));
    givenUsernameLoadsOk();
    givenPasswordValid();
    givenIdempotencyIdentityBuildable();
    when(executor.replayCompleted(any(), any(), any())).thenReturn(Mono.just(Optional.empty()));

    StepVerifier.create(newService().complete(command(otherSessionId)))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW))
        .verifyComplete();

    verify(identities)
        .create(any(), eq(command().idempotencyKey().value()), eq(otherSessionId.value()));
    verify(executor, never()).execute(any());
    verify(operation, never()).execute(any());
  }

  @Test
  void duplicateInProgressYieldsWait() {
    givenReadyForExecute();
    givenExecutorReturns(IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, false);
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> {
              assertThat(r.status())
                  .isEqualTo(CompleteRegistrationResultStatus.DUPLICATE_IN_PROGRESS);
              assertThat(r.nextStep()).isEqualTo(WorkflowNextStep.WAIT);
            })
        .verifyComplete();
  }

  @Test
  void keyReusedYieldsIdempotencyKeyReused() {
    givenReadyForExecute();
    givenExecutorReturns(IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, false);
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(CompleteRegistrationResultStatus.IDEMPOTENCY_KEY_REUSED))
        .verifyComplete();
  }

  @Test
  void failedExistingYieldsSystemFailed() {
    givenReadyForExecute();
    givenExecutorReturns(IdempotencyReservationOutcome.FAILED_EXISTING, false);
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(CompleteRegistrationResultStatus.SYSTEM_FAILED))
        .verifyComplete();
  }

  @Test
  void unknownOutcomeExistingYieldsUnknownOutcome() {
    givenReadyForExecute();
    givenExecutorReturns(IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, false);
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(CompleteRegistrationResultStatus.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  @Test
  void completionFailureClassifierSeparatesKnownPreDispatchFromAmbiguousFailure() {
    givenReadyForExecute();
    givenIdempotencyIdentityBuildable();
    AtomicReference<IdempotentExecutionCommand> execution = new AtomicReference<>();
    when(executor.execute(any()))
        .thenAnswer(
            invocation -> {
              execution.set(invocation.getArgument(0));
              return Mono.just(
                  new IdempotentExecutionResult(
                      IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING, null));
            });

    StepVerifier.create(newService().complete(command()))
        .assertNext(
            result ->
                assertThat(result.status())
                    .isEqualTo(CompleteRegistrationResultStatus.UNKNOWN_OUTCOME))
        .verifyComplete();

    var preDispatch =
        new RegistrationCompletionPreDispatchException(new IllegalStateException("profile"));
    var possibleDispatchFailure = new IllegalStateException("downstream timeout");
    assertThat(execution.get().failureClassifier().classify(preDispatch))
        .isEqualTo(IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
    assertThat(execution.get().failureClassifier().classify(possibleDispatchFailure))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }
}
