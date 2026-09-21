package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.PasswordPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import org.junit.jupiter.api.Test;

/**
 * Branch-complete unit tests for {@link ForgotPasswordPasswordValidator}. Exercises every rule
 * branch in {@code validate(...)} and every category branch in the private {@code categoryCount}.
 */
class ForgotPasswordPasswordValidatorTest {

  /** Builds a validator whose policy port returns the given password policy. */
  private static ForgotPasswordPasswordValidator validatorFor(PasswordPolicy password) {
    AuthWorkflowPolicyPort port = mock(AuthWorkflowPolicyPort.class);
    when(port.registrationPolicy())
        .thenReturn(new RegistrationAuthWorkflowPolicy(null, null, null, null, password));
    return new ForgotPasswordPasswordValidator(port);
  }

  /** A permissive policy (all rules disabled) so a single rule can be isolated per test. */
  private static PasswordPolicy permissive() {
    return new PasswordPolicy(0, 0, false, false, 0, "");
  }

  private static boolean hasCode(ForgotPasswordPasswordValidationResult r, String code) {
    return r.errors().stream().anyMatch(e -> code.equals(e.code()));
  }

  @Test
  void constructorRejectsNullPort() {
    assertThatThrownBy(() -> new ForgotPasswordPasswordValidator(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void throwsWhenPasswordPolicyIsNull() {
    ForgotPasswordPasswordValidator v = validatorFor(null);
    assertThatThrownBy(() -> v.validate("Abcdef1!", "Abcdef1!", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullPasswordAndConfirmReturnEarlyWithBothRequiredErrors() {
    ForgotPasswordPasswordValidationResult r =
        validatorFor(permissive()).validate(null, null, null);
    assertThat(r.valid()).isFalse();
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_REQUIRED)).isTrue();
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.CONFIRM_PASSWORD_REQUIRED)).isTrue();
    // Early return means the mismatch rule is NOT evaluated.
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_MISMATCH)).isFalse();
  }

  @Test
  void emptyPasswordIsRequired() {
    ForgotPasswordPasswordValidationResult r =
        validatorFor(permissive()).validate("", "x", null);
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_REQUIRED)).isTrue();
  }

  @Test
  void emptyConfirmIsRequired() {
    ForgotPasswordPasswordValidationResult r =
        validatorFor(permissive()).validate("x", "", null);
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.CONFIRM_PASSWORD_REQUIRED)).isTrue();
  }

  @Test
  void mismatchIsReported() {
    ForgotPasswordPasswordValidationResult r =
        validatorFor(permissive()).validate("abcdef", "abcxyz", null);
    assertThat(r.valid()).isFalse();
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_MISMATCH)).isTrue();
  }

  @Test
  void minLengthIsEnforced() {
    PasswordPolicy p = new PasswordPolicy(8, 0, false, false, 0, "");
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("abc", "abc", null);
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_MIN_LENGTH)).isTrue();
  }

  @Test
  void maxLengthIsEnforced() {
    PasswordPolicy p = new PasswordPolicy(0, 4, false, false, 0, "");
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("abcdef", "abcdef", null);
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_MAX_LENGTH)).isTrue();
  }

  @Test
  void spacesAreRejectedWhenNoSpaces() {
    PasswordPolicy p = new PasswordPolicy(0, 0, true, false, 0, "");
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("ab cd", "ab cd", null);
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_SPACES)).isTrue();
  }

  @Test
  void sameAsUsernameIsRejectedIgnoringCase() {
    PasswordPolicy p = new PasswordPolicy(0, 0, false, true, 0, "");
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("user", "user", "USER");
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_SAME_AS_USERNAME)).isTrue();
  }

  @Test
  void sameAsUsernameSkippedWhenProtectedUsernameNull() {
    // notSameAsUsername enabled but protectedUsername == null -> rule branch is skipped.
    PasswordPolicy p = new PasswordPolicy(0, 0, false, true, 0, "");
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("user", "user", null);
    assertThat(r.valid()).isTrue();
  }

  @Test
  void requiredCategoriesAreEnforced() {
    // requires 2 categories; a lowercase-only password provides 1.
    PasswordPolicy p = new PasswordPolicy(0, 0, false, false, 2, "");
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("abcdef", "abcdef", null);
    assertThat(hasCode(r, ForgotPasswordCompleteErrorKeys.PASSWORD_CATEGORIES)).isTrue();
  }

  @Test
  void categoriesSatisfiedWithNullSpecialChars() {
    // specialChars == null path in categoryCount; one lowercase category satisfies requirement 1.
    PasswordPolicy p = new PasswordPolicy(0, 0, false, false, 1, null);
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("abc", "abc", null);
    assertThat(r.valid()).isTrue();
  }

  @Test
  void allFourCategoriesAreCounted() {
    // requires 4 categories: upper + lower + digit + special.
    PasswordPolicy p = new PasswordPolicy(0, 0, false, false, 4, "!@#");
    ForgotPasswordPasswordValidationResult r =
        validatorFor(p).validate("Abcd1!ef", "Abcd1!ef", null);
    assertThat(r.valid()).isTrue();
  }

  @Test
  void validPasswordProducesSuccessResult() {
    PasswordPolicy p = new PasswordPolicy(8, 20, true, true, 3, "!@#$%");
    ForgotPasswordPasswordValidationResult r =
        validatorFor(p).validate("Abcdef1!", "Abcdef1!", "someone");
    assertThat(r.valid()).isTrue();
    assertThat(r.errors()).isEmpty();
  }

  @Test
  void emittedErrorDescriptorsHaveFormTypeAndServerSource() {
    PasswordPolicy p = new PasswordPolicy(8, 0, false, false, 0, "");
    ForgotPasswordPasswordValidationResult r = validatorFor(p).validate("abc", "abc", null);
    assertThat(r.valid()).isFalse();
    assertThat(r.errors())
        .allSatisfy(
            e -> {
              assertThat(e.type()).isEqualTo(WorkflowErrorType.FORM);
              assertThat(e.severity()).isEqualTo(WorkflowErrorSeverity.ERROR);
              assertThat(e.source()).isEqualTo("SERVER");
              assertThat(e.targets()).isNotEmpty();
            });
  }
}
