package com.bct.ngtpa.apiservice.integration.registration;

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
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.*;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationOtpResendServiceIntegrationTest {
  private final Context context = new Context();
  private final StatefulExecutor idempotency = new StatefulExecutor();
  private final Otp otp = new Otp();
  private final Session session = new Session();
  private final Message message = new Message();
  private final SharedResendRegistrationOtpService service =
      new SharedResendRegistrationOtpService(
          context,
          idempotency,
          new RegistrationOtpResendProtectedOperation(otp, session, message),
          new RegistrationOtpResendSharedExecutionBridge(new RegistrationOtpResendReplayCodec()),
          new RegistrationSharedIdempotencyIdentityFactory(),
          new RegistrationSharedFingerprintFactory());

  @BeforeEach
  void reset() {
    context.reset();
    idempotency.clear();
    otp.calls = 0;
    session.updates = 0;
    message.deliveries = 0;
    message.accepted = true;
  }

  @Test
  @RegistrationTestCase("TC-038")
  void completedReplayReturnsOriginalResultWithoutAdditionalEffects() {
    ResendRegistrationOtpResult first = service.resend(command("idem-replay")).block();
    int otpCalls = otp.calls;
    int updates = session.updates;
    int deliveries = message.deliveries;
    ResendRegistrationOtpResult replay = service.resend(command("idem-replay")).block();
    assertThat(first.status()).isEqualTo(ResendRegistrationOtpResultStatus.RESENT);
    assertThat(replay.status()).isEqualTo(ResendRegistrationOtpResultStatus.RESENT);
    assertThat(replay.maskedDestination()).isEqualTo(first.maskedDestination());
    assertThat(replay.expiresInSeconds()).isEqualTo(first.expiresInSeconds());
    assertThat(otp.calls).isEqualTo(otpCalls);
    assertThat(session.updates).isEqualTo(updates);
    assertThat(message.deliveries).isEqualTo(deliveries);
  }

  @Test
  @RegistrationTestCase("TC-039")
  void invalidSessionStopsBeforeSharedExecutor() {
    context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION);
    assertThat(service.resend(command("idem-session")).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
    assertThat(idempotency.calls).isZero();
    assertNoEffects();
  }

  @Test
  @RegistrationTestCase("TC-040")
  void invalidCsrfStopsBeforeSharedExecutor() {
    context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);
    assertThat(service.resend(command("idem-csrf")).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.CSRF_INVALID);
    assertThat(idempotency.calls).isZero();
    assertNoEffects();
  }

  @Test
  @RegistrationTestCase("TC-041")
  void rejectedOriginStopsAllEffects() {
    context.rejectOrigin = true;
    assertThat(service.resend(command("idem-origin", "https://attacker.invalid")).block().status())
        .isEqualTo(ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
    assertThat(idempotency.calls).isZero();
    assertNoEffects();
  }

  @Test
  @RegistrationTestCase("TC-043")
  void providerRejectionIsCompletedAndReplayable() {
    message.accepted = false;
    ResendRegistrationOtpResult first = service.resend(command("idem-provider")).block();
    int deliveries = message.deliveries;
    ResendRegistrationOtpResult replay = service.resend(command("idem-provider")).block();
    assertThat(first.status()).isEqualTo(ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED);
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(idempotency.completed).isEqualTo(1);
    assertThat(message.deliveries).isEqualTo(deliveries);
  }

  private ResendRegistrationOtpCommand command(String key) {
    return command(key, "https://portal.test");
  }

  private ResendRegistrationOtpCommand command(String key, String origin) {
    return new ResendRegistrationOtpCommand(
        new AuthFlowSessionId("afs-safe-001"),
        "csrf-secret",
        origin,
        "https://portal.test/registration",
        new RegistrationIdempotencyKey(key),
        "en",
        "req-003-002",
        Duration.ofMinutes(5));
  }

  private void assertNoEffects() {
    assertThat(otp.calls).isZero();
    assertThat(session.updates).isZero();
    assertThat(message.deliveries).isZero();
  }

  static final class StatefulExecutor implements IdempotencyExecutor {
    final Map<IdempotencyOperationId, Stored> records = new ConcurrentHashMap<>();
    int calls;
    int completed;

    public Mono<IdempotentExecutionResult> execute(IdempotentExecutionCommand command) {
      calls++;
      Stored existing = records.get(command.operationId());
      if (existing != null) {
        if (!existing.fingerprint.equals(command.fingerprint()))
          return Mono.just(
              new IdempotentExecutionResult(
                  IdempotencyReservationOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST, null));
        return command
            .replayResolver()
            .hydrate(existing.snapshot)
            .map(
                r ->
                    new IdempotentExecutionResult(
                        IdempotencyReservationOutcome.COMPLETED_REPLAY, r));
      }
      return command
          .protectedOperation()
          .get()
          .flatMap(command.responseCapture()::capture)
          .map(
              c -> {
                records.put(command.operationId(), new Stored(command.fingerprint(), c.snapshot()));
                completed++;
                return new IdempotentExecutionResult(
                    IdempotencyReservationOutcome.ACQUIRED, c.response());
              });
    }

    void clear() {
      records.clear();
      calls = 0;
      completed = 0;
    }
  }

  record Stored(IdempotencyFingerprint fingerprint, IdempotentTerminalResponseSnapshot snapshot) {}

  static final class Context implements RegistrationProtectedAuthFlowContextLoader {
    RegistrationProtectedAuthFlowContextResult result;
    boolean rejectOrigin;

    void reset() {
      rejectOrigin = false;
      result =
          RegistrationProtectedAuthFlowContextResult.valid(
              "otp-old",
              "m****r@example.com",
              new RegistrationAuthFlowContext(
                  MemberIdentityType.ID,
                  "3024102",
                  "04/04/1982",
                  RegistrationContactType.EMAIL,
                  "member@example.com"));
    }

    public Mono<RegistrationProtectedAuthFlowContextResult> load(
        RegistrationProtectedAuthFlowContextCommand command) {
      return Mono.just(
          rejectOrigin
              ? RegistrationProtectedAuthFlowContextResult.invalid(
                  RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS)
              : result);
    }
  }

  static final class Otp implements RegistrationOtpPort {
    int calls;

    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      return Mono.error(new UnsupportedOperationException());
    }

    public Mono<RegistrationOtpCreationResult> resendOtp(
        String active, RegistrationContactType type, String destination) {
      calls++;
      return Mono.just(new RegistrationOtpCreationResult("otp-new", "123456", 300, 60, 4));
    }
  }

  static final class Session implements RegistrationSessionPort {
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

  static final class Message implements RegistrationMessagePort {
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
