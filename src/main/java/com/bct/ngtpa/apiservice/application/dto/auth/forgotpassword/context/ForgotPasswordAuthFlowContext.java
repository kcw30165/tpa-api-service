package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.ForgotPasswordResetAuthorization;

public record ForgotPasswordAuthFlowContext(
    String username,
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
    username = requireText(username, "username");
    identityValue = requireText(identityValue, "identityValue");
    identityType = requireText(identityType, "identityType");
    workflowVersion = requireText(workflowVersion, "workflowVersion");
    phase =
        phase == null && completeStartContext(username, identityValue, identityType)
            ? ForgotPasswordProtectedPhase.USERNAME_VERIFIED
            : phase;
  }

  /**
   * Backward-compatible constructor matching the previous 11-arg signature. Preserves old call
   * sites by inserting a null accountEnv.
   */
  public ForgotPasswordAuthFlowContext(
      String username,
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
        username,
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
      String username,
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
        username,
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
      String username, String userId, String identityType) {
    return hasText(username) && hasText(userId) && hasText(identityType);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public ForgotPasswordAuthFlowContext withPhase(ForgotPasswordProtectedPhase next) {
    return new ForgotPasswordAuthFlowContext(
        username,
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
        username,
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
    return "ForgotPasswordAuthFlowContext[username=<redacted>, identityValue=<redacted>, identityType="
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
