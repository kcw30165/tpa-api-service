package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The protected context must be able to hold an all-unregistered candidate list (such a start is a
 * normal session), while delivery and completion stay impossible for unregistered candidates.
 */
class UsernameRecoveryAuthFlowContextAllUnregisteredTest {
  private static final UsernameRecoveryVerifiedDestination DESTINATION =
      new UsernameRecoveryVerifiedDestination(
          "member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

  private static UsernameRecoverySessionCandidate unregistered(
      String ref, String env, String trustCode) {
    return new UsernameRecoverySessionCandidate(
        new UsernameRecoveryCandidate(
            new UsernameRecoverySchemeRef(ref),
            env,
            trustCode,
            false,
            null,
            UsernameRecoveryDeliveryState.AVAILABLE,
            null,
            null),
        null,
        null);
  }

  private static UsernameRecoveryAuthFlowContext context(
      UsernameRecoveryWorkflowStatus status, UsernameRecoverySessionCandidate... candidates) {
    return new UsernameRecoveryAuthFlowContext(
        status, DESTINATION, Locale.ENGLISH, List.of(candidates));
  }

  @Test
  void anActiveContextMayHoldOnlyUnregisteredCandidates() {
    var context =
        context(
            UsernameRecoveryWorkflowStatus.ACTIVE,
            unregistered("sch_u1", "DB", "DBA"),
            unregistered("sch_u2", "DB", "DBB"));

    assertThat(context.workflowStatus()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
    assertThat(context.candidates()).hasSize(2);
    assertThat(context.candidates()).allSatisfy(c -> assertThat(c.registered()).isFalse());
  }

  @Test
  void aWorkflowCannotBeCompletedWhenThereIsNoRegisteredCandidateToRequest() {
    var onlyUnregistered = unregistered("sch_u1", "DB", "DBA");

    assertThatThrownBy(() -> context(UsernameRecoveryWorkflowStatus.COMPLETED, onlyUnregistered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("candidate state and workflow completion must agree");
  }

  @Test
  void deliveryStateChangesStayImpossibleForAnUnregisteredCandidate() {
    var context =
        context(UsernameRecoveryWorkflowStatus.ACTIVE, unregistered("sch_u1", "DB", "DBA"));
    var reference = new UsernameRecoverySchemeRef("sch_u1");

    assertThatThrownBy(() -> context.reserve(reference))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unregistered candidate cannot participate in delivery");
  }

  @Test
  void duplicateSchemeRefsAndDuplicateEnvTrustPairsAreStillRejected() {
    var first = unregistered("sch_u1", "DB", "DBA");
    var sameRef = unregistered("sch_u1", "DB", "DBB");
    var sameEnvTrust = unregistered("sch_u2", "DB", "DBA");

    assertThatThrownBy(() -> context(UsernameRecoveryWorkflowStatus.ACTIVE, first, sameRef))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("duplicate schemeRef is rejected");
    assertThatThrownBy(() -> context(UsernameRecoveryWorkflowStatus.ACTIVE, first, sameEnvTrust))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("duplicate accountEnv+trustCode candidate is rejected");
  }
}
