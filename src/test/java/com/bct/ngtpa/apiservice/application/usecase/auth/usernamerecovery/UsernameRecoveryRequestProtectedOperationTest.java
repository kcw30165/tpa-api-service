package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.*;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenValidationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.*;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UsernameRecoveryRequestProtectedOperationTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final String CSRF = "csrf-secret";
  private static final String SESSION = "afs-secret";
  private static final String REF = "sch-opaque-1";
  private static final String CORRELATION = "correlation-secret";
  private static final String CONTEXT_SECRET = "0123456789abcdef0123456789abcdef";
  private final FakeAuthFlowPort sessions = new FakeAuthFlowPort();
  private final FakeDestinationPort destinations = new FakeDestinationPort();
  private final FakeDeliveryPort deliveries = new FakeDeliveryPort();
  private final UsernameRecoveryAuthFlowContextBinding binding =
      new UsernameRecoveryAuthFlowContextBinding(
          new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", CONTEXT_SECRET));
  private UsernameRecoveryRequestProtectedOperation operation;

  @BeforeEach
  void setUp() {
    sessions.snapshot = snapshot(context(UsernameRecoveryDeliveryState.AVAILABLE, false));
    operation = newOperation(List.of());
  }

  @Test
  void disallowedOriginAndCsrfMismatchReturnProvenPreDispatchBeforeDelivery() {
    operation = newOperation(List.of("https://app.example"));
    expectRequestError(execute(CSRF, "https://evil.example"));
    assertThat(deliveries.deliverCalls).isZero();
    operation = newOperation(List.of());
    assertProvenPreDispatch(execute("wrong-csrf", null));
    assertThat(deliveries.deliverCalls).isZero();
  }

  @Test
  void sessionAbsentSessionExpiredAndWrongScenarioReturnProvenPreDispatch() {
    sessions.snapshot = null;
    assertProvenPreDispatch(execute(CSRF, null));
    sessions.snapshot =
        snapshot(
            context(UsernameRecoveryDeliveryState.AVAILABLE, false),
            AuthFlowScenario.USERNAME_RECOVERY,
            NOW.minusSeconds(1));
    assertProvenPreDispatch(execute(CSRF, null));
    sessions.snapshot =
        snapshot(
            context(UsernameRecoveryDeliveryState.AVAILABLE, false),
            AuthFlowScenario.LOGIN,
            NOW.plusSeconds(600));
    assertProvenPreDispatch(execute(CSRF, null));
    assertThat(deliveries.deliverCalls).isZero();
  }

  @Test
  void
      invalidSchemeRefUnregisteredCandidateAlreadyRequestedAndCandidateNotAvailableAreDeterministic() {
    assertOutcome(
        execute(command("sch-missing"), CSRF, null),
        UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF);
    sessions.snapshot = snapshot(unregisteredContext());
    assertOutcome(
        execute(command("sch-unregistered"), CSRF, null),
        UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE);
    sessions.snapshot = snapshot(context(UsernameRecoveryDeliveryState.REQUESTED, false));
    assertOutcome(execute(CSRF, null), UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED);
    sessions.snapshot = snapshot(context(UsernameRecoveryDeliveryState.RESERVED, false));
    assertProvenPreDispatch(execute(CSRF, null));
    assertThat(deliveries.deliverCalls).isZero();
  }

  @Test
  void reservationRevisionConflictReturnsProvenPreDispatch() {
    sessions.outcomeAtCall = 1;
    sessions.forcedOutcome = AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;
    assertProvenPreDispatch(execute(CSRF, null));
    assertThat(deliveries.deliverCalls).isZero();
  }

  @Test
  void acceptedDeliveryReservesBeforeOneDispatchAndPersistsRequestedProjection() {
    final boolean[] reservedBeforeDispatch = {false};
    deliveries.beforeDeliver =
        () -> reservedBeforeDispatch[0] = storedState() == UsernameRecoveryDeliveryState.RESERVED;
    StepVerifier.create(execute(CSRF, null))
        .assertNext(
            result -> {
              assertThat(result.completed()).isTrue();
              assertThat(result.result().outcome())
                  .isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
              assertThat(result.result().maskedDestination()).isEqualTo("p*****c@ah.ozk");
              assertThat(result.result().destinationType())
                  .isEqualTo(UsernameRecoveryDestinationType.EMAIL);
            })
        .verifyComplete();
    assertThat(reservedBeforeDispatch[0]).isTrue();
    assertThat(deliveries.deliverCalls).isEqualTo(1);
    assertThat(storedState()).isEqualTo(UsernameRecoveryDeliveryState.REQUESTED);
    assertThat(storedCandidate().senderReference()).isEqualTo("providerMessageId");
  }

  @Test
  void workflowCompletionIsPersistedWhenLastRegisteredCandidateIsRequested() {
    StepVerifier.create(execute(CSRF, null))
        .assertNext(
            result ->
                assertThat(result.result().workflowStatus())
                    .isEqualTo(UsernameRecoveryWorkflowStatus.COMPLETED))
        .verifyComplete();
    assertThat(sessions.snapshot.session().status()).isEqualTo(AuthFlowStatus.COMPLETED);
  }

  @Test
  void definiteDeliveryFailurePersistsFailedAsDeterministicTerminalResult() {
    deliveries.result =
        new MessageDeliveryResult(
            MessageDeliveryStatus.FAILED,
            MessageDeliveryChannel.EMAIL,
            null,
            "REJECTED",
            "rejected");
    StepVerifier.create(execute(CSRF, null))
        .assertNext(
            result -> {
              assertThat(result.completed()).isTrue();
              assertThat(result.result().outcome())
                  .isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED);
            })
        .verifyComplete();
    assertThat(deliveries.deliverCalls).isEqualTo(1);
    assertThat(storedState()).isEqualTo(UsernameRecoveryDeliveryState.FAILED);
  }

  @Test
  void unknownDeliveryOutcomeEmptyDeliveryResultAndReactiveDeliveryFailureArePossibleSideEffect() {
    deliveries.result =
        new MessageDeliveryResult(
            MessageDeliveryStatus.UNKNOWN_OUTCOME, MessageDeliveryChannel.EMAIL, null, null, null);
    assertPossibleSideEffect(execute(CSRF, null));
    resetAvailable();
    deliveries.empty = true;
    assertPossibleSideEffect(execute(CSRF, null));
    resetAvailable();
    deliveries.fail = true;
    assertPossibleSideEffect(execute(CSRF, null));
    assertThat(deliveries.deliverCalls).isEqualTo(1);
    assertThat(storedState()).isEqualTo(UsernameRecoveryDeliveryState.UNKNOWN_OUTCOME);
  }

  @Test
  void acceptedPersistenceConflictIsPossibleSideEffect() {
    sessions.outcomeAtCall = 2;
    sessions.forcedOutcome = AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED;
    assertPossibleSideEffect(execute(CSRF, null));
    assertThat(deliveries.deliverCalls).isEqualTo(1);
  }

  @Test
  void effectiveDestinationFailureIsProvenPreDispatch() {
    destinations.fail = true;
    StepVerifier.create(execute(CSRF, null))
        .assertNext(
            result -> {
              assertThat(result.failureBoundary())
                  .isEqualTo(UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary.NONE);
              assertThat(result.result().outcome())
                  .isEqualTo(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED);
            })
        .verifyComplete();
    assertThat(deliveries.deliverCalls).isZero();
    assertThat(storedState()).isEqualTo(UsernameRecoveryDeliveryState.FAILED);
  }

  @Test
  void diagnosticsDoNotExposeProtectedValues() {
    deliveries.failure =
        new IllegalStateException(
            "username@example.test csrf-secret afs-secret correlation-secret");
    deliveries.fail = true;
    StepVerifier.create(execute(CSRF, null))
        .assertNext(
            result ->
                assertThat(result.toString())
                    .doesNotContain(
                        "username@example.test", "csrf-secret", "afs-secret", "correlation-secret"))
        .verifyComplete();
  }

  private UsernameRecoveryRequestProtectedOperation newOperation(List<String> origins) {
    return new UsernameRecoveryRequestProtectedOperation(
        binding,
        new CsrfTokenValidationService(new Sha256CsrfTokenHasher()),
        destinations,
        sessions,
        deliveries,
        new MaskingService(new MaskingProperties()),
        origins,
        "subject",
        "body",
        "sms");
  }

  private Mono<UsernameRecoveryRequestProtectedExecutionResult> execute(
      String csrf, String origin) {
    return execute(command(REF), csrf, origin);
  }

  private Mono<UsernameRecoveryRequestProtectedExecutionResult> execute(
      UsernameRecoveryRequestCommand command, String csrf, String origin) {
    return operation.execute(command, csrf, origin, null, CORRELATION, NOW);
  }

  private static UsernameRecoveryRequestCommand command(String ref) {
    return new UsernameRecoveryRequestCommand(
        new AuthFlowSessionId(SESSION), new UsernameRecoverySchemeRef(ref), "idem_key_1234");
  }

  private void resetAvailable() {
    sessions.calls = 0;
    sessions.outcomeAtCall = -1;
    sessions.snapshot = snapshot(context(UsernameRecoveryDeliveryState.AVAILABLE, false));
    deliveries.empty = false;
    deliveries.fail = false;
    deliveries.deliverCalls = 0;
    deliveries.result =
        new MessageDeliveryResult(
            MessageDeliveryStatus.ACCEPTED,
            MessageDeliveryChannel.EMAIL,
            "providerMessageId",
            null,
            null);
  }

  private void assertPossibleSideEffect(
      Mono<UsernameRecoveryRequestProtectedExecutionResult> mono) {
    StepVerifier.create(mono)
        .assertNext(
            result ->
                assertThat(result.failureBoundary())
                    .isEqualTo(
                        UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary
                            .POSSIBLE_SIDE_EFFECT))
        .verifyComplete();
  }

  private static void assertOutcome(
      Mono<UsernameRecoveryRequestProtectedExecutionResult> mono,
      UsernameRecoveryRequestResult.Outcome outcome) {
    StepVerifier.create(mono)
        .assertNext(result -> assertThat(result.result().outcome()).isEqualTo(outcome))
        .verifyComplete();
  }

  private static void expectRequestError(Mono<?> mono) {
    StepVerifier.create(mono).expectError(UsernameRecoveryRequestException.class).verify();
  }

  private static void assertProvenPreDispatch(
      Mono<UsernameRecoveryRequestProtectedExecutionResult> mono) {
    StepVerifier.create(mono)
        .assertNext(
            result -> {
              assertThat(result.completed()).isFalse();
              assertThat(result.failureBoundary())
                  .isEqualTo(
                      UsernameRecoveryRequestProtectedExecutionResult.FailureBoundary
                          .PROVEN_PRE_DISPATCH);
              assertThat(result.failure()).isInstanceOf(UsernameRecoveryRequestException.class);
            })
        .verifyComplete();
  }

  private UsernameRecoveryDeliveryState storedState() {
    return storedCandidate().deliveryState();
  }

  private UsernameRecoverySessionCandidate storedCandidate() {
    return binding.load(sessions.snapshot.session()).candidates().get(0);
  }

  private AuthFlowSessionSnapshot snapshot(UsernameRecoveryAuthFlowContext context) {
    return snapshot(context, AuthFlowScenario.USERNAME_RECOVERY, NOW.plusSeconds(600));
  }

  private AuthFlowSessionSnapshot snapshot(
      UsernameRecoveryAuthFlowContext context, AuthFlowScenario scenario, Instant expiry) {
    AuthFlowSession plain =
        new AuthFlowSession(
            new AuthFlowSessionId(SESSION),
            scenario,
            AuthFlowStatus.STARTED,
            new Sha256CsrfTokenHasher().hash(CSRF),
            null,
            AuthFlowSessionData.empty(),
            NOW.minusSeconds(10),
            NOW.minusSeconds(10),
            expiry);
    return new AuthFlowSessionSnapshot(
        binding.bind(plain, context, NOW), new SessionRevision("00000000000000000000000000000000"));
  }

  private static UsernameRecoveryAuthFlowContext context(
      UsernameRecoveryDeliveryState state, boolean second) {
    return new UsernameRecoveryAuthFlowContext(
        state == UsernameRecoveryDeliveryState.REQUESTED
            ? UsernameRecoveryWorkflowStatus.COMPLETED
            : UsernameRecoveryWorkflowStatus.ACTIVE,
        new UsernameRecoveryVerifiedDestination(
            "pepperc@ah.ozk", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
        Locale.ENGLISH,
        List.of(candidate(REF, true, "user-1", state)));
  }

  private static UsernameRecoveryAuthFlowContext unregisteredContext() {
    return new UsernameRecoveryAuthFlowContext(
        UsernameRecoveryWorkflowStatus.ACTIVE,
        new UsernameRecoveryVerifiedDestination(
            "pepperc@ah.ozk", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
        Locale.ENGLISH,
        List.of(
            candidate(REF, true, "user-1", UsernameRecoveryDeliveryState.AVAILABLE),
            candidateWithIdentity(
                "sch-unregistered",
                "DB2",
                "DBB",
                false,
                null,
                UsernameRecoveryDeliveryState.AVAILABLE)));
  }

  private static UsernameRecoverySessionCandidate candidate(
      String ref, boolean registered, String username, UsernameRecoveryDeliveryState state) {
    return candidateWithIdentity(ref, "DB", "DBA", registered, username, state);
  }

  private static UsernameRecoverySessionCandidate candidateWithIdentity(
      String ref,
      String accountEnv,
      String trustCode,
      boolean registered,
      String username,
      UsernameRecoveryDeliveryState state) {
    boolean requested = state == UsernameRecoveryDeliveryState.REQUESTED;
    return new UsernameRecoverySessionCandidate(
        new UsernameRecoveryCandidate(
            new UsernameRecoverySchemeRef(ref), accountEnv, trustCode, registered, username, state),
        requested ? NOW : null,
        requested ? "existing" : null);
  }

  private final class FakeAuthFlowPort implements AuthFlowSessionPort {
    AuthFlowSessionSnapshot snapshot;
    int calls;
    int outcomeAtCall = -1;
    AuthFlowSessionMutationOutcome forcedOutcome;

    public Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(AuthFlowSessionId id) {
      return Mono.just(Optional.ofNullable(snapshot));
    }

    public Mono<AuthFlowSessionMutationResult> updateRevisioned(
        RevisionedAuthFlowSessionMutationCommand command) {
      calls++;
      if (calls == outcomeAtCall)
        return Mono.just(AuthFlowSessionMutationResult.outcome(forcedOutcome));
      snapshot =
          new AuthFlowSessionSnapshot(
              command.updated(), new SessionRevision(String.format("%032d", calls)));
      return Mono.just(
          new AuthFlowSessionMutationResult(AuthFlowSessionMutationOutcome.APPLIED, snapshot));
    }

    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand c) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
      return Mono.just(Optional.ofNullable(snapshot).map(AuthFlowSessionSnapshot::session));
    }
  }

  private static final class FakeDestinationPort
      implements UsernameRecoveryEffectiveDestinationPort {
    boolean fail;

    public Mono<UsernameRecoveryEffectiveDestinationResult> resolve(
        UsernameRecoveryEffectiveDestinationCommand c) {
      return fail
          ? Mono.error(new IllegalStateException("destination"))
          : Mono.just(
              new UsernameRecoveryEffectiveDestinationResult(
                  "pepperc@ah.ozk", UsernameRecoveryVerifiedDestination.Channel.EMAIL));
    }
  }

  private static final class FakeDeliveryPort implements MessageDeliveryPort {
    MessageDeliveryResult result =
        new MessageDeliveryResult(
            MessageDeliveryStatus.ACCEPTED,
            MessageDeliveryChannel.EMAIL,
            "providerMessageId",
            null,
            null);
    RuntimeException failure = new IllegalStateException("delivery");
    boolean fail;
    boolean empty;
    int deliverCalls;
    Runnable beforeDeliver = () -> {};

    public Mono<MessageDeliveryResult> deliver(MessageDeliveryCommand c) {
      deliverCalls++;
      beforeDeliver.run();
      if (fail) return Mono.error(failure);
      if (empty) return Mono.empty();
      return Mono.just(result);
    }
  }
}
