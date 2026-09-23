package com.bct.ngtpa.apiservice.application.service.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import org.junit.jupiter.api.Test;

class RegistrationContactDeriverTest {

  private final RegistrationContactDeriver deriver =
      new RegistrationContactDeriver(new MaskingService(new MaskingProperties()));

  @Test
  void validEmailContactDerivesEmailContact() {
    var contact = deriver.derive(" Member@Example.COM ", null);
    assertThat(contact.contactType()).isEqualTo(RegistrationContactType.EMAIL);
    assertThat(contact.contactValue()).isEqualTo("member@example.com");
    assertThat(contact.maskedDestination()).isEqualTo("m****r@example.com");
  }

  @Test
  void validMobileContactDerivesMobileContact() {
    var contact = deriver.derive(null, " 91234567 ");
    assertThat(contact.contactType()).isEqualTo(RegistrationContactType.MOBILE);
    assertThat(contact.contactValue()).isEqualTo("91234567");
    assertThat(contact.maskedDestination()).isEqualTo("9******7");
  }
}
