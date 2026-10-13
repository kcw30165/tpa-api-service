package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.*;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Guards the protected session context round trip for schemeName/schemeNameChi (see
 * docs/design/10-template-service/template-service-solution-design.md section 8.2). The private
 * Candidate payload record serializes every field explicitly, so a field missing from it is
 * silently dropped between Username Recovery START and REQUEST.
 */
class AesGcmUsernameRecoveryAuthFlowContextProtectorAdapterSchemeNameTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private final AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter adapter =
      new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", SECRET);
  private final AuthFlowSessionId id = new AuthFlowSessionId("afs-secret");

  @Test
  void roundTripPreservesSchemeNameAndSchemeNameChi() {
    UsernameRecoveryAuthFlowContext restored =
        roundTrip(context("JP MORGAN CHASE - JMCI", "CHI-NAME-SAMPLE"));

    UsernameRecoverySessionCandidate candidate = restored.candidates().get(0);
    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE - JMCI");
    assertThat(candidate.schemeNameChi()).isEqualTo("CHI-NAME-SAMPLE");
    assertThat(candidate.trustCode()).isEqualTo("DBA");
    assertThat(candidate.userName()).isEqualTo("user-1");
  }

  @Test
  void roundTripKeepsNullSchemeNamesNull() {
    UsernameRecoverySessionCandidate candidate = roundTrip(context(null, null)).candidates().get(0);

    assertThat(candidate.schemeName()).isNull();
    assertThat(candidate.schemeNameChi()).isNull();
  }

  @Test
  void roundTripKeepsBlankSchemeNameChiUnchangedSoCallersCanApplyTheFallbackRule() {
    UsernameRecoverySessionCandidate candidate =
        roundTrip(context("JP MORGAN CHASE - JMCI", "")).candidates().get(0);

    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE - JMCI");
    assertThat(candidate.schemeNameChi()).isEmpty();
  }

  @Test
  void roundTripPreservesSchemeNamesAfterStateTransition() {
    UsernameRecoveryAuthFlowContext reserved =
        roundTrip(context("JP MORGAN CHASE - JMCI", "CHI-NAME-SAMPLE"))
            .reserve(new UsernameRecoverySchemeRef("sch-opaque-1"));

    UsernameRecoverySessionCandidate candidate = roundTrip(reserved).candidates().get(0);
    assertThat(candidate.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.RESERVED);
    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE - JMCI");
    assertThat(candidate.schemeNameChi()).isEqualTo("CHI-NAME-SAMPLE");
  }

  private UsernameRecoveryAuthFlowContext roundTrip(UsernameRecoveryAuthFlowContext context) {
    return adapter.unprotect(id, adapter.protect(id, context));
  }

  private static UsernameRecoveryAuthFlowContext context(String schemeName, String schemeNameChi) {
    return new UsernameRecoveryAuthFlowContext(
        UsernameRecoveryWorkflowStatus.ACTIVE,
        new UsernameRecoveryVerifiedDestination(
            "pepperc@ah.ozk", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
        Locale.ENGLISH,
        List.of(
            new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                    new UsernameRecoverySchemeRef("sch-opaque-1"),
                    "DB",
                    "DBA",
                    true,
                    "user-1",
                    UsernameRecoveryDeliveryState.AVAILABLE,
                    schemeName,
                    schemeNameChi),
                null,
                null)));
  }
}
