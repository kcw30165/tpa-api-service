package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import org.junit.jupiter.api.Test;

class ForgotPasswordIdentityVerifierTest {

    private final ForgotPasswordIdentityVerifier verifier = new ForgotPasswordIdentityVerifier();

    @Test
    void mapsFrontendStaffNumberType() {
        assertThat(verifier.mapPublicIdentityType("STAFF_NO"))
                .isEqualTo(RegistrationIdentityType.STAFFNO);
    }

    @Test
    void matchesFrontendStaffNumberAgainstProtectedPublicType() {
        assertThat(verifier.matches("STAFF_NO", "3030609", "STAFF_NO", "3030609"))
                .isTrue();
    }

    @Test
    void matchesFrontendStaffNumberAgainstLegacyEnumType() {
        assertThat(verifier.matches("STAFF_NO", "3030609", "STAFFNO", "3030609"))
                .isTrue();
    }

    @Test
    void rejectsNonCanonicalFrontendTypeVariants() {
        assertThat(verifier.mapPublicIdentityType("staff_no")).isNull();
        assertThat(verifier.mapPublicIdentityType(" STAFF_NO ")).isNull();
        assertThat(verifier.mapPublicIdentityType("STAFFNO")).isNull();
    }

    @Test
    void rejectsDifferentIdentityValue() {
        assertThat(verifier.matches("STAFF_NO", "3030608", "STAFF_NO", "3030609"))
                .isFalse();
    }

    @Test
    void rejectsDifferentIdentityType() {
        assertThat(verifier.matches("ID", "3030609", "STAFF_NO", "3030609"))
                .isFalse();
    }

    @Test
    void rejectsUnsupportedOrMissingInputs() {
        assertThat(verifier.matches("UNKNOWN", "3030609", "STAFF_NO", "3030609"))
                .isFalse();
        assertThat(verifier.matches(null, "3030609", "STAFF_NO", "3030609"))
                .isFalse();
        assertThat(verifier.matches("STAFF_NO", null, "STAFF_NO", "3030609"))
                .isFalse();
    }
}
