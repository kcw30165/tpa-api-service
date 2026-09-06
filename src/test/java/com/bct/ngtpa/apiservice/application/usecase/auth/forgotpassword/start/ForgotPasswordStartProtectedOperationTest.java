package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
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
    assertThat(f.protectedContext.normalizedUsername()).isEqualTo("member01");
    assertThat(f.protectedContext.customerId()).isEqualTo("cust-db01");
    assertThat(f.protectedContext.tasUserId()).isEqualTo("tas-user-01");
    assertThat(f.protectedContext.publicIdentityType()).isEqualTo("STAFF_NO");
    assertThat(f.protectedContext.identityVerified()).isFalse();

    var session = ArgumentCaptor.forClass(StartAuthFlowSessionWithConcurrencyCommand.class);
    verify(f.concurrency).start(session.capture());
    assertThat(session.getValue().initialStatus()).isEqualTo(AuthFlowStatus.STARTED);
    assertThat(session.getValue().existingAuthFlowSessionId())
        .isEqualTo(new AuthFlowSessionId("afs-existing"));
    assertThat(session.getValue().data().maskedDestination()).isNull();
    assertThat(session.getValue().data().attributes()).containsOnlyKeys(
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
    when(exception.lookup.lookup(any())).thenReturn(Mono.error(new IllegalStateException("lookup")));
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
    private final ForgotPasswordUsernameLookupPort lookup = mock(ForgotPasswordUsernameLookupPort.class);
    private final AuthFlowSessionConcurrencyService concurrency = mock(AuthFlowSessionConcurrencyService.class);
    private final CreateAuthFlowSessionCsrfUseCase csrf = mock(CreateAuthFlowSessionCsrfUseCase.class);
    private ForgotPasswordAuthFlowContext protectedContext;
    private final ForgotPasswordAuthFlowContextProtectorPort protector =
        new ForgotPasswordAuthFlowContextProtectorPort() {
          @Override
          public ForgotPasswordAuthFlowEncryptedContext protect(
              ForgotPasswordAuthFlowContext context) {
            protectedContext = context;
            return new ForgotPasswordAuthFlowEncryptedContext(
                "ciphertext-1",
                "v1",
                ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
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
              Map.of("STAFF", MemberIdentityType.STAFFNO, "ID", MemberIdentityType.ID));
    }
  }
}
