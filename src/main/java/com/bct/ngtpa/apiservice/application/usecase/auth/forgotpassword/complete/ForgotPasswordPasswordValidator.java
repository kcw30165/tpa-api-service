package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete;

import com.bct.ngtpa.apiservice.application.dto.auth.PasswordPolicy;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ForgotPasswordPasswordValidator {
    private static final String SOURCE_SERVER = "SERVER";
    private final AuthWorkflowPolicyPort policyPort;

    public ForgotPasswordPasswordValidator(AuthWorkflowPolicyPort policyPort) {
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort must not be null");
    }

    public ForgotPasswordPasswordValidationResult validate(
            String password,
            String confirmPassword,
            String protectedUsername) {
        PasswordPolicy policy = Objects.requireNonNull(
                policyPort.registrationPolicy().password(),
                "Registration password policy must not be null");
        List<WorkflowErrorDescriptor> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.PASSWORD_REQUIRED, "password"));
        }
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.CONFIRM_PASSWORD_REQUIRED, "confirmPassword"));
        }
        if (!errors.isEmpty()) {
            return ForgotPasswordPasswordValidationResult.failure(errors);
        }
        if (!password.equals(confirmPassword)) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.PASSWORD_MISMATCH, "confirmPassword"));
        }
        if (policy.minLength() > 0 && password.length() < policy.minLength()) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.PASSWORD_MIN_LENGTH, "password"));
        }
        if (policy.maxLength() > 0 && password.length() > policy.maxLength()) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.PASSWORD_MAX_LENGTH, "password"));
        }
        if (policy.noSpaces() && password.chars().anyMatch(Character::isWhitespace)) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.PASSWORD_SPACES, "password"));
        }
        if (policy.notSameAsUsernameIgnoreCase()
                && protectedUsername != null
                && password.equalsIgnoreCase(protectedUsername)) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.PASSWORD_SAME_AS_USERNAME, "password"));
        }
        if (policy.requiredCategories() > 0
                && categoryCount(password, policy.specialChars()) < policy.requiredCategories()) {
            errors.add(error(ForgotPasswordCompleteErrorKeys.PASSWORD_CATEGORIES, "password"));
        }
        return errors.isEmpty()
                ? ForgotPasswordPasswordValidationResult.success()
                : ForgotPasswordPasswordValidationResult.failure(errors);
    }

    private int categoryCount(String password, String configuredSpecials) {
        String specialChars = configuredSpecials == null ? "" : configuredSpecials;
        int categories = 0;
        if (password.chars().anyMatch(Character::isUpperCase)) {
            categories++;
        }
        if (password.chars().anyMatch(Character::isLowerCase)) {
            categories++;
        }
        if (password.chars().anyMatch(Character::isDigit)) {
            categories++;
        }
        if (password.chars().anyMatch(ch -> specialChars.indexOf(ch) >= 0)) {
            categories++;
        }
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