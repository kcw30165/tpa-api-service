package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.idempotency.DefaultIdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCommands;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyPort;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyRecord;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservation;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationToken;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCompletionLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCompletionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationUsernameCheckContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** Characterizes same-key retry after Registration succeeded but its continuation lookup failed. */
class RegistrationCompleteContinuationReplayCharacterizationTest {
  private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
  private static final AuthFlowSessionId SID = new AuthFlowSessionId("afs-continuation");

  private final RegistrationProtectedAuthFlowContextLoader contexts =
      mock(RegistrationProtectedAuthFlowContextLoader.class);
  private final AuthFlowSessionExpiryRefreshPort expiry =
      mock(AuthFlowSessionExpiryRefreshPort.class);
  private final RegistrationUsernameCheckContextLoader usernames =
      mock(RegistrationUsernameCheckContextLoader.class);
  private final RegistrationPasswordValidator passwords = mock(RegistrationPasswordValidator.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final AuthFlowSessionCompletionLockPort locks =
      mock(AuthFlowSessionCompletionLockPort.class);
  private final RegistrationCompletionPort completion = mock(RegistrationCompletionPort.class);
  private final RegistrationLookupPort lookup = mock(RegistrationLookupPort.class);
  private final AuthFlowSessionCancellationPort cancellation =
      mock(AuthFlowSessionCancellationPort.class);
  private final AtomicReference<AuthFlowSession> sessionState = new AtomicReference<>(session());
  private final InMemoryIdempotencyPort persistence = new InMemoryIdempotencyPort();
  private SharedCompleteRegistrationService service;

  @BeforeEach
  void setUp() {
    var registrationContext =
        new RegistrationAuthFlowContext(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "test@example.com");
    var username =
        new UsernameCheckState(
            new SelectedTrust("JPM", "OI"), "myusername", NOW, NOW.plusSeconds(600));

    when(contexts.load(any()))
        .thenAnswer(
            ignored ->
                Mono.just(
                    sessionState.get().status() == AuthFlowStatus.USERNAME_VERIFIED
                        ? RegistrationProtectedAuthFlowContextResult.valid(
                            "challenge-1", "t***@example.com", registrationContext)
                        : RegistrationProtectedAuthFlowContextResult.invalid(
                            RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS)));
    when(expiry.refresh(eq(SID), any())).thenAnswer(ignored -> Mono.just(sessionState.get()));
    when(usernames.load(any())).thenReturn(username);
    when(passwords.validate(any(), any(), any()))
        .thenReturn(RegistrationPasswordValidationResult.success());

    when(locks.acquire(eq(SID), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock", "owner")));
    when(locks.release(any())).thenReturn(Mono.just(true));
    when(sessions.load(SID)).thenAnswer(ignored -> Mono.just(Optional.of(sessionState.get())));
    when(sessions.loadSnapshot(SID))
        .thenAnswer(
            ignored ->
                Mono.just(
                    Optional.of(
                        new AuthFlowSessionSnapshot(
                            sessionState.get(),
                            new SessionRevision("11111111111111111111111111111111")))));
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            invocation -> {
              RevisionedAuthFlowSessionMutationCommand command = invocation.getArgument(0);
              sessionState.set(command.updated());
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          sessionState.get(),
                          new SessionRevision("22222222222222222222222222222222"))));
            });

    when(completion.complete(any()))
        .thenReturn(Mono.just(RegistrationCompletionResult.registered("20261109", "120000")));
    when(lookup.searchMember(any()))
        .thenReturn(Mono.error(new IllegalStateException("continuation unavailable")));

