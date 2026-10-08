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
    String trustCode,
    String schemeName,
    String schemeNameChi,
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
   * Compatibility constructor for existing call sites predating the addition of {@code
   * trustCode}/{@code schemeName}/{@code schemeNameChi} (see
   * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and
   * E.9, II-4.b0). All three new fields default to {@code null}.
   *
   * <p><strong>Caution:</strong> any caller rebuilding a context from an existing one (e.g. a
   * withX-style state transition) should use the canonical 15-arg constructor and forward {@code
   * trustCode()}/{@code schemeName()}/{@code schemeNameChi()} explicitly, not this compatibility
   * constructor, or the values will be silently nulled out (the same risk Username Recovery's
   * UsernameRecoveryCandidate already documents and guards against).
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
      String accountEnv,
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
        accountEnv,
        null,
        null,
        null,
        workflowVersion,
        phase);
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

  private static boolean completeStartContext(String username, String userId, String identityType) {
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
        trustCode,
        schemeName,
        schemeNameChi,
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
        trustCode,
        schemeName,
        schemeNameChi,
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
        + ", resetAuthorization=<redacted>, accountEnv=<redacted>, trustCode=<redacted>,"
        + " schemeName=<redacted>, schemeNameChi=<redacted>, workflowVersion="
        + workflowVersion
        + ", phase="
        + phase
        + "]";
  }
}
