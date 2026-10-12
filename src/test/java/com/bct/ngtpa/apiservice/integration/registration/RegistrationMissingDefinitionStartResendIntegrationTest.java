package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.registration.RegistrationMessageDeliveryAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.DefaultIdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyCommands;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyOperationId;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyPort;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyRecord;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservation;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationToken;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyStatus;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.RegistrationOtpCreationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.SharedStartRegistrationService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpResultStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpResendProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpResendReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpResendSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.SharedResendRegistrationOtpService;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sub-stage II-2.e (decision D7). The Registration delivery adapter resolves its content from the
 * notice template registry while the START and RESEND chains are being assembled, so a missing
 * {@code registration-otp} definition throws {@link IllegalStateException} there. Neither workflow
 * handles that exception (START with the captcha gate off, and RESEND), and the shared executor
 * re-emits the original error. These tests pin that: the original error reaches the caller as an
 * error signal, no result is emitted (in particular no failed-delivery or system-failed result) and
 * nothing is sent.
 *
 * <p>START with the captcha gate ON turns the error into a {@code SYSTEM_FAILED} result inside the
 * protected operation; that path is not covered here. The state of the idempotency key and the
 * number of session updates are deliberately not asserted.
 */
class RegistrationMissingDefinitionStartResendIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
  private static final Duration WAIT = Duration.ofSeconds(10);
  private static final String MISSING_MESSAGE =
      "No notice-templates.definitions entry for registration-otp";

  // ----- START (captcha gate off) -----------------------------------------------------------

  @Test
  void startWithAMissingDefinitionFailsTheCallerWithTheOriginalErrorAndSendsNothing() {
    Registry registry = Registry.missing();
    MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
    SharedStartRegistrationService service = startService(messages(delivery, registry));

    StepVerifier.create(service.start(startCommand()))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(MISSING_MESSAGE))
        .verify(WAIT);

    assertThat(registry.calls.get()).isEqualTo(1);
    assertThat(registry.lastType.get()).isEqualTo(MessageDeliveryType.REGISTRATION_OTP);
    assertThat(registry.lastLocale.get()).isEqualTo("en");
    verifyNoInteractions(delivery);
  }

  @Test
  void startWithAConfiguredDefinitionReachesTheStartedResult() {
    Registry registry = Registry.configured();
    MessageDeliveryPort delivery = acceptingDelivery();
    RegistrationStartProtectedOperation operation = startOperation(messages(delivery, registry));

    StepVerifier.create(operation.execute(startCommand()))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(StartRegistrationResultStatus.STARTED))
        .expectComplete()
        .verify(WAIT);

    assertThat(registry.calls.get()).isEqualTo(1);
    assertThat(registry.lastType.get()).isEqualTo(MessageDeliveryType.REGISTRATION_OTP);
    verify(delivery).deliver(any());
  }

  // ----- RESEND -----------------------------------------------------------------------------

  @Test
  void resendWithAMissingDefinitionFailsTheCallerWithTheOriginalErrorAndSendsNothing() {
    Registry registry = Registry.missing();
    MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
    SharedResendRegistrationOtpService service = resendService(messages(delivery, registry));

    StepVerifier.create(service.resend(resendCommand()))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(MISSING_MESSAGE))
        .verify(WAIT);

    assertThat(registry.calls.get()).isEqualTo(1);
    assertThat(registry.lastType.get()).isEqualTo(MessageDeliveryType.REGISTRATION_OTP);
    assertThat(registry.lastChannel.get()).isEqualTo(MessageDeliveryChannel.EMAIL);
    assertThat(registry.lastLocale.get()).isEqualTo("en");
    verifyNoInteractions(delivery);
  }

  @Test
  void resendWithAConfiguredDefinitionReachesTheResentResult() {
    Registry registry = Registry.configured();
    MessageDeliveryPort delivery = acceptingDelivery();
    RegistrationOtpResendProtectedOperation operation =
        resendOperation(messages(delivery, registry));

    StepVerifier.create(operation.execute(resendCommand(), validContext()))
        .assertNext(
            result -> assertThat(result.status()).isEqualTo(ResendRegistrationOtpResultStatus.RESENT))
        .expectComplete()
        .verify(WAIT);

    assertThat(registry.calls.get()).isEqualTo(1);
    assertThat(registry.lastChannel.get()).isEqualTo(MessageDeliveryChannel.EMAIL);
    verify(delivery).deliver(any());
  }

  // ----- chains -----------------------------------------------------------------------------

  private static RegistrationMessagePort messages(
      MessageDeliveryPort delivery, NoticeTemplateRegistryPort registry) {
    return new RegistrationMessageDeliveryAdapter(delivery, registry);
  }

  private static MessageDeliveryPort acceptingDelivery() {
    MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
    when(delivery.deliver(any()))
        .thenReturn(
            Mono.just(new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    return delivery;
  }

  private static DefaultIdempotencyExecutor executor() {
    return new DefaultIdempotencyExecutor(
        new AcquiringPort(),
        () -> new IdempotencyReservationToken("reservationToken0123456789abcdefgh"),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static RegistrationStartProtectedOperation startOperation(
      RegistrationMessagePort messages) {
    return new RegistrationStartProtectedOperation(
        new RegistrationStartValidator(new MaskingService(new MaskingProperties())),
        new Lookup(),
        new Sessions(),
        sessionId -> Mono.just("csrf_token_safe"),
        new Otp(),
        messages,
        null,
        null,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static SharedStartRegistrationService startService(RegistrationMessagePort messages) {
    return new SharedStartRegistrationService(
        executor(),
        startOperation(messages),
        new RegistrationStartSharedExecutionBridge(
            new RegistrationStartReplayCodec(), mock(RegistrationStartProtectedReplayPort.class)),
        new RegistrationSharedIdempotencyIdentityFactory(),
        new RegistrationSharedFingerprintFactory());
  }

  private static RegistrationOtpResendProtectedOperation resendOperation(
      RegistrationMessagePort messages) {
    return new RegistrationOtpResendProtectedOperation(new Otp(), new Sessions(), messages);
  }

  private static SharedResendRegistrationOtpService resendService(
      RegistrationMessagePort messages) {
    return new SharedResendRegistrationOtpService(
        ignored -> Mono.just(validContext()),
        executor(),
        resendOperation(messages),
        new RegistrationOtpResendSharedExecutionBridge(new RegistrationOtpResendReplayCodec()),
        new RegistrationSharedIdempotencyIdentityFactory(),
        new RegistrationSharedFingerprintFactory());
  }

  // ----- commands and contexts --------------------------------------------------------------

  private static StartRegistrationCommand startCommand() {
    return new StartRegistrationCommand(
        new RegistrationIdempotencyKey("idem_abc123"),
        "GPFS",
        "1234567890",
        "31/01/1980",
        "member@example.com",
        null,
        "cap_secret",
        null,
        "en",
        "corr-secret",
        Duration.ofMinutes(5));
  }

  private static ResendRegistrationOtpCommand resendCommand() {
    return new ResendRegistrationOtpCommand(
        new AuthFlowSessionId("afs-shared-001"),
        "csrf-secret",
        null,
        null,
        new RegistrationIdempotencyKey("idem-shared-001"),
        "en",
        "request-001",
        Duration.ofMinutes(5));
  }

  private static RegistrationProtectedAuthFlowContextResult validContext() {
    return RegistrationProtectedAuthFlowContextResult.valid(
        "otp-old",
        "m****r@example.com",
        new RegistrationAuthFlowContext(
            MemberIdentityType.ID,
            "3024102",
            "04/04/1982",
            RegistrationContactType.EMAIL,
            "member@example.com"));
  }

  // ----- fakes ------------------------------------------------------------------------------

  /** Records what the adapter asks for; a missing definition throws like the real registry. */
  private static final class Registry implements NoticeTemplateRegistryPort {
    final boolean absent;
    final AtomicInteger calls = new AtomicInteger();
    final AtomicReference<MessageDeliveryType> lastType = new AtomicReference<>();
    final AtomicReference<MessageDeliveryChannel> lastChannel = new AtomicReference<>();
    final AtomicReference<String> lastLocale = new AtomicReference<>();

    private Registry(boolean absent) {
      this.absent = absent;
    }

    static Registry missing() {
      return new Registry(true);
    }

    static Registry configured() {
      return new Registry(false);
    }

    @Override
    public NoticeTemplateDefinition resolve(
        MessageDeliveryType messageType, MessageDeliveryChannel channel, String locale) {
      calls.incrementAndGet();
      lastType.set(messageType);
      lastChannel.set(channel);
      lastLocale.set(locale);
      if (absent) {
        throw new IllegalStateException(MISSING_MESSAGE);
      }
      return new NoticeTemplateDefinition(
          messageType,
          channel,
          "en",
          channel == MessageDeliveryChannel.EMAIL ? "Registration OTP" : null,
          "Code {code} valid {ttlValue} {ttlUnit}",
          Set.of("code", "ttlValue", "ttlUnit"),
          "minutes");
    }
  }

  /** Always grants the reservation, so the protected operation runs inside the real executor. */
  private static final class AcquiringPort implements IdempotencyPort {
    @Override
    public Mono<IdempotencyReservation> reserve(IdempotencyCommands.Reserve command) {
      return Mono.just(
          new IdempotencyReservation(
              IdempotencyReservationOutcome.ACQUIRED,
              IdempotencyStatus.PROCESSING,
              null,
              command.reservationToken()));
    }

    @Override
    public Mono<IdempotencyRecord> complete(IdempotencyCommands.Complete command) {
      return Mono.empty();
    }

    @Override
    public Mono<IdempotencyRecord> fail(IdempotencyCommands.Fail command) {
      return Mono.empty();
    }

    @Override
    public Mono<IdempotencyRecord> markUnknown(IdempotencyCommands.MarkUnknown command) {
      return Mono.empty();
    }

    @Override
    public Mono<Optional<IdempotencyRecord>> load(IdempotencyOperationId operationId) {
      return Mono.just(Optional.empty());
    }
  }

  private static final class Lookup implements RegistrationLookupPort {
    @Override
    public Mono<RegistrationLookupResult> searchMember(RegistrationLookupCommand command) {
      return Mono.just(
          new RegistrationLookupResult(
              RegistrationLookupStatus.ELIGIBLE,
              List.of(new RegistrationLookupAccount("OI", "JPM", null, null, null, null, null, Boolean.FALSE, null)),
              null));
    }
  }

  private static final class Sessions implements RegistrationSessionPort {
    @Override
    public Mono<String> createRegistrationSession(String masked, RegistrationContactType type) {
      return Mono.just("afs_session_for_cookie");
    }

    @Override
    public Mono<Void> updateOtpChallenge(
        String sid, String otpId, RegistrationContactType type, String masked) {
      return Mono.empty();
    }
  }

  private static final class Otp implements RegistrationOtpPort {
    @Override
    public Mono<RegistrationOtpCreationResult> createOtp(
        RegistrationContactType type, String destination) {
      return Mono.just(new RegistrationOtpCreationResult("otp_internal", "123456", 300, 60, 3));
    }

    @Override
    public Mono<RegistrationOtpCreationResult> resendOtp(
        String activeId, RegistrationContactType type, String destination) {
      return Mono.just(new RegistrationOtpCreationResult("otp-new", "123456", 300, 60, 4));
    }
  }
}
