package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationStartModelTest {

    @Test
    void registrationIdentityTypeDefinesAllowedValues() {
        assertThat(RegistrationIdentityType.values()).containsExactly(
                RegistrationIdentityType.GPN,
                RegistrationIdentityType.GPFS,
                RegistrationIdentityType.STAFF,
                RegistrationIdentityType.ID);
    }

    @Test
    void modelsToStringDoNotExposePii() {
        var command = new RegistrationStartCommand(
                RegistrationIdentityType.ID,
                "A1234567",
                "15/01/1980",
                RegistrationContactType.EMAIL,
                "member@example.com",
                "m****r@example.com",
                "cap_secret");
        var result = RegistrationStartValidationResult.valid(command);

        assertThat(command.toString())
                .doesNotContain("A1234567", "member@example.com", "91234567", "cap_secret")
                .contains("m****r@example.com");
        assertThat(result.toString())
                .doesNotContain("A1234567", "member@example.com", "cap_secret");
    }

    @Test
    void validationResultDefensivelyCopiesErrors() {
        var errors = new java.util.ArrayList<RegistrationStartValidationError>();
        errors.add(new RegistrationStartValidationError(
                com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey.START_REQUEST_INVALID,
                "identityType",
                "LOCAL"));
        var result = RegistrationStartValidationResult.invalid(errors);
        errors.clear();

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.command()).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.errors().add(result.errors().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
