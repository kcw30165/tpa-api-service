package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;
import java.util.Objects;

public record ForgotPasswordAuthFlowContext(
        String normalizedUsername,
        String customerId,
        String tasUserId,
        String publicIdentityType,
        boolean identityVerified,
        String resetSubjectId,
        String contactChannel,
        String protectedDestination,
        String maskedDestination,
        ForgotPasswordResetAuthorization resetAuthorization,
        String workflowVersion) {

    public ForgotPasswordAuthFlowContext {
        normalizedUsername = requireText(normalizedUsername, "normalizedUsername");
        customerId = requireText(customerId, "customerId");
        tasUserId = requireText(tasUserId, "tasUserId");
        publicIdentityType = requireText(publicIdentityType, "publicIdentityType");
        workflowVersion = requireText(workflowVersion, "workflowVersion");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "ForgotPasswordAuthFlowContext[normalizedUsername=<redacted>, customerId=<redacted>, tasUserId=<redacted>, publicIdentityType="
                + publicIdentityType
                + ", identityVerified=" + identityVerified
                + ", resetSubjectId=<redacted>, contactChannel=" + contactChannel
                + ", protectedDestination=<redacted>, maskedDestination=" + maskedDestination
                + ", resetAuthorization=<redacted>, workflowVersion=" + workflowVersion + "]";
    }
}