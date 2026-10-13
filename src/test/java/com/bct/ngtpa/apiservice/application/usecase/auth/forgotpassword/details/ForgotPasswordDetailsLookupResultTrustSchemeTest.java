package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms trustCode/schemeName/schemeNameChi (added for Stage II-4, see
 * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and E.9,
 * II-4.b0) on the canonical constructor, and that the notFound() factory carries none of them.
 */
class ForgotPasswordDetailsLookupResultTrustSchemeTest {

  @Test
  void canonicalConstructorAcceptsTrustSchemeFields() {
    var result =
        new ForgotPasswordDetailsLookupResult(
            ForgotPasswordDetailsLookupStatus.MATCHED,
            "DBA",
            "JP MORGAN CHASE",
            "\u6469\u6839\u5927\u901a");

    assertThat(result.trustCode()).isEqualTo("DBA");
    assertThat(result.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(result.schemeNameChi()).isEqualTo("\u6469\u6839\u5927\u901a");
  }

  @Test
  void notFoundCarriesNoTrustOrSchemeFields() {
    var result = ForgotPasswordDetailsLookupResult.notFound();

    assertThat(result.trustCode()).isNull();
    assertThat(result.schemeName()).isNull();
    assertThat(result.schemeNameChi()).isNull();
  }
}
