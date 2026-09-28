package com.bct.ngtpa.apiservice.shared.masking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Defaults contract test for {@link MaskingProperties}. */
class MaskingPropertiesTest {

  @Test
  void defaultsMatchDocumentedPolicy() {
    MaskingProperties properties = new MaskingProperties();

    assertThat(properties.getEmail().isEnabled()).isTrue();
    assertThat(properties.getEmail().getMode()).isEqualTo(MaskMode.VARIABLE_LENGTH);
    assertThat(properties.getEmail().getMaskLength()).isEqualTo(4);
    assertThat(properties.getEmail().getMaskCharacter()).isEqualTo("*");

    assertThat(properties.getMobile().isEnabled()).isTrue();
    assertThat(properties.getMobile().getMode()).isEqualTo(MaskMode.VARIABLE_LENGTH);
    assertThat(properties.getMobile().getMaskLength()).isEqualTo(4);
    assertThat(properties.getMobile().getMaskCharacter()).isEqualTo("*");

    assertThat(properties.getIdentity().isEnabled()).isTrue();
    assertThat(properties.getIdentity().getMaskCharacter()).isEqualTo("*");

    assertThat(properties.getDateOfBirth().isEnabled()).isTrue();
    assertThat(properties.getDateOfBirth().getPlaceholder()).isEqualTo("<redacted>");

    assertThat(properties.getLogging().isEnabled()).isTrue();
    assertThat(properties.getLogging().getMaskCharacter()).isEqualTo("*");
    assertThat(properties.getLogging().getPlaceholder()).isEqualTo("***");

    assertThat(properties.getDisabledPlaceholder()).isEqualTo("<masked>");
  }
}
