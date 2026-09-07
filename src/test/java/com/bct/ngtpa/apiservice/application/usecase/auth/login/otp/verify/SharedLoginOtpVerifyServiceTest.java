package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpVerifyExecutionBridge;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SharedLoginOtpVerifyServiceTest {
  private static final Duration TTL = Duration.ofSeconds(347);
  private static final VerifyLoginOtpCommand COMMAND =
      new VerifyLoginOtpCommand(
          "afs-secret", "csrf-secret", "123456", "idem-verify", null, null, "en", "req-9");
  private static final ProtectedReplayReference HANDOFF =
      new ProtectedReplayReference("0123456789abcdef0123456789abcdef");

  @Test
  void commandUsesSharedIdentityConstantFingerprintTtlAndConservativeClassifier() {
    Fixture f = new Fixture();
    StepVerifier.create(f.service.verify(COMMAND))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(VerifyLoginOtpStatus.SYSTEM_FAILED))
        .verifyComplete();
    verify(f.identities).create(LoginIdempotencyAction.OTP_VERIFY, "afs-secret", "idem-verify");
    verify(f.fingerprints).otpVerify();
    assertThat(f.executor.command.ttl()).isEqualTo(TTL);
    assertThat(f.executor.command.failureClassifier().classify(new RuntimeException("x")))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verifyNoInteractions(f.operation);
  }

  @Test
  void acquiredSuccessAttachesAuthoritativeProtectedReferenceAndInvokesOperationOnce() {
    Fixture f = new Fixture();
    VerifyLoginOtpResult success = success();
    HydratedIdempotentResponse plain = response(200);
    HydratedIdempotentResponse protectedResponse = plain.withProtectedReplayReference(HANDOFF);
    f.executor.invokeProtected = true;
    when(f.operation.verifyProtected(COMMAND))
        .thenReturn(Mono.just(new LoginOtpVerifyProtectedOperationResult(success, HANDOFF)));
    when(f.responses.toTransientResponse(success)).thenReturn(plain);
    when(f.responses.fromTransientResponse(protectedResponse)).thenReturn(success);
    StepVerifier.create(f.service.verify(COMMAND)).expectNext(success).verifyComplete();
    verify(f.operation).verifyProtected(COMMAND);
    assertThat(f.executor.protectedResponse.protectedReplayReference()).isEqualTo(HANDOFF);
  }

  @Test
  void acquiredDeterministicRejectionCarriesNoProtectedReference() {
    Fixture f = new Fixture();
    VerifyLoginOtpResult rejection =
        new VerifyLoginOtpResult(VerifyLoginOtpStatus.INVALID, 2, false, List.of(), null, null);
    HydratedIdempotentResponse response = response(422);
    f.executor.invokeProtected = true;
    when(f.operation.verifyProtected(COMMAND))
        .thenReturn(Mono.just(new LoginOtpVerifyProtectedOperationResult(rejection, null)));
    when(f.responses.toTransientResponse(rejection)).thenReturn(response);
    when(f.responses.fromTransientResponse(response)).thenReturn(rejection);
    StepVerifier.create(f.service.verify(COMMAND)).expectNext(rejection).verifyComplete();
    assertThat(f.executor.protectedResponse.protectedReplayReference()).isNull();
  }

  @Test
  void completedReplayRestoresProtectedResultWithoutInvokingOperation() {
    Fixture f = new Fixture();
    VerifyLoginOtpResult success = success();
    HydratedIdempotentResponse replay = response(200);
    f.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    f.executor.terminal = replay;
    when(f.responses.fromTransientResponse(replay)).thenReturn(success);
    StepVerifier.create(f.service.verify(COMMAND)).expectNext(success).verifyComplete();
    verifyNoInteractions(f.operation);
  }

  @Test
  void existingLifecycleOutcomesFailClosedWithoutProtectedExecution() {
    for (IdempotencyReservationOutcome outcome :
        List.of(
            IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
            IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
            IdempotencyReservationOutcome.FAILED_EXISTING,
            IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING)) {
      Fixture f = new Fixture();
      f.executor.outcome = outcome;
      StepVerifier.create(f.service.verify(COMMAND))
          .assertNext(
              result -> assertThat(result.status()).isEqualTo(VerifyLoginOtpStatus.SYSTEM_FAILED))
          .verifyComplete();
      verifyNoInteractions(f.operation);
    }
  }

  @Test
  void protectedFailurePropagatesAcrossExecutorBoundary() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    when(f.operation.verifyProtected(COMMAND))
        .thenReturn(Mono.error(new IllegalStateException("verify boundary")));
    StepVerifier.create(f.service.verify(COMMAND)).expectErrorMessage("verify boundary").verify();
  }

  private static VerifyLoginOtpResult success() {
    return new VerifyLoginOtpResult(
        VerifyLoginOtpStatus.ACCOUNTS_READY,
        null,
        true,
        List.of(account()),
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        "fresh-csrf");
  }

  private static LoginSessionAccount account() {
    return new LoginSessionAccount(
        new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
        "DB",
        "12",
        "00000000012",
        "1",
        "0001",
        "TR",
        "OE",
        null,
        null);
  }

  private static HydratedIdempotentResponse response(int status) {
    return new HydratedIdempotentResponse(status, "application/json", "{}", Map.of());
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private IdempotentExecutionCommand command;
    private IdempotencyReservationOutcome outcome =
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private HydratedIdempotentResponse terminal;
    private HydratedIdempotentResponse protectedResponse;
    private boolean invokeProtected;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
      command = supplied;
      if (invokeProtected) {
        return supplied
            .protectedOperation()
            .get()
            .map(
                response -> {
                  protectedResponse = response;
                  return new IdempotentExecutionResult(
                      IdempotencyReservationOutcome.ACQUIRED, response);
                });
      }
      return Mono.just(new IdempotentExecutionResult(outcome, terminal));
    }
  }

  private static final class Fixture {
    private final CapturingExecutor executor = new CapturingExecutor();
    private final LoginOtpVerifyProtectedOperation operation =
        mock(LoginOtpVerifyProtectedOperation.class);
    private final LoginOtpVerifyExecutionBridge responses =
        mock(LoginOtpVerifyExecutionBridge.class);
    private final LoginIdempotencyIdentityFactory identities =
        mock(LoginIdempotencyIdentityFactory.class);
    private final LoginFingerprintFactory fingerprints = mock(LoginFingerprintFactory.class);
    private final SharedLoginOtpVerifyService service;

    private Fixture() {
      when(identities.create(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
      when(fingerprints.otpVerify()).thenReturn(mock(IdempotencyFingerprint.class));
      service =
          new SharedLoginOtpVerifyService(
              executor, operation, responses, identities, fingerprints, TTL);
    }
  }
}
