package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms schemeName/schemeNameChi addition (section 8.2) and the compatibility constructor,
 * added alongside the pre-existing UsernameRecoveryLookupCandidateTest, which this does not
 * modify.
 */
class UsernameRecoveryLookupCandidateSchemeNameTest {

  @Test
  void canonicalConstructorAcceptsSchemeNameFields() {
    var candidate =
        new UsernameRecoveryLookupCandidate(
            "DB", "DBA", true, "john.doe", "JP MORGAN CHASE", "摩根大通");

    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(candidate.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void compatibilityConstructorDefaultsSchemeNameFieldsToNull() {
    var candidate = new UsernameRecoveryLookupCandidate("DB", "DBA", true, "john.doe");

    assertThat(candidate.schemeName()).isNull();
    assertThat(candidate.schemeNameChi()).isNull();
  }

  @Test
  void toStringNeverDisclosesSchemeNameValues() {
    var candidate =
        new UsernameRecoveryLookupCandidate(
            "DB", "DBA", true, "john.doe", "JP MORGAN CHASE", "摩根大通");

    assertThat(candidate.toString()).doesNotContain("JP MORGAN CHASE", "摩根大通");
  }
}
