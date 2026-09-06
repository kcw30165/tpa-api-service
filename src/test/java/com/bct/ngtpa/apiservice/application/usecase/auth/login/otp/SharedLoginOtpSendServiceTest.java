package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFailureClassification;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyFingerprint;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpSendExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SharedLoginOtpSendServiceTest {
  private static final Duration TTL = Duration.ofSeconds(300);
  private static final SendLoginOtpCommand COMMAND =
      new SendLoginOtpCommand("afs", "csrf", "dst", "idem", null, null, "en", "req");

  @Test
  void commandUsesSharedIdentityFingerprintTtlAndConservativeClassifier() {
    Fixture f = new Fixture();
    StepVerifier.create(f.service.send(COMMAND))
        .assertNext(r -> assertThat(r.status()).isEqualTo(SendLoginOtpStatus.DUPLICATE_IN_PROGRESS))
        .verifyComplete();
    verify(f.identities).create(LoginIdempotencyAction.OTP_SEND, "afs", "idem");
    verify(f.fingerprints).otpSend("afs", "dst");
    assertThat(f.executor.command.ttl()).isEqualTo(TTL);
    assertThat(f.executor.command.failureClassifier().classify(new RuntimeException("x")))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verifyNoInteractions(f.operation);
  }

  @Test
  void acquiredInvokesProtectedOperationOnceAndRestoresOriginalResult() {
    Fixture f = new Fixture();
    var sent = new SendLoginOtpResult(SendLoginOtpStatus.SENT, "****4567", 300, 60, null);
    var response = response("sent");
    f.executor.invokeProtected = true;
    when(f.operation.send(COMMAND)).thenReturn(Mono.just(sent));
    when(f.responses.toTransientResponse(sent)).thenReturn(response);
    when(f.responses.fromTransientResponse(response)).thenReturn(sent);
    StepVerifier.create(f.service.send(COMMAND)).expectNext(sent).verifyComplete();
    verify(f.operation).send(COMMAND);
  }

  @Test
  void completedReplayReturnsOriginalPublicStatusWithoutProtectedExecution() {
    Fixture f = new Fixture();
    var replay = response("replay");
    var sent = new SendLoginOtpResult(SendLoginOtpStatus.SENT, "****4567", 300, 60, null);
    f.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    f.executor.terminal = replay;
    when(f.responses.fromTransientResponse(replay)).thenReturn(sent);
    StepVerifier.create(f.service.send(COMMAND)).expectNext(sent).verifyComplete();
    verifyNoInteractions(f.operation);
  }

  @Test
  void mapsExistingLifecycleOutcomesWithoutProtectedExecution() {
    Fixture f = new Fixture();
    assertMapped(
        f,
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
        SendLoginOtpStatus.DUPLICATE_IN_PROGRESS);
    assertMapped(
        f,
        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
        SendLoginOtpStatus.KEY_REUSED);
    assertMapped(
        f, IdempotencyReservationOutcome.FAILED_EXISTING, SendLoginOtpStatus.FAILED_EXISTING);
    assertMapped(
        f,
        IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
        SendLoginOtpStatus.UNKNOWN_OUTCOME);
    verifyNoInteractions(f.operation);
  }

  @Test
  void protectedFailurePropagatesAcrossExecutorBoundary() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    when(f.operation.send(COMMAND))
        .thenReturn(Mono.error(new IllegalStateException("delivery boundary")));
    StepVerifier.create(f.service.send(COMMAND)).expectErrorMessage("delivery boundary").verify();
  }

  private static void assertMapped(
      Fixture f, IdempotencyReservationOutcome outcome, SendLoginOtpStatus expected) {
    f.executor.outcome = outcome;
    f.executor.terminal = null;
    StepVerifier.create(f.service.send(COMMAND))
        .assertNext(r -> assertThat(r.status()).isEqualTo(expected))
        .verifyComplete();
  }

  private static HydratedIdempotentResponse response(String body) {
    return new HydratedIdempotentResponse(200, "application/json", body, Map.of());
  }

  private static final class CapturingExecutor implements IdempotencyExecutor {
    private IdempotentExecutionCommand command;
    private IdempotencyReservationOutcome outcome =
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS;
    private HydratedIdempotentResponse terminal;
    private boolean invokeProtected;

    @Override
    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand supplied) {
      command = supplied;
      if (invokeProtected) {
        return supplied
            .protectedOperation()
            .get()
            .map(r -> new IdempotentExecutionResult(IdempotencyReservationOutcome.ACQUIRED, r));
      }
      return Mono.just(new IdempotentExecutionResult(outcome, terminal));
    }
  }

  private static final class Fixture {
    private final CapturingExecutor executor = new CapturingExecutor();
    private final SendLoginOtpUseCase operation = mock(SendLoginOtpUseCase.class);
    private final LoginOtpSendExecutionBridge responses =
        mock(LoginOtpSendExecutionBridge.class);
    private final LoginIdempotencyIdentityFactory identities =
        mock(LoginIdempotencyIdentityFactory.class);
    private final LoginFingerprintFactory fingerprints =
        mock(LoginFingerprintFactory.class);
    private final SharedLoginOtpSendService service;

    private Fixture() {
      when(identities.create(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
      when(fingerprints.otpSend(any(), any())).thenReturn(mock(IdempotencyFingerprint.class));
      service =
          new SharedLoginOtpSendService(
              executor, operation, responses, identities, fingerprints, TTL);
    }
  }
}
