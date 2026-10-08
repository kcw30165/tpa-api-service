package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms trustCode/schemeName/schemeNameChi (added for Stage II-4, see
 * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and E.9,
 * II-4.b0) on the canonical constructor, and that both pre-existing compatibility constructors
 * (1-arg status-only, 2-arg status+accountEnv) continue to default the three new fields to null.
 */
class ForgotPasswordDetailsLookupResultTrustSchemeTest {

  @Test
  void canonicalConstructorAcceptsTrustSchemeFields() {
    var result =
        new ForgotPasswordDetailsLookupResult(
            ForgotPasswordDetailsLookupStatus.MATCHED,
            "DB",
            "DBA",
            "JP MORGAN CHASE",
            "摩根大通");

    assertThat(result.accountEnv()).isEqualTo("DB");
    assertThat(result.trustCode()).isEqualTo("DBA");
    assertThat(result.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(result.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void twoArgCompatibilityConstructorDefaultsTrustSchemeFieldsToNull() {
    var result =
        new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.MATCHED, "DB");

    assertThat(result.accountEnv()).isEqualTo("DB");
    assertThat(result.trustCode()).isNull();
    assertThat(result.schemeName()).isNull();
    assertThat(result.schemeNameChi()).isNull();
  }

  @Test
  void oneArgCompatibilityConstructorDefaultsEverythingToNull() {
    var result =
        new ForgotPasswordDetailsLookupResult(ForgotPasswordDetailsLookupStatus.NOT_FOUND);

    assertThat(result.accountEnv()).isNull();
    assertThat(result.trustCode()).isNull();
    assertThat(result.schemeName()).isNull();
    assertThat(result.schemeNameChi()).isNull();
  }
}
