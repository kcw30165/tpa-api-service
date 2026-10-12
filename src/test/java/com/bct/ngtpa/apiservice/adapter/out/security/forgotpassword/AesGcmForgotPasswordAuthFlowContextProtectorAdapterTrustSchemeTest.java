package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import org.junit.jupiter.api.Test;

/**
 * Confirms the trustCode/schemeName/schemeNameChi fields (added for Stage II-4, a real round-trip
 * data-loss bug found independently: this adapter's private Payload record never serialized them)
 * survive a real protect/unprotect round trip. See
 * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and E.9,
 * II-4.b0.
 */
class AesGcmForgotPasswordAuthFlowContextProtectorAdapterTrustSchemeTest {

  private final AesGcmForgotPasswordAuthFlowContextProtectorAdapter adapter =
      new AesGcmForgotPasswordAuthFlowContextProtectorAdapter("v1", "A".repeat(32));

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
  void roundTripPreservesTrustSchemeFields() {
    var context = context("DBA", "JP MORGAN CHASE", "\u6469\u6839\u5927\u901a");

    var encrypted = adapter.protect(context);
    var decrypted = adapter.unprotect(encrypted);

    assertThat(decrypted.trustCode()).isEqualTo("DBA");
    assertThat(decrypted.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(decrypted.schemeNameChi()).isEqualTo("\u6469\u6839\u5927\u901a");
  }

  @Test
  void roundTripHandlesNullTrustSchemeFieldsGracefully() {
    var context = context(null, null, null);

    var encrypted = adapter.protect(context);
    var decrypted = adapter.unprotect(encrypted);

    assertThat(decrypted.trustCode()).isNull();
    assertThat(decrypted.schemeName()).isNull();
    assertThat(decrypted.schemeNameChi()).isNull();
  }
}
