package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
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
    return new AuthFlowSession(
        sid,
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
    return new CompleteRegistrationCommand(
        sid,
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
    when(identities.create(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
    when(fingerprints.create(any(), any())).thenReturn(mock(IdempotencyFingerprint.class));
  }

  private void givenContextValid() {
    when(contexts.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.valid(
                    "challenge-1", "j***@example.com", registrationContext())));
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
  }

  @Test
  void expiryRefreshErrorPropagates() {
    givenContextValid();
    when(expiry.refresh(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().complete(command()))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- session status / username / password gates --------------------------

  @Test
  void wrongSessionStatusYieldsInvalidState() {
    givenContextValid();
    when(expiry.refresh(any(), any())).thenReturn(Mono.just(sessionWith(AuthFlowStatus.OTP_SENT)));
    StepVerifier.create(newService().complete(command()))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(CompleteRegistrationResultStatus.INVALID_STATE))
        .verifyComplete();
  }

  @Test
  void usernameLoadThrowingYieldsInvalidAuthFlow() {
    givenContextValid();
    when(expiry.refresh(any(), any()))
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
    when(expiry.refresh(any(), any()))
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
    when(expiry.refresh(any(), any()))
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
}
