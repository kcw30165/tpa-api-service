package com.bct.ngtpa.apiservice.application.dto.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageDeliveryModelTest {

    @Test
    void messageDeliveryTypeInitiallySupportsRegistrationOtp() {
        assertThat(Arrays.stream(MessageDeliveryType.values()).map(Enum::name).toList())
                .containsExactly("REGISTRATION_OTP");
    }

    @Test
    void messageDeliveryScenarioInitiallySupportsRegistration() {
        assertThat(Arrays.stream(MessageDeliveryScenario.values()).map(Enum::name).toList())
                .containsExactly("REGISTRATION");
    }

    @Test
    void messageDeliveryChannelSupportsSmsAndEmail() {
        assertThat(Arrays.stream(MessageDeliveryChannel.values()).map(Enum::name).toList())
                .containsExactly("SMS", "EMAIL");
    }

    @Test
    void messageDeliveryStatusSupportsAcceptedAndFailed() {
        assertThat(Arrays.stream(MessageDeliveryStatus.values()).map(Enum::name).toList())
                .containsExactly("ACCEPTED", "FAILED");
    }

    @Test
    void messageDeliveryCommandCarriesGenericMessageFields() {
        RecordComponent[] components = MessageDeliveryCommand.class.getRecordComponents();

        assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
                .containsExactly(
                        "messageType",
                        "scenario",
                        "channel",
                        "recipient",
                        "subject",
                        "body",
                        "templateCode",
                        "templateVariables",
                        "locale",
                        "correlationId");
        assertThat(components[0].getType()).isEqualTo(MessageDeliveryType.class);
        assertThat(components[1].getType()).isEqualTo(MessageDeliveryScenario.class);
        assertThat(components[2].getType()).isEqualTo(MessageDeliveryChannel.class);
        assertThat(components[7].getType()).isEqualTo(Map.class);
    }

    @Test
    void messageDeliveryCommandDefensivelyCopiesTemplateVariables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("code", "123456");

        MessageDeliveryCommand command = new MessageDeliveryCommand(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryScenario.REGISTRATION,
                MessageDeliveryChannel.SMS,
                "85261234567",
                null,
                "Your code is 123456",
                "registration-otp",
                variables,
                "en-HK",
                "req-123");

        variables.put("code", "000000");

        assertThat(command.templateVariables()).containsEntry("code", "123456");
        assertThat(command.templateVariables()).isUnmodifiable();
    }

    @Test
    void messageDeliveryCommandNormalizesNullTemplateVariablesToEmptyMap() {
        MessageDeliveryCommand command = new MessageDeliveryCommand(
                MessageDeliveryType.REGISTRATION_OTP,
                MessageDeliveryScenario.REGISTRATION,
                MessageDeliveryChannel.EMAIL,
                "user@example.com",
                "Subject",
                "Body",
                "registration-otp",
                null,
                "en-HK",
                "req-123");

        assertThat(command.templateVariables()).isEmpty();
    }

    @Test
    void messageDeliveryCommandDoesNotContainSmsAdapterSpecificFields() {
        List<String> componentNames = Arrays.stream(MessageDeliveryCommand.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames)
                .doesNotContain("username", "password", "sendId", "classCode", "country", "phone", "smsContent");
        assertThat(Arrays.stream(MessageDeliveryCommand.class.getMethods())
                .map(method -> method.getName())
                .filter(name -> List.of("username", "password", "sendId", "classCode", "country", "phone", "smsContent")
                        .contains(name))
                .toList()).isEmpty();
    }

    @Test
    void messageDeliveryResultCarriesSafeOutcomeFields() {
        RecordComponent[] components = MessageDeliveryResult.class.getRecordComponents();

        assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
                .containsExactly(
                        "status",
                        "channel",
                        "providerMessageId",
                        "failureCode",
                        "failureMessage");
        assertThat(components[0].getType()).isEqualTo(MessageDeliveryStatus.class);
        assertThat(components[1].getType()).isEqualTo(MessageDeliveryChannel.class);
        assertThat(components[2].getType()).isEqualTo(String.class);
    }
}