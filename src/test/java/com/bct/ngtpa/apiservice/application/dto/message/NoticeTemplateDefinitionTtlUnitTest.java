package com.bct.ngtpa.apiservice.application.dto.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The per-locale TTL unit carried by the resolved definition (solution design section 8.6). */
class NoticeTemplateDefinitionTtlUnitTest {

  @Test
  void sixArgumentConstructorLeavesTheTtlUnitNull() {
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.REGISTRATION_OTP,
            MessageDeliveryChannel.SMS,
            "en",
            null,
            "Your OTP is {code}",
            Set.of("code"));

    assertThat(definition.ttlUnit()).isNull();
  }

  @Test
  void theUnitIsRetained() {
    NoticeTemplateDefinition english =
        new NoticeTemplateDefinition(
            MessageDeliveryType.REGISTRATION_OTP,
            MessageDeliveryChannel.SMS,
            "en",
            null,
            "Valid for {ttlValue} {ttlUnit}",
            Set.of("ttlValue", "ttlUnit"),
            "minutes");
    NoticeTemplateDefinition chinese =
        new NoticeTemplateDefinition(
            MessageDeliveryType.REGISTRATION_OTP,
            MessageDeliveryChannel.SMS,
            "zh_HK",
            null,
            "有效期為{ttlValue}{ttlUnit}",
            Set.of("ttlValue", "ttlUnit"),
            "分鐘");

    assertThat(english.ttlUnit()).isEqualTo("minutes");
    assertThat(chinese.ttlUnit()).isEqualTo("分鐘");
  }

  @Test
  void aNullUnitIsAccepted() {
    NoticeTemplateDefinition definition =
        new NoticeTemplateDefinition(
            MessageDeliveryType.USERNAME_RECOVERY,
            MessageDeliveryChannel.SMS,
            "en",
            null,
            "Your username is {username}",
            Set.of("username"),
            null);

    assertThat(definition.ttlUnit()).isNull();
  }

  @Test
  void aBlankUnitIsRejected() {
    for (String blank : new String[] {"", " ", "   "}) {
      assertThatThrownBy(
              () ->
                  new NoticeTemplateDefinition(
                      MessageDeliveryType.REGISTRATION_OTP,
                      MessageDeliveryChannel.SMS,
                      "en",
                      null,
                      "body",
                      Set.of(),
                      blank))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ttlUnit");
    }
  }

  @Test
  void theSixArgumentConstructorEqualsTheSevenArgumentOneWithANullUnit() {
    NoticeTemplateDefinition six =
        new NoticeTemplateDefinition(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryChannel.EMAIL,
            "en",
            "Subject",
            "Body {code}",
            Set.of("code"));
    NoticeTemplateDefinition seven =
        new NoticeTemplateDefinition(
            MessageDeliveryType.LOGIN_OTP,
            MessageDeliveryChannel.EMAIL,
            "en",
            "Subject",
            "Body {code}",
            Set.of("code"),
            null);

    assertThat(six).isEqualTo(seven);
  }
}
