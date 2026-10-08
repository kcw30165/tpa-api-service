package com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import org.junit.jupiter.api.Test;

/**
 * Confirms accountEnv (a pre-existing field this adapter's private Payload record never
 * serialized - a real, independently-found round-trip data-loss bug, fixed by the same change
 * that added trustCode/schemeName/schemeNameChi for Stage II-4) and the three new fields all
 * survive a real protect/unprotect round trip. See
 * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and E.9,
 * II-4.b0.
 */
class AesGcmForgotPasswordAuthFlowContextProtectorAdapterTrustSchemeTest {

  private final AesGcmForgotPasswordAuthFlowContextProtectorAdapter adapter =
      new AesGcmForgotPasswordAuthFlowContextProtectorAdapter("v1", "A".repeat(32));

  private static ForgotPasswordAuthFlowContext context(
      String accountEnv, String trustCode, String schemeName, String schemeNameChi) {
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
        accountEnv,
        trustCode,
        schemeName,
        schemeNameChi,
        "v1",
        ForgotPasswordProtectedPhase.USERNAME_VERIFIED);
  }

  @Test
  void roundTripPreservesAccountEnvAndTrustSchemeFields() {
    var context = context("DB", "DBA", "JP MORGAN CHASE", "摩根大通");

    var encrypted = adapter.protect(context);
    var decrypted = adapter.unprotect(encrypted);

    assertThat(decrypted.accountEnv()).isEqualTo("DB");
    assertThat(decrypted.trustCode()).isEqualTo("DBA");
    assertThat(decrypted.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(decrypted.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void roundTripHandlesNullAccountEnvAndTrustSchemeFieldsGracefully() {
    var context = context(null, null, null, null);

    var encrypted = adapter.protect(context);
    var decrypted = adapter.unprotect(encrypted);

    assertThat(decrypted.accountEnv()).isNull();
    assertThat(decrypted.trustCode()).isNull();
    assertThat(decrypted.schemeName()).isNull();
    assertThat(decrypted.schemeNameChi()).isNull();
  }
}
