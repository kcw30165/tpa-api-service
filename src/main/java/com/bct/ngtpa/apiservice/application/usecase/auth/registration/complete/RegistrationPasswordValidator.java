package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.auth.PasswordPolicy;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Validates completion passwords using the existing registration PasswordPolicy. */
public class RegistrationPasswordValidator {
  private static final String TARGET_PASSWORD = "password";

  private static final String SOURCE_SERVER = "SERVER";
  private final AuthWorkflowPolicyPort policyPort;

  public RegistrationPasswordValidator(AuthWorkflowPolicyPort policyPort) {
    this.policyPort = Objects.requireNonNull(policyPort, "policyPort must not be null");
  }

  public RegistrationPasswordValidationResult validate(
      String password, String confirmPassword, String protectedUsername) {
    PasswordPolicy policy =
        Objects.requireNonNull(
            policyPort.registrationPolicy().password(),
            "Registration password policy must not be null");
    List<WorkflowErrorDescriptor> errors = new ArrayList<>();

    if (password == null || password.isEmpty()) {
      errors.add(error(CompleteRegistrationErrorKeys.PASSWORD_REQUIRED, TARGET_PASSWORD));
    }
    if (confirmPassword == null || confirmPassword.isEmpty()) {
      errors.add(error(CompleteRegistrationErrorKeys.CONFIRM_PASSWORD_REQUIRED, "confirmPassword"));
    }
    if (!errors.isEmpty()) {
      return RegistrationPasswordValidationResult.failure(errors);
    }
    if (!password.equals(confirmPassword)) {
      errors.add(error(CompleteRegistrationErrorKeys.PASSWORD_MISMATCH, "confirmPassword"));
    }
    if (policy.minLength() > 0 && password.length() < policy.minLength()) {
      errors.add(error(CompleteRegistrationErrorKeys.PASSWORD_MIN_LENGTH, TARGET_PASSWORD));
    }
    if (policy.maxLength() > 0 && password.length() > policy.maxLength()) {
      errors.add(error(CompleteRegistrationErrorKeys.PASSWORD_MAX_LENGTH, TARGET_PASSWORD));
    }
    if (policy.noSpaces() && password.chars().anyMatch(Character::isWhitespace)) {
      errors.add(error(CompleteRegistrationErrorKeys.PASSWORD_SPACES, TARGET_PASSWORD));
    }
    if (policy.notSameAsUsernameIgnoreCase()
        && protectedUsername != null
        && password.equalsIgnoreCase(protectedUsername)) {
      errors.add(error(CompleteRegistrationErrorKeys.PASSWORD_SAME_AS_USERNAME, TARGET_PASSWORD));
    }
    if (policy.requiredCategories() > 0
        && categoryCount(password, policy.specialChars()) < policy.requiredCategories()) {
      errors.add(error(CompleteRegistrationErrorKeys.PASSWORD_CATEGORIES, TARGET_PASSWORD));
    }
    return errors.isEmpty()
        ? RegistrationPasswordValidationResult.success()
        : RegistrationPasswordValidationResult.failure(errors);
  }

  private int categoryCount(String password, String configuredSpecials) {
    String specialChars = configuredSpecials == null ? "" : configuredSpecials;
    int categories = 0;
    if (password.chars().anyMatch(Character::isUpperCase)) categories++;
    if (password.chars().anyMatch(Character::isLowerCase)) categories++;
    if (password.chars().anyMatch(Character::isDigit)) categories++;
    if (password.chars().anyMatch(ch -> specialChars.indexOf(ch) >= 0)) categories++;
    return categories;
  }

  private WorkflowErrorDescriptor error(String code, String target) {
    return new WorkflowErrorDescriptor(
        WorkflowErrorType.FORM,
        code,
        null,
        List.of(target),
        WorkflowErrorSeverity.ERROR,
        SOURCE_SERVER);
  }
}
