package com.bct.ngtpa.apiservice.adapter.out.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Registration OTP delivery through the notice template registry (solution design section 8.6,
 * decisions D6 and D7). The registry is a hand-written fake and the delivery port is a mock.
 */
class RegistrationMessageDeliveryAdapterTest {

  private static final String SMS_BODY = "SMS {code} {ttlValue}{ttlUnit}";
  private static final String EMAIL_BODY = "Email {code} {ttlValue} {ttlUnit}";

  private final MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);
  private final FakeRegistry registry = new FakeRegistry();

  // ----- constructor guards ----------------------------------------------------

  @Test
  void constructorRejectsNullMessageDeliveryPort() {
    assertThatThrownBy(() -> new RegistrationMessageDeliveryAdapter(null, registry))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("messageDeliveryPort");
  }

  @Test
  void constructorRejectsNullNoticeTemplateRegistryPort() {
    assertThatThrownBy(() -> new RegistrationMessageDeliveryAdapter(delivery, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("noticeTemplateRegistryPort");
  }

  // ----- content comes from the registry, resolved per request -----------------

  @Test
  void mobileContactResolvesTheSmsDefinitionAndSendsItsBodyWithoutASubject() {
    registry.sms = definition(MessageDeliveryChannel.SMS, "zh_HK", null, SMS_BODY, "分鐘");
    deliveryReturns(delivery, MessageDeliveryStatus.ACCEPTED);

    StepVerifier.create(
            newAdapter(delivery)
                .deliverRegistrationOtp(
                    RegistrationContactType.MOBILE, "91234567", "123456", 300, "zh-HK", "req-1"))
        .expectNext(true)
        .verifyComplete();

    assertThat(registry.resolveCalls).isEqualTo(1);
    assertThat(registry.lastType).isEqualTo(MessageDeliveryType.REGISTRATION_OTP);
    assertThat(registry.lastChannel).isEqualTo(MessageDeliveryChannel.SMS);
    assertThat(registry.lastLocale).isEqualTo("zh-HK");
    MessageDeliveryCommand command = sentCommand(delivery);
    assertThat(command.messageType()).isEqualTo(MessageDeliveryType.REGISTRATION_OTP);
    assertThat(command.scenario()).isEqualTo(MessageDeliveryScenario.REGISTRATION);
    assertThat(command.channel()).isEqualTo(MessageDeliveryChannel.SMS);
    assertThat(command.recipient()).isEqualTo("91234567");
    assertThat(command.subject()).isNull();
    assertThat(command.body()).isEqualTo(SMS_BODY);
    assertThat(command.templateCode()).isEqualTo("registration-otp");
    assertThat(command.locale()).isEqualTo("zh-HK");
    assertThat(command.correlationId()).isEqualTo("req-1");
    assertThat(command.templateVariables())
        .isEqualTo(Map.of("code", "123456", "ttlValue", "5", "ttlUnit", "分鐘"));
  }

  @Test
  void emailContactResolvesTheEmailDefinitionAndSendsItsSubjectAndBody() {
    registry.email =
        definition(MessageDeliveryChannel.EMAIL, "en", "Subject", EMAIL_BODY, "minutes");
    deliveryReturns(delivery, MessageDeliveryStatus.ACCEPTED);

    StepVerifier.create(
            newAdapter(delivery)
                .deliverRegistrationOtp(
                    RegistrationContactType.EMAIL,
                    "user@example.com",
                    "123456",
                    300,
                    "en",
                    "req-2"))
        .expectNext(true)
        .verifyComplete();

    assertThat(registry.lastChannel).isEqualTo(MessageDeliveryChannel.EMAIL);
    assertThat(registry.lastLocale).isEqualTo("en");
    MessageDeliveryCommand command = sentCommand(delivery);
    assertThat(command.channel()).isEqualTo(MessageDeliveryChannel.EMAIL);
    assertThat(command.recipient()).isEqualTo("user@example.com");
    assertThat(command.subject()).isEqualTo("Subject");
    assertThat(command.body()).isEqualTo(EMAIL_BODY);
    assertThat(command.templateVariables())
        .isEqualTo(Map.of("code", "123456", "ttlValue", "5", "ttlUnit", "minutes"));
  }

  @Test
  void aNullContactTypeRoutesToEmailLikeAnyContactTypeOtherThanMobile() {
    deliveryReturns(delivery, MessageDeliveryStatus.ACCEPTED);

    newAdapter(delivery)
        .deliverRegistrationOtp(null, "user@example.com", "123456", 300, "en", "req-3")
        .block();

    assertThat(registry.lastChannel).isEqualTo(MessageDeliveryChannel.EMAIL);
    assertThat(sentCommand(delivery).channel()).isEqualTo(MessageDeliveryChannel.EMAIL);
  }

  @Test
  void theLanguageOfTheRequestIsPassedThroughUnchangedAndANullOneToo() {
    deliveryReturns(delivery, MessageDeliveryStatus.ACCEPTED);

    newAdapter(delivery)
        .deliverRegistrationOtp(RegistrationContactType.MOBILE, "91234567", "1", 60, null, "r")
        .block();

    assertThat(registry.lastLocale).isNull();
    assertThat(sentCommand(delivery).locale()).isNull();
  }

  // ----- the unit word comes from the resolved definition (D6) -----------------

  @Test
  void theUnitIsTheOneOfTheResolvedDefinitionNotOneDerivedFromTheRequestTag() {
    registry.sms = definition(MessageDeliveryChannel.SMS, "zh_HK", null, SMS_BODY, "分鐘");
    deliveryReturns(delivery, MessageDeliveryStatus.ACCEPTED);

    newAdapter(delivery)
        .deliverRegistrationOtp(RegistrationContactType.MOBILE, "91234567", "1", 300, "zh-TW", "r")
        .block();

    assertThat(registry.lastLocale).isEqualTo("zh-TW");
    assertThat(sentCommand(delivery).templateVariables().get("ttlUnit")).isEqualTo("分鐘");
  }

  @Test
  void aDefinitionWithoutAUnitSendsNoUnitVariable() {
    registry.sms = definition(MessageDeliveryChannel.SMS, "en", null, "Code {code}", null);
    deliveryReturns(delivery, MessageDeliveryStatus.ACCEPTED);

    newAdapter(delivery)
        .deliverRegistrationOtp(RegistrationContactType.MOBILE, "91234567", "9", 300, "en", "r")
        .block();

    assertThat(sentCommand(delivery).templateVariables())
        .isEqualTo(Map.of("code", "9", "ttlValue", "5"));
  }

  // ----- ttl-to-minutes conversion with a minimum of one minute ----------------

  @Test
  void ttlValueIsWholeMinutesWithAMinimumOfOne() {
    assertThat(ttlValueSentFor(0)).isEqualTo("1");
    assertThat(ttlValueSentFor(30)).isEqualTo("1");
    assertThat(ttlValueSentFor(59)).isEqualTo("1");
    assertThat(ttlValueSentFor(60)).isEqualTo("1");
    assertThat(ttlValueSentFor(119)).isEqualTo("1");
    assertThat(ttlValueSentFor(120)).isEqualTo("2");
    assertThat(ttlValueSentFor(300)).isEqualTo("5");
    assertThat(ttlValueSentFor(3600)).isEqualTo("60");
  }

  // ----- result-status mapping: ACCEPTED is true, anything else is false -------

  @Test
  void onlyAnAcceptedStatusYieldsTrue() {
    assertThat(deliveredWith(MessageDeliveryStatus.ACCEPTED)).isTrue();
    assertThat(deliveredWith(MessageDeliveryStatus.FAILED)).isFalse();
    assertThat(deliveredWith(MessageDeliveryStatus.UNKNOWN_OUTCOME)).isFalse();
  }

  // ----- failures (decision D7) -------------------------------------------------

  @Test
  void aMissingDefinitionFailsInsideTheCallWithIllegalStateAndNothingIsSent() {
    registry.failure = new IllegalStateException("No notice-templates.definitions entry for x");

    assertThatThrownBy(
            () ->
                newAdapter(delivery)
                    .deliverRegistrationOtp(
                        RegistrationContactType.MOBILE, "91234567", "123456", 300, "en", "req-4"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notice-templates.definitions");

    verifyNoInteractions(delivery);
  }

  @Test
  void aDeliveryErrorSignalIsNotSwallowed() {
    when(delivery.deliver(any())).thenReturn(Mono.error(new IllegalStateException("sender down")));

    StepVerifier.create(
            newAdapter(delivery)
                .deliverRegistrationOtp(
                    RegistrationContactType.MOBILE, "91234567", "123456", 300, "en", "req-5"))
        .expectErrorMessage("sender down")
        .verify();
  }

  // ----- helpers ----------------------------------------------------------------

  private RegistrationMessageDeliveryAdapter newAdapter(MessageDeliveryPort port) {
    return new RegistrationMessageDeliveryAdapter(port, registry);
  }

  private static void deliveryReturns(MessageDeliveryPort port, MessageDeliveryStatus status) {
    when(port.deliver(any()))
        .thenReturn(Mono.just(new MessageDeliveryResult(status, null, null, null, null)));
  }

  private static MessageDeliveryCommand sentCommand(MessageDeliveryPort port) {
    ArgumentCaptor<MessageDeliveryCommand> captor =
        ArgumentCaptor.forClass(MessageDeliveryCommand.class);
    verify(port).deliver(captor.capture());
    return captor.getValue();
  }

  private String ttlValueSentFor(int ttlSeconds) {
    MessageDeliveryPort port = mock(MessageDeliveryPort.class);
    deliveryReturns(port, MessageDeliveryStatus.ACCEPTED);
    newAdapter(port)
        .deliverRegistrationOtp(
            RegistrationContactType.MOBILE, "91234567", "123456", ttlSeconds, "en", "r")
        .block();
    return sentCommand(port).templateVariables().get("ttlValue");
  }

  private Boolean deliveredWith(MessageDeliveryStatus status) {
    MessageDeliveryPort port = mock(MessageDeliveryPort.class);
    deliveryReturns(port, status);
    return newAdapter(port)
        .deliverRegistrationOtp(
            RegistrationContactType.MOBILE, "91234567", "123456", 300, "en", "r")
        .block();
  }

  private static NoticeTemplateDefinition definition(
      MessageDeliveryChannel channel, String locale, String subject, String body, String ttlUnit) {
    return new NoticeTemplateDefinition(
        MessageDeliveryType.REGISTRATION_OTP,
        channel,
        locale,
        subject,
        body,
        Set.of("code", "ttlValue", "ttlUnit"),
        ttlUnit);
  }

  private static final class FakeRegistry implements NoticeTemplateRegistryPort {
    NoticeTemplateDefinition sms =
        definition(MessageDeliveryChannel.SMS, "en", null, SMS_BODY, "minutes");
    NoticeTemplateDefinition email =
        definition(MessageDeliveryChannel.EMAIL, "en", "Subject", EMAIL_BODY, "minutes");
    RuntimeException failure;
    int resolveCalls;
    MessageDeliveryType lastType;
    MessageDeliveryChannel lastChannel;
    String lastLocale;

    @Override
    public NoticeTemplateDefinition resolve(
        MessageDeliveryType messageType, MessageDeliveryChannel channel, String locale) {
      resolveCalls++;
      lastType = messageType;
      lastChannel = channel;
      lastLocale = locale;
      if (failure != null) {
        throw failure;
      }
      return channel == MessageDeliveryChannel.SMS ? sms : email;
    }
  }
}
