package com.bct.ngtpa.apiservice.application.dto.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoticeTemplateDefinitionTest {

  @Test
  void constructsSmsDefinitionWithoutSubject() {
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryChannel.SMS,
            "en",
            null,
            "BCT {trustCode}: Your One-Time Password (OTP) : {code}",
            Set.of("trustCode", "code"));

    assertThat(definition.subject()).isNull();
    assertThat(definition.requiredPlaceholders()).containsExactlyInAnyOrder("trustCode", "code");
  }

  @Test
  void constructsEmailDefinitionWithSubject() {
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.USERNAME_RECOVERY,
            MessageDeliveryChannel.EMAIL,
            "en",
            "Your Username",
            "Your username is {username}",
            Set.of("username"));

    assertThat(definition.subject()).isEqualTo("Your Username");
  }

  @Test
  void defaultsNullRequiredPlaceholdersToEmptySet() {
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.REGISTRATION_OTP,
            MessageDeliveryChannel.SMS,
            "en",
            null,
            "Your OTP is {code}",
            null);

    assertThat(definition.requiredPlaceholders()).isEmpty();
  }

  @Test
  void requiredPlaceholdersIsUnmodifiable() {
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.REGISTRATION_OTP,
            MessageDeliveryChannel.SMS,
            "en",
            null,
            "Your OTP is {code}",
            new LinkedHashSet<>(Set.of("code")));

    assertThatThrownBy(() -> definition.requiredPlaceholders().add("extra"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsNullMessageType() {
    assertThatThrownBy(
            () ->
                new NoticeTemplateDefinition(
                    null, MessageDeliveryChannel.SMS, "en", null, "body", Set.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("messageType");
  }

  @Test
  void rejectsNullChannel() {
    assertThatThrownBy(
            () ->
                new NoticeTemplateDefinition(
                    MessageDeliveryType.LOGIN_OTP, null, "en", null, "body", Set.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("channel");
  }

  @Test
  void rejectsBlankLocale() {
    assertThatThrownBy(
            () ->
                new NoticeTemplateDefinition(
                    MessageDeliveryType.LOGIN_OTP,
                    MessageDeliveryChannel.SMS,
                    "   ",
                    null,
                    "body",
                    Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("locale");
  }

  @Test
  void rejectsBlankBody() {
    assertThatThrownBy(
            () ->
                new NoticeTemplateDefinition(
                    MessageDeliveryType.LOGIN_OTP,
                    MessageDeliveryChannel.SMS,
                    "en",
                    null,
                    "   ",
                    Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("body");
  }

  @Test
  void rejectsNullLocale() {
    assertThatThrownBy(
            () ->
                new NoticeTemplateDefinition(
                    MessageDeliveryType.LOGIN_OTP,
                    MessageDeliveryChannel.SMS,
                    null,
                    null,
                    "body",
                    Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("locale");
  }

  @Test
  void rejectsNullBody() {
    assertThatThrownBy(
            () ->
                new NoticeTemplateDefinition(
                    MessageDeliveryType.LOGIN_OTP,
                    MessageDeliveryChannel.SMS,
                    "en",
                    null,
                    null,
                    Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("body");
  }

  @Test
  void allowsNullSubjectRegardlessOfChannel() {
    // OCP: this record must never branch on which channel value is present, so that adding a
    // future channel (WhatsApp, WeChat, ...) never requires modifying this class. The
    // "subject required for EMAIL" rule is enforced elsewhere (NoticeTemplateProperties.validate()
    // at config load, and MessageDeliveryAdapter.validate() at dispatch time), not here.
    NoticeTemplateDefinition smsDefinition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryChannel.SMS,
            "en",
            null,
            "body",
            Set.of());
    NoticeTemplateDefinition emailDefinitionWithNullSubject =
        new NoticeTemplateDefinition(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryChannel.EMAIL,
            "en",
            null,
            "body",
            Set.of());

    assertThat(smsDefinition.subject()).isNull();
    assertThat(emailDefinitionWithNullSubject.subject()).isNull();
  }

  @Test
  void allowsBlankSubjectForEmailChannel() {
    // Locked in deliberately: this DTO does not enforce EMAIL-requires-subject (see
    // allowsNullSubjectRegardlessOfChannel for rationale).
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryChannel.EMAIL,
            "en",
            "   ",
            "body",
            Set.of());

    assertThat(definition.subject()).isEqualTo("   ");
  }
}
