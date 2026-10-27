package com.bct.ngtpa.apiservice.application.service.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountIdentifierFormatterTest {

  @Test
  void rejectsInvalidWidthsAndPadCharacterAndExposesConfiguredWidths() {
    assertThatThrownBy(() -> new AccountIdentifierFormatter(0, 4, '0'))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountIdentifierFormatter(11, 0, '0'))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccountIdentifierFormatter(11, 4, 'x'))
        .isInstanceOf(IllegalArgumentException.class);
    var formatter = new AccountIdentifierFormatter(11, 4, '0');
    assertThat(formatter.policyWidth()).isEqualTo(11);
    assertThat(formatter.certificateWidth()).isEqualTo(4);
  }

  @Test
  void formatsPolicyAndCertificateUsingConfiguredWidths() {
    var formatter = new AccountIdentifierFormatter(11, 4, '0');

    assertThat(formatter.format("118", "3")).isEqualTo("00000000118-0003");
  }

  @Test
  void usesConfiguredWidthsAndPadCharacter() {
    var formatter = new AccountIdentifierFormatter(7, 3, '9');

    assertThat(formatter.format("42", "8")).isEqualTo("9999942-998");
  }

  @Test
  void rejectsNullAndBlankIdentifiers() {
    var formatter = new AccountIdentifierFormatter(11, 4, '0');

    assertThatThrownBy(() -> formatter.formatPolicyNo(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> formatter.formatPolicyNo("  "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> formatter.formatCertificateNo(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> formatter.formatCertificateNo(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonNumericAndNonPositiveCertificateNumbers() {
    var formatter = new AccountIdentifierFormatter(11, 4, '0');

    assertThatThrownBy(() -> formatter.formatCertificateNo("abc"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> formatter.formatCertificateNo("0"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> formatter.formatCertificateNo("-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsValuesWiderThanConfiguredWidth() {
    var formatter = new AccountIdentifierFormatter(3, 2, '0');

    assertThatThrownBy(() -> formatter.formatPolicyNo("1234"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> formatter.formatCertificateNo("123"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
