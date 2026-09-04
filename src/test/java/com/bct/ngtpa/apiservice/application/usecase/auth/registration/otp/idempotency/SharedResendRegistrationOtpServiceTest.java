package com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SharedResendRegistrationOtpServiceTest {
  @Test
  void invalidContextStopsBeforeSharedExecutor() {
    AtomicInteger executions = new AtomicInteger();
    var service =
        service(
            command ->
                Mono.just(
                    RegistrationProtectedAuthFlowContextResult.invalid(
                        RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID)),
            sharedCommand -> {
              executions.incrementAndGet();
              return Mono.error(new AssertionError("executor must not be called"));
            });
    assertThat(service.resend(command()).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.CSRF_INVALID);
    assertThat(executions).hasValue(0);
  }

  @Test
  void duplicateDoesNotInvokeProtectedOperation() {
    AtomicInteger protectedCalls = new AtomicInteger();
    IdempotencyExecutor executor =
        sharedCommand ->
            Mono.just(
                new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, null));
    var service = service(validContext(), executor, protectedCalls);
    assertThat(service.resend(command()).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS);
    assertThat(protectedCalls).hasValue(0);
  }

  private static SharedResendRegistrationOtpService service(
      RegistrationProtectedAuthFlowContextLoader loader, IdempotencyExecutor executor) {
    return service(loader, executor, new AtomicInteger());
  }

  private static SharedResendRegistrationOtpService service(
      RegistrationProtectedAuthFlowContextLoader loader,
      IdempotencyExecutor executor,
      AtomicInteger protectedCalls) {
    var operation =
        new RegistrationOtpResendProtectedOperation(
            new NoopOtpPort(protectedCalls), new NoopSessionPort(), new NoopMessagePort());
    return new SharedResendRegistrationOtpService(
        loader,
        executor,
        operation,
        new RegistrationOtpResendSharedExecutionBridge(new RegistrationOtpResendReplayCodec()),
        new RegistrationSharedIdempotencyIdentityFactory(),
        new RegistrationSharedFingerprintFactory());
  }

  private static RegistrationProtectedAuthFlowContextLoader validContext() {
    return ignored ->
        Mono.just(
            RegistrationProtectedAuthFlowContextResult.valid(
                "otp-old",
                "m****r@example.com",
                new RegistrationAuthFlowContext(
                    MemberIdentityType.ID,
                    "3024102",
                    "04/04/1982",
                    RegistrationContactType.EMAIL,
                    "member@example.com")));
  }

  private static ResendRegistrationOtpCommand command() {
    return new ResendRegistrationOtpCommand(
        new AuthFlowSessionId("afs-shared-001"),
        "csrf-secret",
        null,
        null,
        new com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey(
            "idem-shared-001"),
        "en",
        "request-001",
        Duration.ofMinutes(5));
  }

  private record NoopOtpPort(AtomicInteger calls)
      implements com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort {
    public Mono<
            com.bct.ngtpa.apiservice.application.usecase.auth.registration
                .RegistrationOtpCreationResult>
        createOtp(RegistrationContactType type, String destination) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<
            com.bct.ngtpa.apiservice.application.usecase.auth.registration
                .RegistrationOtpCreationResult>
        resendOtp(String activeId, RegistrationContactType type, String destination) {
      calls.incrementAndGet();
      return Mono.error(new AssertionError("protected operation must not execute"));
    }
  }

  private static final class NoopSessionPort
      implements com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort {
    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<Void> updateOtpChallenge(
        String sid, String otpId, RegistrationContactType type, String masked) {
      return Mono.empty();
    }
  }

  private static final class NoopMessagePort
      implements com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort {
    public Mono<Boolean> deliverRegistrationOtp(
        RegistrationContactType type,
        String recipient,
        String code,
        int ttl,
        String language,
        String correlation) {
      return Mono.just(true);
    }
  }
}
