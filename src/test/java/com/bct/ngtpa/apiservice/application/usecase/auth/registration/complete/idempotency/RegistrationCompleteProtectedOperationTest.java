package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.*;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RegistrationCompleteProtectedOperationTest {
  private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
  private static final AuthFlowSessionId SID = new AuthFlowSessionId("afs-safe");
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionCompletionLockPort locks =
      mock(AuthFlowSessionCompletionLockPort.class);
  private final RegistrationCompletionPort completion = mock(RegistrationCompletionPort.class);
  private final RegistrationLookupPort lookup = mock(RegistrationLookupPort.class);
  private final AuthFlowSessionCancellationPort cancellation =
      mock(AuthFlowSessionCancellationPort.class);
  private RegistrationCompleteProtectedOperation operation;
  private AuthFlowSession state;

  @BeforeEach
  void setup() {
    state = session(AuthFlowStatus.USERNAME_VERIFIED);
    when(locks.acquire(eq(SID), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock", "owner")));
    when(locks.release(any())).thenReturn(Mono.just(true));
    when(sessions.load(SID)).thenAnswer(ignored -> Mono.just(Optional.of(state)));
    when(sessions.loadSnapshot(SID))
        .thenAnswer(
            ignored ->
                Mono.just(
                    Optional.of(
                        new AuthFlowSessionSnapshot(
                            state, new SessionRevision("11111111111111111111111111111111")))));
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation -> {
              RevisionedAuthFlowSessionMutationCommand command = invocation.getArgument(0);
              state = command.updated();
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          state, new SessionRevision("22222222222222222222222222222222"))));
            });
    when(sessions.completeRevisioned(any(), any()))
        .thenAnswer(
            invocation -> {
              state = copyWithStatus(state, AuthFlowStatus.COMPLETED, invocation.getArgument(1));
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          state, new SessionRevision("33333333333333333333333333333333"))));
            });
    when(completion.complete(any()))
        .thenReturn(Mono.just(RegistrationCompletionResult.registered("20260717", "120000")));
    operation =
        new RegistrationCompleteProtectedOperation(
            sessions,
            locks,
            completion,
            lookup,
            cancellation,
            Duration.ofSeconds(30),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @RegistrationTestCase("TC-061")
  void finalTrustCompletesReturnsToLoginAndReleasesLock() {
    when(lookup.searchMember(any()))
        .thenReturn(
            Mono.just(
                new RegistrationLookupResult(
                    RegistrationLookupStatus.NOT_ELIGIBLE, List.of(), null)));
    CompleteRegistrationResult result = operation.execute(input()).block();
    assertThat(result.status()).isEqualTo(CompleteRegistrationResultStatus.REGISTERED);
    assertThat(result.nextStep().name()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(result.registered()).isTrue();
    assertThat(result.clearAuthFlowCookie()).isTrue();
    assertThat(state.status()).isEqualTo(AuthFlowStatus.COMPLETED);
    verify(completion).complete(any());
    verify(locks).release(any());
  }

  @Test
  @RegistrationTestCase("TC-062")
  void remainingTrustsPreserveOrderAndFlowForAnotherRegistration() {
    List<RegistrationLookupAccount> accounts =
        List.of(
            new RegistrationLookupAccount(
                "OI", "JPM", "MPF", "P1", "C1", "A", null, Boolean.TRUE, "CU1"),
            new RegistrationLookupAccount(
                "DB", "DBA", "MPF", "P2", "C2", "A", null, Boolean.FALSE, "CU1"));
    when(lookup.searchMember(any()))
        .thenReturn(
            Mono.just(
                new RegistrationLookupResult(RegistrationLookupStatus.ELIGIBLE, accounts, null)));
    CompleteRegistrationResult result = operation.execute(input()).block();
    assertThat(result.status())
        .isEqualTo(CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS);
    assertThat(result.remainingTrusts()).containsExactly(accounts.get(1));
    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.SELECT_TRUST);
    assertThat(state.status()).isEqualTo(AuthFlowStatus.TRUST_SELECTION_READY);
    assertThat(result.clearAuthFlowCookie()).isFalse();
  }

  @Test
  @RegistrationTestCase("TC-065")
  void lockConflictReturnsWaitingWithoutReloadOrDispatch() {
    when(locks.acquire(eq(SID), any()))
        .thenReturn(Mono.just(new SessionLockHandle(false, "lock", "owner")));
    CompleteRegistrationResult result = operation.execute(input()).block();
    assertThat(result.status()).isEqualTo(CompleteRegistrationResultStatus.DUPLICATE_IN_PROGRESS);
    assertThat(result.nextStep().name()).isEqualTo("WAIT");
    verify(sessions, never()).load(any());
    verify(completion, never()).complete(any());
    verify(locks, never()).release(any());
  }

  @Test
  void authoritativeMissingAndInvalidStateStopBeforeDownstream() {
    when(sessions.load(SID)).thenReturn(Mono.just(Optional.empty()));
    assertThat(operation.execute(input()).block().status())
        .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW);
    verify(completion, never()).complete(any());
    reset(completion);
    when(sessions.load(SID)).thenReturn(Mono.just(Optional.of(session(AuthFlowStatus.COMPLETING))));
    assertThat(operation.execute(input()).block().status())
        .isEqualTo(CompleteRegistrationResultStatus.INVALID_STATE);
    verify(completion, never()).complete(any());
  }

  @Test
  void selectedTrustAndProtectedRoleReachDownstreamInApprovedOrder() {
    when(lookup.searchMember(any()))
        .thenReturn(
            Mono.just(
                new RegistrationLookupResult(
                    RegistrationLookupStatus.NOT_ELIGIBLE, List.of(), null)));
    operation.execute(input()).block();
    ArgumentCaptor<RegistrationCompletionCommand> captor =
        ArgumentCaptor.forClass(RegistrationCompletionCommand.class);
    verify(completion).complete(captor.capture());
    RegistrationCompletionCommand downstream = captor.getValue();
    assertThat(downstream.accountEnv()).isEqualTo("OI");
    assertThat(downstream.trustCode()).isEqualTo("JPM");
    assertThat(downstream.userId()).isEqualTo("A1234567");
    assertThat(downstream.customerId()).isEqualTo("member1");
    assertThat(downstream.actorUserRole().name()).isEqualTo("ROLE_USER_MBR");
  }

  @Test
  @RegistrationTestCase("TC-068")
  void businessRejectionsRemainCanonicalAndDoNotExposeRawCode() {
    when(completion.complete(any()))
        .thenReturn(
            Mono.just(RegistrationCompletionResult.rejected("err.tpa.validation.rejected")));
    CompleteRegistrationResult result = operation.execute(input()).block();
    assertThat(result.status()).isEqualTo(CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED);
    assertThat(result.errors())
        .singleElement()
        .satisfies(error -> assertThat(error.code()).isEqualTo("err.registration.complete.failed"));
    assertThat(result.toString())
        .doesNotContain("err.tpa.validation.rejected", "Password1!", "member@example.com");
  }

  @Test
  void memberNotFoundCancelsAndDuplicateCustomerReturnsToUsernameCheck() {
    when(cancellation.cancel(eq(SID), any(), eq(NOW))).thenReturn(Mono.empty());
    when(completion.complete(any()))
        .thenReturn(Mono.just(RegistrationCompletionResult.rejected("err.tpa.member.not.found")));
    CompleteRegistrationResult missing = operation.execute(input()).block();
    assertThat(missing.nextStep().name()).isEqualTo("RESTART_FLOW");
    assertThat(missing.clearAuthFlowCookie()).isTrue();
    verify(cancellation).cancel(eq(SID), any(), eq(NOW));

    reset(sessions, locks, completion, lookup, cancellation);
    setup();
    reset(completion, cancellation);
    when(completion.complete(any()))
        .thenReturn(Mono.just(RegistrationCompletionResult.rejected("err.tpa.cust.id.duplicated")));
    CompleteRegistrationResult duplicate = operation.execute(input()).block();
    assertThat(duplicate.nextStep().name()).isEqualTo("USERNAME_CHECK");
    assertThat(state.status()).isEqualTo(AuthFlowStatus.TRUST_SELECTION_READY);
  }

  @Test
  void memberRegisteredDuplicateRoutesToUsernameCheckInsteadOfSuccess() {
    when(completion.complete(any()))
        .thenReturn(Mono.just(RegistrationCompletionResult.rejected("err.tpa.member.registered")));
    CompleteRegistrationResult result = operation.execute(input()).block();
    assertThat(result.status()).isEqualTo(CompleteRegistrationResultStatus.DOWNSTREAM_REJECTED);
    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
    assertThat(result.registered()).isFalse();
    assertThat(result.clearAuthFlowCookie()).isFalse();
    assertThat(state.status()).isEqualTo(AuthFlowStatus.TRUST_SELECTION_READY);
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code())
                    .isEqualTo("err.registration.complete.memberAlreadyRegistered"));
    verify(lookup, never()).searchMember(any());
  }

  @Test
  void continuationLookupFailureIsDeterministicAfterConfirmedRegistration() {
    when(lookup.searchMember(any()))
        .thenReturn(Mono.error(new IllegalStateException("lookup detail")));
    CompleteRegistrationResult result = operation.execute(input()).block();
    assertThat(result.status())
        .isEqualTo(CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED);
    assertThat(result.nextStep().name()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(result.toString()).doesNotContain("lookup detail");
    verify(completion, times(1)).complete(any());
  }

  @Test
  void preDispatchAndAmbiguousFailuresPropagateAndReleaseLock() {
    var preDispatch =
        new RegistrationCompletionPreDispatchException(new IllegalStateException("profile detail"));
    when(completion.complete(any())).thenReturn(Mono.error(preDispatch));
    StepVerifier.create(operation.execute(input()))
        .expectErrorMatches(error -> error == preDispatch)
        .verify();
    verify(locks).release(any());

    reset(sessions, locks, completion, lookup, cancellation);
    setup();
    reset(completion, locks);
    when(locks.acquire(eq(SID), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock", "owner")));
    when(locks.release(any())).thenReturn(Mono.just(true));
    var ambiguous = new IllegalStateException("ambiguous timeout");
    when(completion.complete(any())).thenReturn(Mono.error(ambiguous));
    StepVerifier.create(operation.execute(input()))
        .expectErrorMatches(error -> error == ambiguous)
        .verify();
    verify(locks).release(any());
  }

  private RegistrationCompleteProtectedOperationInput input() {
    return new RegistrationCompleteProtectedOperationInput(
        command(),
        context(),
        new UsernameCheckState(new SelectedTrust("JPM", "OI"), "member1", NOW, null),
        state);
  }

  private CompleteRegistrationCommand command() {
    return new CompleteRegistrationCommand(
        SID,
        "csrf",
        new RegistrationIdempotencyKey("idem-safe"),
        "Password1!",
        "Password1!",
        "https://portal.test",
        null,
        "en",
        "req",
        Duration.ofMinutes(5));
  }

  private RegistrationAuthFlowContext context() {
    return new RegistrationAuthFlowContext(
        MemberIdentityType.ID,
        "A1234567",
        "01/01/1980",
        RegistrationContactType.EMAIL,
        "member@example.com");
  }

  private AuthFlowSession session(AuthFlowStatus status) {
    return new AuthFlowSession(
        SID,
        AuthFlowScenario.REGISTRATION,
        status,
        "hash",
        "otp",
        new AuthFlowSessionData("EMAIL", "m***@example.com", Map.of()),
        NOW.minusSeconds(60),
        NOW,
        NOW.plusSeconds(1800));
  }

  private AuthFlowSession copyWithStatus(
      AuthFlowSession source, AuthFlowStatus status, Instant updatedAt) {
    return new AuthFlowSession(
        source.authFlowSessionId(),
        source.scenario(),
        status,
        source.csrfTokenHash(),
        source.activeOtpChallengeId(),
        source.data(),
        source.createdAt(),
        updatedAt,
        source.expiresAt());
  }
}
