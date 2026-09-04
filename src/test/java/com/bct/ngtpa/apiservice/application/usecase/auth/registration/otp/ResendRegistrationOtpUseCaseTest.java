package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ResendRegistrationOtpUseCaseTest {
  @Test
  void acquiredExecutesAndCapturesSafeTerminalResult() {
    Fixture fixture = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    ResendRegistrationOtpResult result = fixture.service.resend(command()).block();
    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.RESENT);
    assertThat(fixture.otp.calls).isEqualTo(1);
    assertThat(fixture.session.updates).isEqualTo(1);
    assertThat(fixture.message.deliveries).isEqualTo(1);
    assertThat(fixture.executor.snapshot.serializedSafeBody())
        .contains("otp-resend", "VERIFY_OTP", "m****r@example.com")
        .doesNotContain(
            "member@example.com", "123456", "otp-old", "otp-new", "afs-secret", "csrf-secret");
    assertThat(fixture.executor.command.operationId().scope().value()).isNotEqualTo("afs-secret");
    assertThat(fixture.executor.command.fingerprint().version()).isEqualTo(1);
  }

  @Test
  void invalidContextStopsBeforeSharedExecutor() {
    Fixture fixture = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    fixture.context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);
    assertThat(fixture.service.resend(command()).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.CSRF_INVALID);
    assertThat(fixture.executor.calls).isZero();
    assertThat(fixture.otp.calls).isZero();
  }

  @Test
  void nonExecutableOutcomesDoNotInvokeProtectedWorkflow() {
    for (IdempotencyReservationOutcome outcome :
        List.of(
            IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS,
            IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
            IdempotencyReservationOutcome.FAILED_EXISTING,
            IdempotencyReservationOutcome.UNKNOWN_OUTCOME_EXISTING)) {
      Fixture fixture = new Fixture(outcome);
      fixture.service.resend(command()).block();
      assertThat(fixture.otp.calls).isZero();
      assertThat(fixture.session.updates).isZero();
      assertThat(fixture.message.deliveries).isZero();
    }
  }

  @Test
  void knownRateLimitAndDeliveryRejectionAreCapturedTerminalResults() {
    Fixture limited = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    limited.otp.error = new RegistrationOtpSendRateLimitedException();
    assertThat(limited.service.resend(command()).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.SEND_RATE_LIMITED);
    assertThat(limited.executor.snapshot).isNotNull();
    Fixture rejected = new Fixture(IdempotencyReservationOutcome.ACQUIRED);
    rejected.message.accepted = false;
    assertThat(rejected.service.resend(command()).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED);
    assertThat(rejected.executor.snapshot).isNotNull();
  }

  private static ResendRegistrationOtpCommand command() {
    return new ResendRegistrationOtpCommand(
        new AuthFlowSessionId("afs-secret"),
        "csrf-secret",
        null,
        null,
        new RegistrationIdempotencyKey("idem-secret"),
        "en",
        "corr",
        Duration.ofMinutes(5));
  }

  private static final class Fixture {
    final Context context = new Context();
    final Executor executor;
    final Otp otp = new Otp();
    final Session session = new Session();
    final Message message = new Message();
    final SharedResendRegistrationOtpService service;

    Fixture(IdempotencyReservationOutcome outcome) {
      executor = new Executor(outcome);
      var bridge =
          new RegistrationOtpResendSharedExecutionBridge(new RegistrationOtpResendReplayCodec());
      service =
          new SharedResendRegistrationOtpService(
              context,
              executor,
              new RegistrationOtpResendProtectedOperation(otp, session, message),
              bridge,
              new RegistrationSharedIdempotencyIdentityFactory(),
              new RegistrationSharedFingerprintFactory());
    }
  }

  private static final class Executor implements IdempotencyExecutor {
    final IdempotencyReservationOutcome outcome;
    int calls;
    IdempotentExecutionCommand command;
    IdempotentTerminalResponseSnapshot snapshot;

    Executor(IdempotencyReservationOutcome outcome) {
      this.outcome = outcome;
    }

    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      calls++;
      this.command = command;
      if (outcome == IdempotencyReservationOutcome.ACQUIRED) {
        return command
            .protectedOperation()
            .get()
            .flatMap(command.responseCapture()::capture)
            .map(
                captured -> {
                  snapshot = captured.snapshot();
                  return new IdempotentExecutionResult(outcome, captured.response());
                });
      }
      return Mono.just(new IdempotentExecutionResult(outcome, null));
    }
  }

  private static final class Context implements RegistrationProtectedAuthFlowContextLoader {
    RegistrationProtectedAuthFlowContextResult result =
        RegistrationProtectedAuthFlowContextResult.valid(
            "otp-old",
            "m****r@example.com",
            new RegistrationAuthFlowContext(
                MemberIdentityType.ID,
                "3024102",
                "04/04/1982",
                RegistrationContactType.EMAIL,
                "member@example.com"));

    public Mono<RegistrationProtectedAuthFlowContextResult> load(
        RegistrationProtectedAuthFlowContextCommand command) {
      return Mono.just(result);
    }
  }

  private static final class Otp implements RegistrationOtpPort {
    int calls;
    RuntimeException error;

    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<RegistrationOtpCreationResult> resendOtp(
        String active, RegistrationContactType type, String destination) {
      calls++;
      return error == null
          ? Mono.just(new RegistrationOtpCreationResult("otp-new", "123456", 300, 60, 4))
          : Mono.error(error);
    }
  }

  private static final class Session implements RegistrationSessionPort {
    int updates;

    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<Void> updateOtpChallenge(
        String sid, String otp, RegistrationContactType type, String masked) {
      updates++;
      return Mono.empty();
    }
  }

  private static final class Message implements RegistrationMessagePort {
    int deliveries;
    boolean accepted = true;

    public Mono<Boolean> deliverRegistrationOtp(
        RegistrationContactType type,
        String recipient,
        String code,
        int ttl,
        String language,
        String correlation) {
      deliveries++;
      return Mono.just(accepted);
    }
  }
}
