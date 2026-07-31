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
        String workflowVersion,
        ForgotPasswordProtectedPhase phase) {

    public ForgotPasswordAuthFlowContext {
        normalizedUsername = requireText(normalizedUsername, "normalizedUsername");
        customerId = requireText(customerId, "customerId");
        tasUserId = requireText(tasUserId, "tasUserId");
        publicIdentityType = requireText(publicIdentityType, "publicIdentityType");
        workflowVersion = requireText(workflowVersion, "workflowVersion");
        phase = phase == null && completeStartContext(normalizedUsername, customerId, tasUserId, publicIdentityType)
                ? ForgotPasswordProtectedPhase.USERNAME_VERIFIED : phase;
    }

    public ForgotPasswordAuthFlowContext(
            String normalizedUsername, String customerId, String tasUserId, String publicIdentityType,
            boolean identityVerified, String resetSubjectId, String contactChannel,
            String protectedDestination, String maskedDestination,
            ForgotPasswordResetAuthorization resetAuthorization, String workflowVersion) {
        this(normalizedUsername, customerId, tasUserId, publicIdentityType, identityVerified,
                resetSubjectId, contactChannel, protectedDestination, maskedDestination,
                resetAuthorization, workflowVersion, null);
    }

    private static boolean completeStartContext(String username, String customerId, String userId, String identityType) {
        return hasText(username) && hasText(customerId) && hasText(userId) && hasText(identityType);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    public ForgotPasswordAuthFlowContext withPhase(ForgotPasswordProtectedPhase next) {
        return new ForgotPasswordAuthFlowContext(normalizedUsername, customerId, tasUserId, publicIdentityType,
                next == ForgotPasswordProtectedPhase.IDENTITY_VERIFIED, resetSubjectId, contactChannel,
                protectedDestination, maskedDestination, resetAuthorization, workflowVersion, next);
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
                + ", resetAuthorization=<redacted>, workflowVersion=" + workflowVersion + ", phase=" + phase + "]";
    }
}