package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.PasswordPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegistrationPasswordValidatorTest {
    private final RegistrationPasswordValidator validator = new RegistrationPasswordValidator(new Policy());

    @Test
    void validPasswordPassesExistingPolicy() {
        assertThat(validator.validate("Password1!", "Password1!", "member1").valid()).isTrue();
    }

    @Test
    void requiredAndConfirmationErrorsTargetFrontendFields() {
        var result = validator.validate("", null, "member1");
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).extracting(e -> e.code())
                .containsExactly(
                        CompleteRegistrationErrorKeys.PASSWORD_REQUIRED,
                        CompleteRegistrationErrorKeys.CONFIRM_PASSWORD_REQUIRED);
        assertThat(result.errors()).flatExtracting(e -> e.targets())
                .containsExactly("password", "confirmPassword");
    }

    @Test
    void mismatchTargetsConfirmationWithoutEchoingPasswords() {
        var result = validator.validate("Password1!", "Password2!", "member1");
        assertThat(result.errors()).anySatisfy(error -> {
            assertThat(error.code()).isEqualTo(CompleteRegistrationErrorKeys.PASSWORD_MISMATCH);
            assertThat(error.targets()).containsExactly("confirmPassword");
            assertThat(error.toString()).doesNotContain("Password1!", "Password2!");
        });
    }

    @Test
    void enforcesLengthWhitespaceUsernameAndCategoryRules() {
        assertThat(validator.validate("Aa1!", "Aa1!", "member1").errors())
                .extracting(e -> e.code()).contains(CompleteRegistrationErrorKeys.PASSWORD_MIN_LENGTH);
        assertThat(validator.validate("Password 1!", "Password 1!", "member1").errors())
                .extracting(e -> e.code()).contains(CompleteRegistrationErrorKeys.PASSWORD_SPACES);
        assertThat(validator.validate("MEMBER1", "MEMBER1", "member1").errors())
                .extracting(e -> e.code()).contains(CompleteRegistrationErrorKeys.PASSWORD_SAME_AS_USERNAME);
        assertThat(validator.validate("abcdefgh", "abcdefgh", "member1").errors())
                .extracting(e -> e.code()).contains(CompleteRegistrationErrorKeys.PASSWORD_CATEGORIES);
    }

    @Test
    void completeCommandRedactsAllSensitiveTransportValues() {
        var command = new CompleteRegistrationCommand(
                new com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId("afs_safe"),
                "csrf-secret",
                new com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey("idem-safe"),
                "Password1!",
                "Password1!",
                "https://portal.test",
                "https://portal.test/register",
                "en",
                "req-1",
                java.time.Duration.ofMinutes(5));
        assertThat(command.toString()).doesNotContain(
                "afs_safe", "csrf-secret", "idem-safe", "Password1!", "https://portal.test");
    }

    private static final class Policy implements AuthWorkflowPolicyPort {
        @Override public RegistrationAuthWorkflowPolicy registrationPolicy() {
            return new RegistrationAuthWorkflowPolicy(null, null, null, null,
                    new PasswordPolicy(8, 16, true, true, 3, "!@#$%^&*()_-"));
        }
        @Override public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
            return Optional.empty();
        }
    }
}
