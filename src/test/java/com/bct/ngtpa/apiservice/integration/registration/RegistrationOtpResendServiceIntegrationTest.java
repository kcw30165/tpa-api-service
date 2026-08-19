package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.*;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

/** Executes the real resend orchestration with controlled external ports. */
@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationOtpResendServiceIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
  private final ContextLoader context = new ContextLoader();
  private final IdempotencyStore idempotency = new IdempotencyStore();
  private final OtpGateway otp = new OtpGateway();
  private final SessionGateway session = new SessionGateway();
  private final MessageGateway message = new MessageGateway();
  private final ResendRegistrationOtpService service =
      new ResendRegistrationOtpService(
          context,
          new HashGenerator(),
          idempotency,
          otp,
          session,
          message,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void reset() {
    context.reset();
    idempotency.reset();
    otp.reset();
    session.reset();
    message.reset();
  }

  @Test
  @RegistrationTestCase("TC-038")
  void completedReplayCreatesNoAdditionalChallengeDeliveryOrSessionMutation() {
    ResendRegistrationOtpResult first = service.resend(command("idem-replay")).block();
    assertThat(first.status()).isEqualTo(ResendRegistrationOtpResultStatus.RESENT);
    int otpCalls = otp.calls;
    int sessionUpdates = session.updates;
    int deliveries = message.deliveries;

    ResendRegistrationOtpResult replay = service.resend(command("idem-replay")).block();
    assertThat(replay.status()).isEqualTo(ResendRegistrationOtpResultStatus.COMPLETED_REPLAY);
    assertThat(replay.replaySnapshot()).isNotNull();
    assertThat(otp.calls).isEqualTo(otpCalls);
    assertThat(session.updates).isEqualTo(sessionUpdates);
    assertThat(message.deliveries).isEqualTo(deliveries);
  }

  @Test
  @RegistrationTestCase("TC-039")
  void missingOrInvalidSessionStopsBeforeIdempotencyAndOtpReplacement() {
    context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.MISSING_SESSION);
    ResendRegistrationOtpResult result = service.resend(command("idem-session")).block();
    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
    assertThat(idempotency.starts).isZero();
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-040")
  void invalidCsrfStopsBeforeIdempotencyAndOtpReplacement() {
    context.result =
        RegistrationProtectedAuthFlowContextResult.invalid(
            RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID);
    ResendRegistrationOtpResult result = service.resend(command("idem-csrf")).block();
    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.CSRF_INVALID);
    assertThat(idempotency.starts).isZero();
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-041")
  void rejectedOriginFromProtectedContextStopsAllResendSideEffects() {
    context.rejectDisallowedOrigin = true;
    ResendRegistrationOtpResult result =
        service.resend(command("idem-origin", "https://attacker.invalid", null)).block();
    assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.INVALID_AUTH_FLOW);
    assertThat(context.lastOrigin).isEqualTo("https://attacker.invalid");
    assertThat(idempotency.starts).isZero();
    assertNoOperationalSideEffects();
  }

  @Test
  @RegistrationTestCase("TC-043")
  void providerFailureReturnsSafeCanonicalErrorAndMarksIdempotencyFailed() {
    message.accepted = false;
    ResendRegistrationOtpResult result = service.resend(command("idem-provider")).block();
    assertThat(result.status())
        .isEqualTo(ResendRegistrationOtpResultStatus.MESSAGE_DELIVERY_FAILED);
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.registration.message.deliveryFailed"));
    assertThat(idempotency.failed).isEqualTo(1);
    assertThat(result.toString())
        .doesNotContain("123456", "member@example.com", "csrf-secret", "otp-new");
  }

  private ResendRegistrationOtpCommand command(String key) {
    return command(key, "https://portal.test", "https://portal.test/registration");
  }

  private ResendRegistrationOtpCommand command(String key, String origin, String referer) {
    return new ResendRegistrationOtpCommand(
        new AuthFlowSessionId("afs-safe-001"),
        "csrf-secret",
        origin,
        referer,
        new RegistrationIdempotencyKey(key),
        "en",
        "req-003-002",
        Duration.ofMinutes(5));
  }

  private void assertNoOperationalSideEffects() {
    assertThat(otp.calls).isZero();
    assertThat(session.updates).isZero();
    assertThat(message.deliveries).isZero();
  }

  static final class ContextLoader implements RegistrationProtectedAuthFlowContextLoader {
    RegistrationProtectedAuthFlowContextResult result;
    boolean rejectDisallowedOrigin;
    String lastOrigin;

    void reset() {
      rejectDisallowedOrigin = false;
      lastOrigin = null;
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
      lastOrigin = command.origin();
      if (rejectDisallowedOrigin && !"https://portal.test".equals(command.origin())) {
        return Mono.just(
            RegistrationProtectedAuthFlowContextResult.invalid(
                RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS));
      }
      return Mono.just(result);
    }
  }

  static final class HashGenerator implements RegistrationRequestHashGenerator {
    public RegistrationRequestHash hash(RegistrationIdempotencyAction action, List<String> parts) {
      return new RegistrationRequestHash("1".repeat(64));
    }
  }

  static final class IdempotencyStore implements RegistrationIdempotencyPort {
    private final Map<String, RegistrationIdempotencyRecord> records = new HashMap<>();
    int starts;
    int failed;

    void reset() {
      records.clear();
      starts = 0;
      failed = 0;
    }

    public Mono<RegistrationIdempotencyStartResult> startProcessing(
        RegistrationIdempotencyStartCommand command) {
      starts++;
      String key = command.idempotencyKey().value();
      RegistrationIdempotencyRecord existing = records.get(key);
      if (existing != null) {
        return Mono.just(
            new RegistrationIdempotencyStartResult(
                RegistrationIdempotencyStartOutcome.COMPLETED_REPLAY, existing));
      }
      RegistrationIdempotencyRecord processing =
          record(
              command.idempotencyKey(),
              command.requestHash(),
              RegistrationIdempotencyStatus.PROCESSING,
              null);
      records.put(key, processing);
      return Mono.just(
          new RegistrationIdempotencyStartResult(
              RegistrationIdempotencyStartOutcome.STARTED, processing));
    }

    public Mono<RegistrationIdempotencyRecord> markCompleted(
        RegistrationIdempotencyCompleteCommand command) {
      RegistrationIdempotencyRecord completed =
          record(
              command.idempotencyKey(),
              command.requestHash(),
              RegistrationIdempotencyStatus.COMPLETED,
              command.responseSnapshot());
      records.put(command.idempotencyKey().value(), completed);
      return Mono.just(completed);
    }

    public Mono<RegistrationIdempotencyRecord> markFailed(
        RegistrationIdempotencyFailCommand command) {
      failed++;
      return Mono.just(records.get(command.idempotencyKey().value()));
    }

    public Mono<Optional<RegistrationIdempotencyRecord>> load(
        RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) {
      return Mono.just(Optional.ofNullable(records.get(key.value())));
    }

    public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) {
      records.remove(key.value());
      return Mono.empty();
    }

    private RegistrationIdempotencyRecord record(
        RegistrationIdempotencyKey key,
        RegistrationRequestHash hash,
        RegistrationIdempotencyStatus status,
        RegistrationIdempotencyResponseSnapshot snapshot) {
      return new RegistrationIdempotencyRecord(
          RegistrationIdempotencyAction.OTP_RESEND,
          key,
          hash,
          status,
          snapshot,
          NOW,
          NOW,
          NOW.plusSeconds(300));
    }
  }

  static final class OtpGateway implements RegistrationOtpPort {
    int calls;

    void reset() {
      calls = 0;
    }

    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      throw new UnsupportedOperationException();
    }

    public Mono<RegistrationOtpCreationResult> resendOtp(
        String activeId, RegistrationContactType type, String destination) {
      calls++;
      return Mono.just(new RegistrationOtpCreationResult("otp-new", "123456", 300, 60, 4));
    }
  }

  static final class SessionGateway implements RegistrationSessionPort {
    int updates;

    void reset() {
      updates = 0;
    }

    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      throw new UnsupportedOperationException();
    }

    public Mono<Void> updateOtpChallenge(
        String sid, String otpId, RegistrationContactType type, String masked) {
      updates++;
      return Mono.empty();
    }
  }

  static final class MessageGateway implements RegistrationMessagePort {
    int deliveries;
    boolean accepted;

    void reset() {
      deliveries = 0;
      accepted = true;
    }

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
