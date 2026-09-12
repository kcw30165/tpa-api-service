package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;

public record ForgotPasswordAuthFlowContext(
    String normalizedUsername,
    String customerId,
    String identityValue,
    String identityType,
    boolean identityVerified,
    String resetSubjectId,
    String contactChannel,
    String protectedDestination,
    String maskedDestination,
    ForgotPasswordResetAuthorization resetAuthorization,
    String accountEnv,
    String workflowVersion,
    ForgotPasswordProtectedPhase phase) {

  public ForgotPasswordAuthFlowContext {
    normalizedUsername = requireText(normalizedUsername, "normalizedUsername");
    customerId = requireText(customerId, "customerId");
    identityValue = requireText(identityValue, "identityValue");
    identityType = requireText(identityType, "identityType");
    workflowVersion = requireText(workflowVersion, "workflowVersion");
    phase =
        phase == null
                && completeStartContext(
                    normalizedUsername, customerId, identityValue, identityType)
            ? ForgotPasswordProtectedPhase.USERNAME_VERIFIED
            : phase;
  }

  /**
   * Backward-compatible constructor matching the previous 12-arg signature. Preserves old call
   * sites by inserting a null accountEnv.
   */
  public ForgotPasswordAuthFlowContext(
      String normalizedUsername,
      String customerId,
      String identityValue,
      String identityType,
      boolean identityVerified,
      String resetSubjectId,
      String contactChannel,
      String protectedDestination,
      String maskedDestination,
      ForgotPasswordResetAuthorization resetAuthorization,
      String workflowVersion,
      ForgotPasswordProtectedPhase phase) {
    this(
        normalizedUsername,
        customerId,
        identityValue,
        identityType,
        identityVerified,
        resetSubjectId,
        contactChannel,
        protectedDestination,
        maskedDestination,
        resetAuthorization,
        null,
        workflowVersion,
        phase);
  }

  public ForgotPasswordAuthFlowContext(
      String normalizedUsername,
      String customerId,
      String identityValue,
      String identityType,
      boolean identityVerified,
      String resetSubjectId,
      String contactChannel,
      String protectedDestination,
      String maskedDestination,
      ForgotPasswordResetAuthorization resetAuthorization,
      String workflowVersion) {
    this(
        normalizedUsername,
        customerId,
        identityValue,
        identityType,
        identityVerified,
        resetSubjectId,
        contactChannel,
        protectedDestination,
        maskedDestination,
        resetAuthorization,
        null,
        workflowVersion,
        null);
  }

  private static boolean completeStartContext(
      String username, String customerId, String userId, String identityType) {
    return hasText(username) && hasText(customerId) && hasText(userId) && hasText(identityType);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public ForgotPasswordAuthFlowContext withPhase(ForgotPasswordProtectedPhase next) {
    return new ForgotPasswordAuthFlowContext(
        normalizedUsername,
        customerId,
        identityValue,
        identityType,
        identityVerified || next == ForgotPasswordProtectedPhase.IDENTITY_VERIFIED,
        resetSubjectId,
        contactChannel,
        protectedDestination,
        maskedDestination,
        resetAuthorization,
        accountEnv,
        workflowVersion,
        next);
  }

  public ForgotPasswordAuthFlowContext withResetAuthorization(
      ForgotPasswordResetAuthorization authorization) {
    return new ForgotPasswordAuthFlowContext(
        normalizedUsername,
        customerId,
        identityValue,
        identityType,
        identityVerified,
        resetSubjectId,
        contactChannel,
        protectedDestination,
        maskedDestination,
        authorization,
        accountEnv,
        workflowVersion,
        phase);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  @Override
  public String toString() {
    return "ForgotPasswordAuthFlowContext[normalizedUsername=<redacted>, customerId=<redacted>, identityValue=<redacted>, identityType="
        + identityType
        + ", identityVerified="
        + identityVerified
        + ", resetSubjectId=<redacted>, contactChannel="
        + contactChannel
        + ", protectedDestination=<redacted>, maskedDestination="
        + maskedDestination
        + ", resetAuthorization=<redacted>, accountEnv=<redacted>, workflowVersion="
        + workflowVersion
        + ", phase="
        + phase
        + "]";
  }
}