    var executor =
        new DefaultIdempotencyExecutor(
            persistence,
            () -> new IdempotencyReservationToken("reservationToken0123456789abcdefgh"),
            Clock.fixed(NOW, ZoneOffset.UTC));
    var responses =
        new RegistrationCompleteSharedExecutionBridge(new RegistrationCompleteReplayCodec());
    var operation =
        new RegistrationCompleteProtectedOperation(
            sessions,
            locks,
            completion,
            lookup,
            cancellation,
            Duration.ofSeconds(30),
            Clock.fixed(NOW, ZoneOffset.UTC));
    service =
        new SharedCompleteRegistrationService(
            contexts,
            expiry,
            usernames,
            passwords,
            executor,
            operation,
            responses,
            new RegistrationSharedIdempotencyIdentityFactory(),
            new RegistrationSharedFingerprintFactory(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void sameKeyRetryAfterContinuationFailureIsRejectedBeforeIdempotencyReplay() {
    CompleteRegistrationCommand command = command();

    CompleteRegistrationResult first = service.complete(command).block();
    CompleteRegistrationResult replay = service.complete(command).block();

    assertThat(first.status())
        .isEqualTo(CompleteRegistrationResultStatus.CONTINUATION_LOOKUP_FAILED);
    assertThat(replay.status()).isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW);
    assertThat(sessionState.get().status()).isEqualTo(AuthFlowStatus.COMPLETING);
    assertThat(persistence.record.status()).isEqualTo(IdempotencyStatus.COMPLETED);
    assertThat(persistence.completeCalls).isOne();
    assertThat(persistence.reserveCalls).isOne();
    verify(completion, times(1)).complete(any());
    verify(lookup, times(1)).searchMember(any());
    verify(locks, times(1)).acquire(eq(SID), any());
    verify(expiry, times(1)).refresh(eq(SID), any());
  }

  private static CompleteRegistrationCommand command() {
    return new CompleteRegistrationCommand(
        SID,
        "csrf-token",
        new RegistrationIdempotencyKey("reg-complete-same-key"),
        "Passw0rd!",
        "Passw0rd!",
        "https://portal.example",
        "https://portal.example/complete",
        "en",
        "req-complete",
        Duration.ofMinutes(5));
  }

  private static AuthFlowSession session() {
    return new AuthFlowSession(
        SID,
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.USERNAME_VERIFIED,
        "csrf-hash",
        null,
        new AuthFlowSessionData("email", "t***@example.com", Map.of()),
        NOW.minusSeconds(120),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800));
  }

  private static final class InMemoryIdempotencyPort implements IdempotencyPort {
    private IdempotencyRecord record;
    private int reserveCalls;
    private int completeCalls;

    @Override
    public Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve command) {
      reserveCalls++;
      if (record == null) {
        record =
            new IdempotencyRecord(
                command.operationId(),
                command.fingerprint(),
                command.reservationToken(),
                IdempotencyStatus.PROCESSING,
                null,
                command.now(),
                command.now(),
                command.now().plus(command.ttl()));
        return Mono.just(
            new IdempotencyReservation(
                IdempotencyReservationOutcome.ACQUIRED,
                IdempotencyStatus.PROCESSING,
                null,
                command.reservationToken()));
      }
      if (!record.fingerprint().equals(command.fingerprint())) {
        return Mono.just(
            new IdempotencyReservation(
                IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                record.status(),
                null,
                null));
      }
      return Mono.just(
          new IdempotencyReservation(
              IdempotencyReservationOutcome.COMPLETED_REPLAY,
              IdempotencyStatus.COMPLETED,
              record.terminalResponse(),
              null));
    }

    @Override
    public Mono<IdempotencyRecord> complete(IdempotencyCommands.Complete command) {
      completeCalls++;
      record =
          new IdempotencyRecord(
              command.operationId(),
              command.fingerprint(),
              command.reservationToken(),
              IdempotencyStatus.COMPLETED,
              command.terminalResponse(),
              record.createdAt(),
              command.now(),
              command.now().plus(command.ttl()));
      return Mono.just(record);
    }

    @Override
    public Mono<IdempotencyRecord> fail(IdempotencyCommands.Fail command) {
      return Mono.error(new AssertionError("continuation result should be replayable"));
    }

    @Override
    public Mono<IdempotencyRecord> markUnknown(IdempotencyCommands.MarkUnknown command) {
      return Mono.error(new AssertionError("continuation result should be replayable"));
    }

    @Override
    public Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId operationId) {
      return Mono.just(Optional.ofNullable(record));
    }
  }
}
