package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend;

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
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpResendExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SharedLoginOtpResendServiceTest {
  private static final Duration TTL = Duration.ofSeconds(300);
  private static final ResendLoginOtpCommand COMMAND =
      new ResendLoginOtpCommand(
          "afs-secret", "csrf-secret", "idem-key-0001", null, null, "en", "req");

  @Test
  void commandUsesSharedIdentityFingerprintTtlAndConservativeClassifier() {
    Fixture f = new Fixture();
    StepVerifier.create(f.service.resend(COMMAND))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(ResendLoginOtpStatus.DUPLICATE_IN_PROGRESS))
        .verifyComplete();
    verify(f.identities)
        .create(LoginIdempotencyAction.OTP_RESEND, "afs-secret", "idem-key-0001");
    verify(f.fingerprints).otpResend("afs-secret");
    assertThat(f.executor.command.ttl()).isEqualTo(TTL);
    assertThat(f.executor.command.failureClassifier().classify(new RuntimeException("x")))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
    verifyNoInteractions(f.operation);
  }

  @Test
  void acquiredExecutesProtectedOperationAndRestoresOriginalResult() {
    Fixture f = new Fixture();
    var original = new ResendLoginOtpResult(ResendLoginOtpStatus.RESENT, "****4567", 300, 60, null);
    var response = response(200, Map.of());
    f.executor.invokeProtected = true;
    when(f.operation.resend(COMMAND)).thenReturn(Mono.just(original));
    when(f.responses.toTransientResponse(original)).thenReturn(response);
    when(f.responses.fromTransientResponse(response)).thenReturn(original);
    StepVerifier.create(f.service.resend(COMMAND)).expectNext(original).verifyComplete();
    verify(f.operation).resend(COMMAND);
  }

  @Test
  void completedReplayRestoresCooldownAndRetryAfterWithoutSideEffects() {
    Fixture f = new Fixture();
    var response = response(429, Map.of("Retry-After", "25"));
    var original =
        new ResendLoginOtpResult(ResendLoginOtpStatus.COOLDOWN_ACTIVE, "****4567", null, 60, 25);
    f.executor.outcome = IdempotencyReservationOutcome.COMPLETED_REPLAY;
    f.executor.terminal = response;
    when(f.responses.fromTransientResponse(response)).thenReturn(original);
    StepVerifier.create(f.service.resend(COMMAND)).expectNext(original).verifyComplete();
    verifyNoInteractions(f.operation);
  }

  @Test
  void mapsExistingLifecycleOutcomesWithoutProtectedExecution() {
    Fixture f = new Fixture();
    assertMapped(
        f,
        IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
        ResendLoginOtpStatus.DUPLICATE_IN_PROGRESS);
    assertMapped(
        f,
        IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
        ResendLoginOtpStatus.KEY_REUSED);
    assertMapped(
        f, IdempotencyReservationOutcome.FAILED_EXISTING, ResendLoginOtpStatus.FAILED_EXISTING);
    assertMapped(
        f,
        IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING,
        ResendLoginOtpStatus.UNKNOWN_OUTCOME);
    verifyNoInteractions(f.operation);
  }

  @Test
  void protectedFailurePropagatesAcrossExecutorBoundary() {
    Fixture f = new Fixture();
    f.executor.invokeProtected = true;
    when(f.operation.resend(COMMAND))
        .thenReturn(Mono.error(new IllegalStateException("dispatch boundary")));
    StepVerifier.create(f.service.resend(COMMAND)).expectErrorMessage("dispatch boundary").verify();
  }

  private static void assertMapped(
      Fixture f, IdempotencyReservationOutcome outcome, ResendLoginOtpStatus expected) {
    f.executor.outcome = outcome;
    f.executor.terminal = null;
    StepVerifier.create(f.service.resend(COMMAND))
        .assertNext(r -> assertThat(r.status()).isEqualTo(expected))
        .verifyComplete();
  }

  private static HydratedIdempotentResponse response(int status, Map<String, String> headers) {
    return new HydratedIdempotentResponse(status, "application/json", "{}", headers);
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
    private final ResendLoginOtpUseCase operation = mock(ResendLoginOtpUseCase.class);
    private final LoginOtpResendExecutionBridge responses =
        mock(LoginOtpResendExecutionBridge.class);
    private final LoginIdempotencyIdentityFactory identities =
        mock(LoginIdempotencyIdentityFactory.class);
    private final LoginFingerprintFactory fingerprints =
        mock(LoginFingerprintFactory.class);
    private final SharedLoginOtpResendService service;

    private Fixture() {
      when(identities.create(any(), any(), any())).thenReturn(mock(IdempotencyOperationId.class));
      when(fingerprints.otpResend(any())).thenReturn(mock(IdempotencyFingerprint.class));
      service =
          new SharedLoginOtpResendService(
              executor, operation, responses, identities, fingerprints, TTL);
    }
  }
}
