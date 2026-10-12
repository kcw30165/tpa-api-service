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
   * Compatibility constructor for call sites that carry no trust or scheme fields: {@code
   * trustCode}, {@code schemeName} and {@code schemeNameChi} default to {@code null} (see
   * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and E.9,
   * II-4.b0).
   *
   * <p><strong>Caution:</strong> any caller rebuilding a context from an existing one (e.g. a
   * withX-style state transition) should use the canonical 14-arg constructor and forward {@code
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
        null,
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
        trustCode,
        schemeName,
        schemeNameChi,
        workflowVersion,
        phase);
  }

  /**
   * Returns a copy carrying the trust and scheme fields returned by the member lookup, keeping the
   * stored value for any field the lookup did not supply. The details step needs this because the
   * context created at START carries none of them.
   */
  public ForgotPasswordAuthFlowContext withLookupFields(
      String lookupTrustCode, String lookupSchemeName, String lookupSchemeNameChi) {
    return new ForgotPasswordAuthFlowContext(
        username,
        identityValue,
        identityType,
        identityVerified,
        resetSubjectId,
        contactChannel,
        protectedDestination,
        maskedDestination,
        resetAuthorization,
        hasText(lookupTrustCode) ? lookupTrustCode : trustCode,
        hasText(lookupSchemeName) ? lookupSchemeName : schemeName,
        hasText(lookupSchemeNameChi) ? lookupSchemeNameChi : schemeNameChi,
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
        + ", resetAuthorization=<redacted>, trustCode=<redacted>,"
        + " schemeName=<redacted>, schemeNameChi=<redacted>, workflowVersion="
        + workflowVersion
        + ", phase="
        + phase
        + "]";
  }
}
