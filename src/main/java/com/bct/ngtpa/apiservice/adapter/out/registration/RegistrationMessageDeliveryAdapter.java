package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Delivers the Registration OTP. The subject, the body and the unit word of {@code {ttlUnit}} come
 * from the notice template registry, resolved per request with the language of the request
 * (solution design section 8.6, decisions D6 and D7). A missing definition fails inside {@link
 * #deliverRegistrationOtp} with an {@link IllegalStateException}; there is no built-in fallback
 * content.
 */
@Slf4j
public class RegistrationMessageDeliveryAdapter implements RegistrationMessagePort {
  private final MessageDeliveryPort messageDeliveryPort;
  private final NoticeTemplateRegistryPort noticeTemplateRegistryPort;

  public RegistrationMessageDeliveryAdapter(
      MessageDeliveryPort messageDeliveryPort,
      NoticeTemplateRegistryPort noticeTemplateRegistryPort) {
    this.messageDeliveryPort =
        Objects.requireNonNull(messageDeliveryPort, "messageDeliveryPort must not be null");
    this.noticeTemplateRegistryPort =
        Objects.requireNonNull(
            noticeTemplateRegistryPort, "noticeTemplateRegistryPort must not be null");
  }

  @Override
  public Mono<Boolean> deliverRegistrationOtp(
      RegistrationContactType contactType,
      String recipient,
      String otpCode,
      int ttlSeconds,
      String acceptLanguage,
      String correlationId) {
    MessageDeliveryChannel channel = toChannel(contactType);
    NoticeTemplateDefinition definition =
        noticeTemplateRegistryPort.resolve(
            MessageDeliveryType.REGISTRATION_OTP, channel, acceptLanguage);

    var variables = new LinkedHashMap<String, String>();
    variables.put("code", otpCode);
    variables.put("ttlValue", String.valueOf(Math.max(1, ttlSeconds / 60)));
    if (definition.ttlUnit() != null) {
      variables.put("ttlUnit", definition.ttlUnit());
    }

    var command =
        new MessageDeliveryCommand(
            MessageDeliveryType.REGISTRATION_OTP,
            MessageDeliveryScenario.REGISTRATION,
            channel,
            recipient,
            definition.subject(),
            definition.body(),
            "registration-otp",
            Map.copyOf(variables),
            acceptLanguage,
            correlationId);

    return messageDeliveryPort
        .deliver(command)
        .map(result -> result.status() == MessageDeliveryStatus.ACCEPTED);
  }

  private MessageDeliveryChannel toChannel(RegistrationContactType contactType) {
    if (contactType == RegistrationContactType.MOBILE) {
      return MessageDeliveryChannel.SMS;
    }
    return MessageDeliveryChannel.EMAIL;
  }
}
