package com.bct.ngtpa.apiservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Confirms schemeName()/schemeNameChi() accessors delegate correctly, and - critically - that
 * withState() no longer loses those fields on a state transition (the fixed field-loss landmine;
 * see docs/design/10-template-service/template-service-implementation-plan.md Appendix A, II-1.b).
 */
class UsernameRecoverySessionCandidateSchemeNameTest {

  @Test
  void accessorsDelegateToWrappedCandidate() {
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
    var sessionCandidate = new UsernameRecoverySessionCandidate(candidate, null, null);

    assertThat(sessionCandidate.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(sessionCandidate.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void withStatePreservesSchemeNameAcrossReserveTransition() {
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
    var sessionCandidate = new UsernameRecoverySessionCandidate(candidate, null, null);

    var reserved = sessionCandidate.withState(UsernameRecoveryDeliveryState.RESERVED, null, null);

    assertThat(reserved.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(reserved.schemeNameChi()).isEqualTo("摩根大通");
    assertThat(reserved.deliveryState()).isEqualTo(UsernameRecoveryDeliveryState.RESERVED);
  }

  @Test
  void withStatePreservesSchemeNameAcrossRequestedTransition() {
    var schemeRef = new UsernameRecoverySchemeRef("sch_opaque-1");
    var candidate =
        new UsernameRecoveryCandidate(
            schemeRef,
            "DB",
            "DBA",
            true,
            "john.doe",
            UsernameRecoveryDeliveryState.RESERVED,
            "JP MORGAN CHASE",
            "摩根大通");
    var sessionCandidate = new UsernameRecoverySessionCandidate(candidate, null, null);

    var requested =
        sessionCandidate.withState(
            UsernameRecoveryDeliveryState.REQUESTED, java.time.Instant.now(), "sender-ref-1");

    assertThat(requested.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(requested.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void withStateHandlesNullSchemeNameFieldsGracefully() {
    var schemeRef = new UsernameRecoverySchemeRef("sch_opaque-1");
    var candidate =
        new UsernameRecoveryCandidate(
            schemeRef, "DB", "DBA", true, "john.doe", UsernameRecoveryDeliveryState.AVAILABLE);
    var sessionCandidate = new UsernameRecoverySessionCandidate(candidate, null, null);

    var reserved = sessionCandidate.withState(UsernameRecoveryDeliveryState.RESERVED, null, null);

    assertThat(reserved.schemeName()).isNull();
    assertThat(reserved.schemeNameChi()).isNull();
  }
}
