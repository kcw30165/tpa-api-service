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
  void startedContextCarriesOnlyTheIdentityAndTheWorkflowVersion() {
    var started =
        ForgotPasswordAuthFlowContext.started("member01", "identity-value", "STAFFNO", "v1");

    assertThat(started.username()).isEqualTo("member01");
    assertThat(started.identityVerified()).isFalse();
    assertThat(started.phase()).isEqualTo(ForgotPasswordProtectedPhase.USERNAME_VERIFIED);
    assertThat(started.resetSubjectId()).isNull();
    assertThat(started.contactChannel()).isNull();
    assertThat(started.resetAuthorization()).isNull();
    assertThat(started.trustCode()).isNull();
    assertThat(started.schemeName()).isNull();
    assertThat(started.schemeNameChi()).isNull();
  }

  @Test
  void withResetSubjectIdPreservesEveryOtherField() {
    var context = context("DBA", "Scheme En", "Scheme Chi");

    var updated = context.withResetSubjectId("subject-2");

    assertThat(updated.resetSubjectId()).isEqualTo("subject-2");
    assertThat(updated.trustCode()).isEqualTo("DBA");
    assertThat(updated.schemeName()).isEqualTo("Scheme En");
    assertThat(updated.schemeNameChi()).isEqualTo("Scheme Chi");
    assertThat(updated.username()).isEqualTo(context.username());
    assertThat(updated.identityValue()).isEqualTo(context.identityValue());
    assertThat(updated.phase()).isEqualTo(context.phase());
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
