package com.bct.ngtpa.apiservice.application.service.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import org.junit.jupiter.api.Test;

class RegistrationContactDeriverTest {

    @Test
    void validEmailContactDerivesEmailContact() {
        var contact = new RegistrationContactDeriver().derive(" Member@Example.COM ", null);

        assertThat(contact.contactType()).isEqualTo(RegistrationContactType.EMAIL);
        assertThat(contact.contactValue()).isEqualTo("member@example.com");
        assertThat(contact.maskedDestination()).isEqualTo("m****r@example.com");
    }

    @Test
    void validMobileContactDerivesMobileContact() {
        var contact = new RegistrationContactDeriver().derive(null, " 91234567 ");

        assertThat(contact.contactType()).isEqualTo(RegistrationContactType.MOBILE);
        assertThat(contact.contactValue()).isEqualTo("91234567");
        assertThat(contact.maskedDestination()).isEqualTo("****4567");
    }
}
