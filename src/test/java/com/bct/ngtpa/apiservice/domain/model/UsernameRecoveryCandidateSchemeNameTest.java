package com.bct.ngtpa.apiservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms schemeName/schemeNameChi addition (section 8.2) on
 * the domain-layer UsernameRecoveryCandidate (distinct from the DTO-layer
 * UsernameRecoveryLookupCandidate, which has its own dedicated test).
 */
class UsernameRecoveryCandidateSchemeNameTest {

  @Test
  void canonicalConstructorAcceptsSchemeNameFields() {
    var schemeRef = new UsernameRecoverySchemeRef("sch_opaque-1");
    var candidate =
        new UsernameRecoveryCandidate(
            schemeRef,
            "DB",
            "DBA",
            true,
            "john.doe",
            UsernameRecoveryDeliveryState.AVAILABLE,
            "JP MORGAN CHASE",
            "摩根大通");

    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(candidate.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void canonicalConstructorAllowsNullSchemeNameFields() {
    var schemeRef = new UsernameRecoverySchemeRef("sch_opaque-1");
    var candidate =
        new UsernameRecoveryCandidate(
            schemeRef, "DB", "DBA", true, "john.doe", UsernameRecoveryDeliveryState.AVAILABLE, null, null);

    assertThat(candidate.schemeName()).isNull();
    assertThat(candidate.schemeNameChi()).isNull();
  }

  @Test
  void toStringNeverDisclosesSchemeNameValues() {
    var schemeRef = new UsernameRecoverySchemeRef("sch_opaque-1");
    var candidate =
        new UsernameRecoveryCandidate(
            schemeRef,
            "DB",
            "DBA",
            true,
            "john.doe",
            UsernameRecoveryDeliveryState.AVAILABLE,
            "JP MORGAN CHASE",
            "摩根大通");

    assertThat(candidate.toString()).doesNotContain("JP MORGAN CHASE", "摩根大通");
  }
}
