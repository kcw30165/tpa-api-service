package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordUsernameLookupPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionWithConcurrencyCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionWithConcurrencyResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ForgotPasswordStartProtectedOperationTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-06T15:00:00Z"), ZoneOffset.UTC);

  @Test
  void freshSuccessPreservesNormalizedIdentityContextSessionAndActorRole() {
    Fixture f = new Fixture();
    when(f.lookup.lookup(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordUsernameLookupResult.confirmed(
                    "cust-db01", "tas-user-01", MemberIdentityType.STAFFNO)));
    when(f.concurrency.start(any())).thenReturn(Mono.just(f.started));
    when(f.csrf.createForSession(any()))
        .thenReturn(
            Mono.just(
                new CreateAuthFlowSessionCsrfResult(
                    "csrf-token-1", CLOCK.instant().plusSeconds(1800), 1800)));

    StepVerifier.create(f.operation.execute(command("afs-existing")))
        .assertNext(
            result -> {
              assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.STARTED);
              assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.ENTER_ID);
              assertThat(result.authFlowSessionId()).isEqualTo("afs-new");
              assertThat(result.csrfToken()).isEqualTo("csrf-token-1");
              assertThat(result.identityType()).isEqualTo("STAFF_NO");
              assertThat(result.toString())
                  .doesNotContain("member01", "cust-db01", "tas-user-01", "csrf-token-1");
            })
        .verifyComplete();

    var lookup = ArgumentCaptor.forClass(StartForgotPasswordUsernameLookupCommand.class);
    verify(f.lookup).lookup(lookup.capture());
    assertThat(lookup.getValue().normalizedUsername()).isEqualTo("member01");
    assertThat(lookup.getValue().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(f.protectedContext.username()).isEqualTo("cust-db01");
    assertThat(f.protectedContext.username()).isEqualTo("cust-db01");
    assertThat(f.protectedContext.identityValue()).isEqualTo("tas-user-01");
    assertThat(f.protectedContext.identityType()).isEqualTo("STAFF_NO");
    assertThat(f.protectedContext.identityVerified()).isFalse();

    var session = ArgumentCaptor.forClass(StartAuthFlowSessionWithConcurrencyCommand.class);
    verify(f.concurrency).start(session.capture());
    assertThat(session.getValue().initialStatus()).isEqualTo(AuthFlowStatus.STARTED);
    assertThat(session.getValue().existingAuthFlowSessionId())
        .isEqualTo(new AuthFlowSessionId("afs-existing"));
    assertThat(session.getValue().data().maskedDestination()).isNull();
    assertThat(session.getValue().data().attributes())
        .containsOnlyKeys(
            "forgotPasswordContextCiphertext",
            "forgotPasswordContextKeyVersion",
            "forgotPasswordContextAlgorithm");
  }

  @Test
  void deterministicAndTechnicalLookupOutcomesRemainPubliclyStable() {
    Fixture notFound = new Fixture();
    when(notFound.lookup.lookup(any()))
        .thenReturn(Mono.just(ForgotPasswordUsernameLookupResult.notFound()));
    assertThat(notFound.operation.execute(command(null)).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.USERNAME_NOT_FOUND);

    Fixture failed = new Fixture();
    when(failed.lookup.lookup(any()))
        .thenReturn(Mono.just(ForgotPasswordUsernameLookupResult.systemFailed()));
    assertThat(failed.operation.execute(command(null)).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.SYSTEM_FAILED);

    Fixture exception = new Fixture();
    when(exception.lookup.lookup(any()))
        .thenReturn(Mono.error(new IllegalStateException("lookup")));
    assertThat(exception.operation.execute(command(null)).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.SYSTEM_FAILED);
  }

  @Test
  void replacementRevisionConflictMapsToRestartWithoutCreatingAResultSession() {
    Fixture f = new Fixture();
    when(f.lookup.lookup(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordUsernameLookupResult.confirmed(
                    "cust-db01", "tas-user-01", MemberIdentityType.ID)));
    when(f.concurrency.start(any()))
        .thenReturn(Mono.error(new IllegalStateException("Auth flow session revision conflict")));

    var result = f.operation.execute(command("afs-existing")).block();
    assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.AUTH_FLOW_INVALID);
    assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
    assertThat(result.authFlowSessionId()).isNull();
    assertThat(result.csrfToken()).isNull();
    verify(f.failureEventPort, never()).publish(any());
  }

  @Test
  void lookupExceptionPublishesCauseFreeLocalIncidentWithoutSensitiveValues() {
    String marker = "R1-PRIVATE-THROWABLE-MARKER";
    Fixture fixture = new Fixture();
    when(fixture.lookup.lookup(any())).thenReturn(Mono.error(new IllegalStateException(marker)));

    var result = fixture.operation.execute(command(null)).block();

    assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.SYSTEM_FAILED);
    var failure = ArgumentCaptor.forClass(FailureEvent.class);
    verify(fixture.failureEventPort).publish(failure.capture());
    verifyNoMoreInteractions(fixture.failureEventPort);
    FailureEvent event = failure.getValue();
    assertThat(event.event()).isEqualTo("forgot-password.start.failed");
    assertThat(event.requestId()).isEqualTo("req-1");
    assertThat(event.capability()).isEqualTo("FORGOT_PASSWORD");
    assertThat(event.operation()).isEqualTo("ForgotPasswordStart");
    assertThat(event.origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(event.internalCode()).isEqualTo("START_TECHNICAL_FAILURE");
    assertThat(event.publicCode()).isEqualTo(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value());
    assertThat(event.httpStatus()).isEqualTo(503);
    assertThat(event.retryable()).isFalse();
    assertThat(event.safeDimensions()).containsExactly(Map.entry("stage", "username-lookup"));
    assertThat(event.toString())
        .doesNotContain(marker, "member01", "cust-db01", "tas-user-01");
  }

  @Test
  void recognizedBusinessRejectionDoesNotEmitTechnicalIncident() {
    Fixture fixture = new Fixture();
    when(fixture.lookup.lookup(any()))
        .thenReturn(Mono.just(ForgotPasswordUsernameLookupResult.notFound()));

    var result = fixture.operation.execute(command(null)).block();

    assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.USERNAME_NOT_FOUND);
    verify(fixture.failureEventPort, never()).publish(any());
  }

  @Test
  void lookupTechnicalResultPreservesSpecificErrorClassifications() {
    for (FailureOrigin origin :
        new FailureOrigin[] {
          FailureOrigin.DOWNSTREAM_CONTRACT,
          FailureOrigin.DOWNSTREAM_INVALID_RESPONSE,
          FailureOrigin.DOWNSTREAM_TRANSPORT,
          FailureOrigin.LOCAL_SYSTEM
        }) {
      Fixture fixture = new Fixture();
      when(fixture.lookup.lookup(any()))
          .thenReturn(Mono.just(ForgotPasswordUsernameLookupResult.systemFailed(origin)));

      var result = fixture.operation.execute(command(null)).block();

      assertThat(result.status()).isEqualTo(StartForgotPasswordResultStatus.SYSTEM_FAILED);
      var failure = ArgumentCaptor.forClass(FailureEvent.class);
      verify(fixture.failureEventPort).publish(failure.capture());
      assertThat(failure.getValue().origin()).isEqualTo(origin);
      assertThat(failure.getValue().safeDimensions()).containsExactly(Map.entry("stage", "username-lookup"));
    }

    Fixture genericFailure = new Fixture();
    when(genericFailure.lookup.lookup(any()))
        .thenReturn(Mono.just(ForgotPasswordUsernameLookupResult.systemFailed()));
    assertThat(genericFailure.operation.execute(command(null)).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.SYSTEM_FAILED);
    var genericEvent = ArgumentCaptor.forClass(FailureEvent.class);
    verify(genericFailure.failureEventPort).publish(genericEvent.capture());
    assertThat(genericEvent.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
  }

  @Test
  void localSessionAndCsrfFailuresPublishLocalSystemIncidents() {
    Fixture sessionFailure = new Fixture();
    when(sessionFailure.lookup.lookup(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordUsernameLookupResult.confirmed(
                    "cust-db01", "tas-user-01", MemberIdentityType.ID)));
    when(sessionFailure.concurrency.start(any()))
        .thenReturn(Mono.error(new IllegalStateException("R1-SESSION-DETAIL")));

    assertThat(sessionFailure.operation.execute(command(null)).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.SYSTEM_FAILED);
    assertPublishedLocalSessionFailure(sessionFailure);

    Fixture csrfFailure = new Fixture();
    when(csrfFailure.lookup.lookup(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordUsernameLookupResult.confirmed(
                    "cust-db01", "tas-user-01", MemberIdentityType.ID)));
    when(csrfFailure.concurrency.start(any())).thenReturn(Mono.just(csrfFailure.started));
    when(csrfFailure.csrf.createForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("R1-CSRF-DETAIL")));

    assertThat(csrfFailure.operation.execute(command(null)).block().status())
        .isEqualTo(StartForgotPasswordResultStatus.SYSTEM_FAILED);
    assertPublishedLocalSessionFailure(csrfFailure);
  }

  private static void assertPublishedLocalSessionFailure(Fixture fixture) {
    var failure = ArgumentCaptor.forClass(FailureEvent.class);
    verify(fixture.failureEventPort).publish(failure.capture());
    verifyNoMoreInteractions(fixture.failureEventPort);
    assertThat(failure.getValue().origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
    assertThat(failure.getValue().requestId()).isEqualTo("req-1");
    assertThat(failure.getValue().safeDimensions())
        .containsExactly(Map.entry("stage", "session-replacement-or-csrf"));
    assertThat(failure.getValue().toString())
        .doesNotContain(
            "R1-SESSION-DETAIL",
            "R1-CSRF-DETAIL",
            "member01",
            "cust-db01",
            "tas-user-01");
  }

  private static StartForgotPasswordCommand command(String existing) {
    return new StartForgotPasswordCommand(
        new ForgotPasswordIdempotencyKey("idem-fp-1"),
        " Member01 ",
        existing,
        "en",
        "req-1",
        Duration.ofMinutes(5),
        ActorUserRole.ROLE_USER_MBR);
  }

  private static final class Fixture {
    private final ForgotPasswordUsernameLookupPort lookup =
        mock(ForgotPasswordUsernameLookupPort.class);
    private final AuthFlowSessionConcurrencyService concurrency =
        mock(AuthFlowSessionConcurrencyService.class);
    private final CreateAuthFlowSessionCsrfUseCase csrf =
        mock(CreateAuthFlowSessionCsrfUseCase.class);
    private final FailureEventPort failureEventPort = mock(FailureEventPort.class);
    private ForgotPasswordAuthFlowContext protectedContext;
    private final ForgotPasswordAuthFlowContextProtectorPort protector =
        new ForgotPasswordAuthFlowContextProtectorPort() {
          @Override
          public ForgotPasswordAuthFlowEncryptedContext protect(
              ForgotPasswordAuthFlowContext context) {
            protectedContext = context;
            return new ForgotPasswordAuthFlowEncryptedContext(
                "ciphertext-1", "v1", ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
          }

          @Override
          public ForgotPasswordAuthFlowContext unprotect(
              ForgotPasswordAuthFlowEncryptedContext encryptedContext) {
            throw new UnsupportedOperationException("unprotect is not used by START parity tests");
          }
        };
    private final AuthFlowSession session = mock(AuthFlowSession.class);
    private final StartAuthFlowSessionWithConcurrencyResult started =
        mock(StartAuthFlowSessionWithConcurrencyResult.class);
    private final ForgotPasswordStartProtectedOperation operation;

    private Fixture() {
      when(session.authFlowSessionId()).thenReturn(new AuthFlowSessionId("afs-new"));
      when(started.session()).thenReturn(session);
      when(started.expiresInSeconds()).thenReturn(1800);
      operation =
          new ForgotPasswordStartProtectedOperation(
              lookup,
              protector,
              concurrency,
              csrf,
              CLOCK,
              new SessionTtlPolicy(true, true, 1800),
              new SessionConcurrencyPolicy(
                  SessionConcurrencyMode.SINGLE_ACTIVE_FLOW,
                  SessionOnNewFlowPolicy.REPLACE_EXISTING),
              "afs",
              16,
              true,
              true,
              true,
              Map.of("STAFF", MemberIdentityType.STAFFNO, "ID", MemberIdentityType.ID),
              failureEventPort);
    }
  }
}
