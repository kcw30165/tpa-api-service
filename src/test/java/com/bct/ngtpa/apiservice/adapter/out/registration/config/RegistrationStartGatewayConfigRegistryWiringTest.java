package com.bct.ngtpa.apiservice.adapter.out.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.message.NoticeTemplateDefinition;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The notice template registry that is injected into {@link RegistrationStartGatewayConfig} is the
 * one the Registration delivery adapter resolves its content from (solution design section 8.6).
 */
class RegistrationStartGatewayConfigRegistryWiringTest {

  private final RegistrationStartGatewayConfig config = new RegistrationStartGatewayConfig();
  private final NoticeTemplateRegistryPort registry = mock(NoticeTemplateRegistryPort.class);
  private final MessageDeliveryPort delivery = mock(MessageDeliveryPort.class);

  @Test
  void theGatewayDeliversRegistrationOtpWithContentFromTheInjectedRegistry() {
    when(registry.resolve(any(), any(), any()))
        .thenReturn(
            new NoticeTemplateDefinition(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryChannel.SMS,
                "en",
                null,
                "Code {code} valid {ttlValue} {ttlUnit}",
                Set.of("code", "ttlValue", "ttlUnit"),
                "minutes"));
    when(delivery.deliver(any()))
        .thenReturn(
            Mono.just(
                new MessageDeliveryResult(MessageDeliveryStatus.ACCEPTED, null, null, null, null)));
    RegistrationMessagePort gateway = gateway();

    StepVerifier.create(
            gateway.deliverRegistrationOtp(
                RegistrationContactType.MOBILE, "91234567", "123456", 300, "en", "req-1"))
        .expectNext(true)
        .verifyComplete();

    verify(registry)
        .resolve(MessageDeliveryType.REGISTRATION_OTP, MessageDeliveryChannel.SMS, "en");
    ArgumentCaptor<MessageDeliveryCommand> captor =
        ArgumentCaptor.forClass(MessageDeliveryCommand.class);
    verify(delivery).deliver(captor.capture());
    assertThat(captor.getValue().body()).isEqualTo("Code {code} valid {ttlValue} {ttlUnit}");
    assertThat(captor.getValue().templateVariables().get("ttlUnit")).isEqualTo("minutes");
    assertThat(captor.getValue().recipient()).isEqualTo("91234567");
  }

  @Test
  void aMissingDefinitionFailsTheGatewayCallAndNothingIsSent() {
    when(registry.resolve(any(), any(), any()))
        .thenThrow(new IllegalStateException("No notice-templates.definitions entry"));
    RegistrationMessagePort gateway = gateway();

    assertThatThrownBy(
            () ->
                gateway.deliverRegistrationOtp(
                    RegistrationContactType.MOBILE, "91234567", "123456", 300, "en", "req-2"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notice-templates.definitions");

    verifyNoInteractions(delivery);
  }

  private RegistrationMessagePort gateway() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    return config.registrationMessageGateway(
        delivery,
        registry,
        new DeploymentEnvironmentPolicy(environment),
        properties());
  }

  private static AuthWorkflowProperties properties() {
    var properties = new AuthWorkflowProperties();
    var registration = new AuthWorkflowProperties.Registration();
    registration.getMessage().getDestinationOverride().setMobile(null);
    registration.getMessage().getDestinationOverride().setEmail(null);
    properties.setRegistration(registration);
    return properties;
  }
}
