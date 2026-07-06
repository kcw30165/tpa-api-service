package com.bct.ngtpa.apiservice.application.service.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RegistrationContactMaskerTest {

    @Test
    void masksEmailWithoutExposingFullAddress() {
        assertThat(RegistrationContactMasker.maskEmail("member@example.com")).isEqualTo("m****r@example.com");
        assertThat(RegistrationContactMasker.maskEmail("a@example.com")).startsWith("a").endsWith("@example.com").doesNotContain("a@example.com");
    }

    @Test
    void masksMobileShowingLastFourDigitsOnly() {
        assertThat(RegistrationContactMasker.maskMobile("91234567")).isEqualTo("****4567");
    }

    @Test
    void maskerRejectsBlankSafely() {
        assertThatThrownBy(() -> RegistrationContactMasker.maskEmail(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contact must not be blank");
        assertThatThrownBy(() -> RegistrationContactMasker.maskMobile(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contact must not be blank");
    }
}
