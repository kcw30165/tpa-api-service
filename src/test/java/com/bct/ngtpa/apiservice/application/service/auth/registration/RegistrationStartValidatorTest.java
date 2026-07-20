package com.bct.ngtpa.apiservice.application.service.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;

import org.junit.jupiter.api.Test;

class RegistrationStartValidatorTest {
    private final RegistrationStartValidator validator = new RegistrationStartValidator();

    @Test
    void contactValidationRejectsBothBlank() {
        var result = validator.validate(input("GPFS", "1234567890", "31/01/1980", null, null, null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> {
            assertThat(error.errorKey()).isEqualTo(RegistrationErrorKey.REGISTRATION_CONTACT_REQUIRED);
            assertThat(error.target()).isEqualTo("email");
        });
    }

    @Test
    void contactValidationRejectsBothPresent() {
        var result = validator.validate(input("GPFS", "1234567890", "31/01/1980", "member@example.com", "91234567", null));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error.errorKey()).isEqualTo(RegistrationErrorKey.REGISTRATION_CONTACT_EXACTLYONEREQUIRED));
    }

    @Test
    void emailValidationRejectsInvalidEmail() {
        for (String email : java.util.List.of("memberexample.com", "@domain.com", "member@", "member@@example.com", "member example@example.com")) {
            var result = validator.validate(input("GPFS", "1234567890", "31/01/1980", email, null, null));
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.errorKey()).isEqualTo(RegistrationErrorKey.REGISTRATION_EMAIL_INVALID);
                assertThat(error.target()).isEqualTo("email");
                assertThat(error.toString()).doesNotContain(email);
            });
        }
    }

    @Test
    void emailValidationRejectsTooLongEmail() {
        var local = "a".repeat(111);
        var result = validator.validate(input("GPFS", "1234567890", "31/01/1980", local + "@example.com", null, null));

        assertThat(result.errors()).anySatisfy(error -> assertThat(error.target()).isEqualTo("email"));
    }

    @Test
    void mobileValidationRejectsInvalidMobile() {
        for (String mobile : java.util.List.of("31234567", "9123456", "912345678", "9123abcd", "9123 4567")) {
            var result = validator.validate(input("GPFS", "1234567890", "31/01/1980", null, mobile, null));
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.errorKey()).isEqualTo(RegistrationErrorKey.REGISTRATION_MOBILE_INVALID);
                assertThat(error.target()).isEqualTo("mobile");
                assertThat(error.toString()).doesNotContain(mobile);
            });
        }
    }

    @Test
    void dateOfBirthValidationAcceptsStrictDdMmYyyy() {
        assertThat(validator.validate(input("GPFS", "1234567890", "31/01/1980", "member@example.com", null, null)).valid()).isTrue();
        assertThat(validator.validate(input("GPFS", "1234567890", "29/02/2000", "member@example.com", null, null)).valid()).isTrue();
    }

    @Test
    void dateOfBirthValidationRejectsInvalidDates() {
        for (String dob : java.util.List.of("31/02/1980", "1980-01-31", "01/31/1980", " ")) {
            var result = validator.validate(input("GPFS", "1234567890", dob, "member@example.com", null, null));
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.errorKey()).isEqualTo(RegistrationErrorKey.REGISTRATION_DATEOFBIRTH_INVALID);
                assertThat(error.target()).isEqualTo("dateOfBirth");
            });
        }
    }

    @Test
    void identityValidationAcceptsGpnAndGpfs() {
        assertThat(validator.validate(input("GPN", "01234567", "31/01/1980", "member@example.com", null, null)).valid()).isTrue();
        assertThat(validator.validate(input("GPFS", "1234567890", "31/01/1980", "member@example.com", null, null)).valid()).isTrue();
    }

    @Test
    void identityValidationRejectsInvalidGpnAndGpfs() {
        for (var pair : java.util.List.of(
                new String[]{"GPN", "1234567"},
                new String[]{"GPN", "123456789"},
                new String[]{"GPN", "ABC12345"},
                new String[]{"GPFS", "123456789"},
                new String[]{"GPFS", "12345678901"},
                new String[]{"GPFS", "ABC1234567"})) {
            var result = validator.validate(input(pair[0], pair[1], "31/01/1980", "member@example.com", null, null));
            assertThat(result.errors()).anySatisfy(error -> assertThat(error.target()).isEqualTo("identityValue"));
        }
    }

    @Test
    void identityValidationAcceptsStaffNo() {
        assertThat(validator.validate(input("STAFF", "A123B", "31/01/1980", "member@example.com", null, null)).valid()).isTrue();
        assertThat(validator.validate(input("STAFF", "A 123", "31/01/1980", "member@example.com", null, null)).valid()).isFalse();
        assertThat(validator.validate(input("STAFF", "A-123", "31/01/1980", "member@example.com", null, null)).valid()).isFalse();
    }

    @Test
    void identityValidationAcceptsID() {
        assertThat(validator.validate(input("ID", "P1234567", "31/01/1980", "member@example.com", null, null)).valid()).isTrue();
        assertThat(validator.validate(input("ID", "ID-123456", "31/01/1980", "member@example.com", null, null)).valid()).isTrue();
        assertThat(validator.validate(input("ID", "P 123", "31/01/1980", "member@example.com", null, null)).valid()).isFalse();
        assertThat(validator.validate(input("ID", "A".repeat(41), "31/01/1980", "member@example.com", null, null)).valid()).isFalse();
    }

    @Test
    void validatorBuildsCommandForValidRequest() {
        var result = validator.validate(input("GPFS", "1234567890", "31/01/1980", "member@example.com", null, null));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.command().identityType()).isEqualTo(RegistrationIdentityType.GPFS);
        assertThat(result.command().dateOfBirth()).isEqualTo("31/01/1980");
        assertThat(result.command().contactType()).isEqualTo(RegistrationContactType.EMAIL);
        assertThat(result.command().maskedDestination()).isNotBlank();
    }

    @Test
    void validatorAggregatesMultipleErrors() {
        var result = validator.validate(input("BAD", "ABC", "31/02/1980", "bad-email", null, "cap abc"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void validatorUsesWorkflowErrorKeysOnly() {
        var result = validator.validate(input("BAD", "ABC", "31/02/1980", null, null, null));

        assertThat(result.errors()).allSatisfy(error -> {
            assertThat(error.errorKey().value()).startsWith("err.registration.");
            assertThat(error.toString()).doesNotContain("Invalid", "Please", "請", "錯誤");
        });
    }

    @Test
    void captchaChallengeIdIsOptionalAndValidatedWhenPresent() {
        assertThat(validator.validate(input("GPFS", "1234567890", "31/01/1980", "member@example.com", null, null)).valid()).isTrue();
        assertThat(validator.validate(input("GPFS", "1234567890", "31/01/1980", "member@example.com", null, " ")).valid()).isTrue();
        for (String captcha : java.util.List.of("cap abc", "cap:abc", "member@example.com")) {
            var result = validator.validate(input("GPFS", "1234567890", "31/01/1980", "member@example.com", null, captcha));
            assertThat(result.errors()).anySatisfy(error -> {
                assertThat(error.errorKey()).isEqualTo(RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID);
                assertThat(error.target()).isEqualTo("captchaChallengeId");
                assertThat(error.toString()).doesNotContain(captcha);
            });
        }
    }

    private static RegistrationStartInput input(String identityType, String identityValue, String dob, String email, String mobile, String captcha) {
        return new RegistrationStartInput(identityType, identityValue, dob, email, mobile, captcha);
    }
}
