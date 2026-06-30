package com.bct.ngtpa.apiservice.application.service.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecureOtpCodeGeneratorTest {

    @Test
    void generatesNumericOtpWithConfiguredLength() {
        OtpCodeGenerator generator = new SecureOtpCodeGenerator();

        String otp = generator.generate(6, true);

        assertThat(otp).hasSize(6).matches("\\d{6}");
    }

    @Test
    void generatesAlphanumericOtpWhenNumericOnlyFalse() {
        OtpCodeGenerator generator = new SecureOtpCodeGenerator();

        String otp = generator.generate(10, false);

        assertThat(otp).hasSize(10).matches("[A-Za-z0-9]{10}");
    }

    @Test
    void rejectsInvalidOtpLength() {
        OtpCodeGenerator generator = new SecureOtpCodeGenerator();

        assertThatThrownBy(() -> generator.generate(0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(-1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}