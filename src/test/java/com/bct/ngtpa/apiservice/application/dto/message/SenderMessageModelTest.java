package com.bct.ngtpa.apiservice.application.dto.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SenderMessageModelTest {

  @Test
  void senderStatusSupportsAcceptedFailedAndUnknownOutcome() {
    assertThat(Arrays.stream(SenderStatus.values()).map(Enum::name).toList())
        .containsExactly("ACCEPTED", "FAILED", "UNKNOWN_OUTCOME");
  }

  @Test
  void smsMessageCarriesGenericSmsFields() {
    RecordComponent[] components = SmsMessage.class.getRecordComponents();

    assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
        .containsExactly("recipient", "content", "country", "correlationId");
    assertThat(components[0].getType()).isEqualTo(String.class);
    assertThat(components[1].getType()).isEqualTo(String.class);
    assertThat(components[2].getType()).isEqualTo(String.class);
    assertThat(components[3].getType()).isEqualTo(String.class);
  }

  @Test
  void smsMessageDoesNotContainProviderCredentialOrPayloadFields() {
    List<String> componentNames =
        Arrays.stream(SmsMessage.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames)
        .isNotEmpty()
        .doesNotContain("username", "password", "sendId", "classCode", "smsContent");
    assertThat(
            Arrays.stream(SmsMessage.class.getMethods())
                .map(method -> method.getName())
                .filter(
                    name ->
                        List.of("username", "password", "sendId", "classCode", "smsContent")
                            .contains(name))
                .toList())
        .isEmpty();
  }

  @Test
  void emailMessageCarriesGenericEmailFields() {
    RecordComponent[] components = EmailMessage.class.getRecordComponents();

    assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
        .containsExactly("recipient", "subject", "body", "from", "correlationId");
    assertThat(components[0].getType()).isEqualTo(String.class);
    assertThat(components[1].getType()).isEqualTo(String.class);
    assertThat(components[2].getType()).isEqualTo(String.class);
    assertThat(components[3].getType()).isEqualTo(String.class);
    assertThat(components[4].getType()).isEqualTo(String.class);
  }

  @Test
  void emailMessageDoesNotContainSmtpProviderFields() {
    List<String> componentNames =
        Arrays.stream(EmailMessage.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames)
        .isNotEmpty()
        .doesNotContain("smtpHost", "smtpPort", "username", "password");
    assertThat(
            Arrays.stream(EmailMessage.class.getMethods())
                .map(method -> method.getName())
                .filter(
                    name -> List.of("smtpHost", "smtpPort", "username", "password").contains(name))
                .toList())
        .isEmpty();
  }

  @Test
  void senderResultCarriesSafeOutcomeFields() {
    RecordComponent[] components = SenderResult.class.getRecordComponents();

    assertThat(Arrays.stream(components).map(RecordComponent::getName).toList())
        .containsExactly("status", "providerMessageId", "failureCode", "failureMessage");
    assertThat(components[0].getType()).isEqualTo(SenderStatus.class);
    assertThat(components[1].getType()).isEqualTo(String.class);
    assertThat(components[2].getType()).isEqualTo(String.class);
    assertThat(components[3].getType()).isEqualTo(String.class);
  }

  @Test
  void messageDeliveryCommandStillDoesNotContainSenderProviderFields() {
    List<String> componentNames =
        Arrays.stream(MessageDeliveryCommand.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(componentNames)
        .isNotEmpty()
        .doesNotContain("sendId", "classCode", "smtpHost", "smtpPort", "smsContent");
    assertThat(
            Arrays.stream(MessageDeliveryCommand.class.getMethods())
                .map(method -> method.getName())
                .filter(
                    name ->
                        List.of("sendId", "classCode", "smtpHost", "smtpPort", "smsContent")
                            .contains(name))
                .toList())
        .isEmpty();
  }
}
