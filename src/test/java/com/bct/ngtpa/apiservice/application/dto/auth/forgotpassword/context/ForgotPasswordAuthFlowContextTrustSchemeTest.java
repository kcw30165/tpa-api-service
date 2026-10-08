package com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms trustCode/schemeName/schemeNameChi (added for Stage II-4, see
 * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and E.9,
 * II-4.b0) survive withPhase/withResetAuthorization state transitions - the exact bug shape
 * Username Recovery's UsernameRecoverySessionCandidate.withState() once had and fixed (commit
 * 8889c0a1; see Appendix E.8).
 */
class ForgotPasswordAuthFlowContextTrustSchemeTest {

  private static ForgotPasswordAuthFlowContext context(
      String trustCode, String schemeName, String schemeNameChi) {
    return new ForgotPasswordAuthFlowContext(
        "member01",
        "identity-value",
        "STAFFNO",
        false,
        null,
        null,
        null,
        null,
        null,
        "DB",
        trustCode,
        schemeName,
        schemeNameChi,
        "v1",
        ForgotPasswordProtectedPhase.USERNAME_VERIFIED);
  }

  @Test
  void canonicalConstructorAcceptsTrustSchemeFields() {
    var context = context("DBA", "JP MORGAN CHASE", "摩根大通");

    assertThat(context.trustCode()).isEqualTo("DBA");
    assertThat(context.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(context.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void compatibilityConstructorDefaultsTrustSchemeFieldsToNull() {
    var context =
        new ForgotPasswordAuthFlowContext(
            "member01",
            "identity-value",
            "STAFFNO",
            false,
            null,
            null,
            null,
            null,
            null,
            "DB",
            "v1",
            ForgotPasswordProtectedPhase.USERNAME_VERIFIED);

    assertThat(context.trustCode()).isNull();
    assertThat(context.schemeName()).isNull();
    assertThat(context.schemeNameChi()).isNull();
  }

  @Test
  void withPhasePreservesTrustSchemeFieldsAcrossTransition() {
    var context = context("DBA", "JP MORGAN CHASE", "摩根大通");

    var transitioned = context.withPhase(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);

    assertThat(transitioned.trustCode()).isEqualTo("DBA");
    assertThat(transitioned.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(transitioned.schemeNameChi()).isEqualTo("摩根大通");
    assertThat(transitioned.phase()).isEqualTo(ForgotPasswordProtectedPhase.IDENTITY_VERIFIED);
  }

  @Test
  void withPhaseHandlesNullTrustSchemeFieldsGracefully() {
    var context = context(null, null, null);

    var transitioned = context.withPhase(ForgotPasswordProtectedPhase.OTP_SENT);

    assertThat(transitioned.trustCode()).isNull();
    assertThat(transitioned.schemeName()).isNull();
    assertThat(transitioned.schemeNameChi()).isNull();
  }

  @Test
  void withResetAuthorizationPreservesTrustSchemeFields() {
    var context = context("DBA", "JP MORGAN CHASE", "摩根大通");

    var updated = context.withResetAuthorization(null);

    assertThat(updated.trustCode()).isEqualTo("DBA");
    assertThat(updated.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(updated.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void toStringNeverDisclosesTrustSchemeValues() {
    var context = context("DBA", "JP MORGAN CHASE", "摩根大通");

    assertThat(context.toString()).doesNotContain("DBA", "JP MORGAN CHASE", "摩根大通");
  }
}
