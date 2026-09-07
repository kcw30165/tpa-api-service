package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.*;
import com.bct.ngtpa.apiservice.integration.registration.support.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationCompletionSuccessIntegrationTest {
  static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
  static final AuthFlowSessionId SID = new AuthFlowSessionId("afs-safe");
  RegistrationProtectedAuthFlowContextLoader context =
      mock(RegistrationProtectedAuthFlowContextLoader.class);
  AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  RegistrationUsernameCheckContextLoader username =
      mock(RegistrationUsernameCheckContextLoader.class);
  RegistrationPasswordValidator passwords = mock(RegistrationPasswordValidator.class);
  RegistrationIdempotencyPort idempotency = mock(RegistrationIdempotencyPort.class);
  RegistrationRequestHashGenerator hashes = mock(RegistrationRequestHashGenerator.class);
  AuthFlowSessionCompletionLockPort locks = mock(AuthFlowSessionCompletionLockPort.class);
  AuthFlowSessionExpiryRefreshPort expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
  RegistrationCompletionPort completion = mock(RegistrationCompletionPort.class);
  RegistrationLookupPort lookup = mock(RegistrationLookupPort.class);
  AuthFlowSessionCancellationPort cancellation = mock(AuthFlowSessionCancellationPort.class);
  CompleteRegistrationService service;
  AuthFlowSession state;

  @BeforeEach
  void setup() {
    state = session(AuthFlowStatus.USERNAME_VERIFIED);
    when(context.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.valid(
                    "otp-safe",
                    "m***@example.com",
                    new RegistrationAuthFlowContext(
                        MemberIdentityType.ID,
                        "user-safe",
                        "01/01/1980",
                        RegistrationContactType.EMAIL,
                        "member@example.com"))));
    when(expiry.refresh(eq(SID), any())).thenAnswer(i -> Mono.just(state));
    when(sessions.load(SID)).thenAnswer(i -> Mono.just(Optional.of(state)));
    when(sessions.loadSnapshot(SID))
        .thenAnswer(
            i ->
                Mono.just(
                    Optional.of(
                        new AuthFlowSessionSnapshot(
                            state, new SessionRevision("11111111111111111111111111111111")))));
    when(sessions.updateRevisioned(any()))
        .thenAnswer(
            i -> {
              RevisionedAuthFlowSessionMutationCommand command = i.getArgument(0);
              state = command.updated();
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          state, new SessionRevision("22222222222222222222222222222222"))));
            });
    when(sessions.completeRevisioned(any(), any()))
        .thenAnswer(
            i -> {
              Instant completedAt = i.getArgument(1);
              state =
                  new AuthFlowSession(
                      state.authFlowSessionId(),
                      state.scenario(),
                      AuthFlowStatus.COMPLETED,
                      state.csrfTokenHash(),
                      state.activeOtpChallengeId(),
                      state.data(),
                      state.createdAt(),
                      completedAt,
                      state.expiresAt());
              return Mono.just(
                  new AuthFlowSessionMutationResult(
                      AuthFlowSessionMutationOutcome.APPLIED,
                      new AuthFlowSessionSnapshot(
                          state, new SessionRevision("33333333333333333333333333333333"))));
            });
    when(username.load(any()))
        .thenReturn(new UsernameCheckState(new SelectedTrust("JPM", "OI"), "user1A!", NOW, null));
    when(passwords.validate(anyString(), anyString(), anyString()))
        .thenReturn(RegistrationPasswordValidationResult.success());
    when(hashes.hash(any(), anyList())).thenReturn(new RegistrationRequestHash("a".repeat(64)));
    RegistrationIdempotencyRecord processingRecord = record();
    when(idempotency.startProcessing(any()))
        .thenReturn(
            Mono.just(
                new RegistrationIdempotencyStartResult(
                    RegistrationIdempotencyStartOutcome.STARTED, processingRecord)));
    when(locks.acquire(eq(SID), any()))
        .thenReturn(Mono.just(new SessionLockHandle(true, "lock", "owner")));
    when(locks.release(any())).thenReturn(Mono.just(true));
    when(completion.complete(any()))
        .thenReturn(Mono.just(RegistrationCompletionResult.registered("20260717", "120000")));
    service =
        new CompleteRegistrationService(
            context,
            sessions,
            username,
            passwords,
            idempotency,
            hashes,
            locks,
            expiry,
            completion,
            lookup,
            cancellation,
            Duration.ofSeconds(30),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @RegistrationTestCase("TC-061")
  void finalTrustCompletesAndReturnsToLogin() {
    when(lookup.searchMember(any()))
        .thenReturn(
            Mono.just(
                new RegistrationLookupResult(
                    RegistrationLookupStatus.NOT_ELIGIBLE, List.of(), null)));
    var r = service.complete(command("idem-final")).block();
    assertThat(r.status()).isEqualTo(CompleteRegistrationResultStatus.REGISTERED);
    assertThat(r.registered()).isTrue();
    assertThat(r.clearAuthFlowCookie()).isTrue();
    assertThat(r.nextStep().name()).isEqualTo("RETURN_TO_LOGIN");
    assertThat(state.status()).isEqualTo(AuthFlowStatus.COMPLETED);
    verify(completion).complete(any());
  }

  @Test
  @RegistrationTestCase("TC-062")
  void remainingTrustsKeepFlowReadyForAnotherSelection() {
    when(lookup.searchMember(any()))
        .thenReturn(
            Mono.just(
                new RegistrationLookupResult(
                    RegistrationLookupStatus.ELIGIBLE,
                    List.of(
                        new RegistrationLookupAccount(
                            "member@example.com", "91234567", "DB", "DBA")),
                    null)));
    var r = service.complete(command("idem-more")).block();
    assertThat(r.status())
        .isEqualTo(CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS);
    assertThat(r.registered()).isTrue();
    assertThat(r.clearAuthFlowCookie()).isFalse();
    assertThat(r.remainingTrusts()).hasSize(1);
    assertThat(state.status()).isEqualTo(AuthFlowStatus.TRUST_SELECTION_READY);
  }

  CompleteRegistrationCommand command(String key) {
    return new CompleteRegistrationCommand(
        SID,
        "csrf",
        new RegistrationIdempotencyKey(key),
        "Password1!",
        "Password1!",
        "https://portal.test",
        null,
        "en",
        "req",
        Duration.ofMinutes(5));
  }

  AuthFlowSession session(AuthFlowStatus status) {
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

  RegistrationIdempotencyRecord record() {
    return new RegistrationIdempotencyRecord(
        RegistrationIdempotencyAction.REGISTRATION_COMPLETE,
        new RegistrationIdempotencyKey("idem-processing"),
        new RegistrationRequestHash("a".repeat(64)),
        RegistrationIdempotencyStatus.PROCESSING,
        null,
        NOW,
        NOW,
        NOW.plusSeconds(300));
  }
}
