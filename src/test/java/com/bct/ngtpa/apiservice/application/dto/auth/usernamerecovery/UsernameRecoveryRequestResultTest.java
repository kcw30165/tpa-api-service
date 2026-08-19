package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestResultTest {

  @Test
  void acceptedRequestCarriesWorkflowStatusAndMaskedDestination() {
    var result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
            UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
            new UsernameRecoverySchemeRef("sch_1"),
            "p****c@ah.ozk",
            UsernameRecoveryDestinationType.EMAIL);

    assertThat(result.outcome()).isEqualTo(UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED);
    assertThat(result.workflowStatus())
        .isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
    assertThat(result.schemeRef().value()).isEqualTo("sch_1");
    assertThat(result.maskedDestination()).isEqualTo("p****c@ah.ozk");
    assertThat(result.destinationType()).isEqualTo(UsernameRecoveryDestinationType.EMAIL);
  }

  @Test
  void acceptedRequestAcceptsPartiallyRequestedAndCompletedOnly() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                    UsernameRecoveryWorkflowStatus.ACTIVE,
                    new UsernameRecoverySchemeRef("sch_1"),
                    "p****c@ah.ozk",
                    UsernameRecoveryDestinationType.EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accepted request workflowStatus must be PARTIALLY_REQUESTED or COMPLETED");
  }

  @Test
  void acceptedRequestRequiresSuccessData() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                    null,
                    new UsernameRecoverySchemeRef("sch_1"),
                    "p****c@ah.ozk",
                    UsernameRecoveryDestinationType.EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accepted request result requires a workflowStatus");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                    UsernameRecoveryWorkflowStatus.COMPLETED,
                    null,
                    "p****c@ah.ozk",
                    UsernameRecoveryDestinationType.EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accepted request result requires a schemeRef");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                    UsernameRecoveryWorkflowStatus.COMPLETED,
                    new UsernameRecoverySchemeRef("sch_1"),
                    " ",
                    UsernameRecoveryDestinationType.EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accepted request result maskedDestination must not be blank");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                    UsernameRecoveryWorkflowStatus.COMPLETED,
                    new UsernameRecoverySchemeRef("sch_1"),
                    "p****c@ah.ozk",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accepted request result requires a destinationType");
  }

  @Test
  void businessAndSystemOutcomesCarryNoSuccessData() {
    for (var outcome :
        new UsernameRecoveryRequestResult.Outcome[] {
          UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED,
          UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED,
          UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE,
          UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF,
          UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED,
          UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME
        }) {
      var result = new UsernameRecoveryRequestResult(outcome, null, null, null, null);
      assertThat(result.workflowStatus()).isNull();
      assertThat(result.schemeRef()).isNull();
      assertThat(result.maskedDestination()).isNull();
      assertThat(result.destinationType()).isNull();
    }
  }

  @Test
  void nonAcceptedOutcomeRejectsSuccessData() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryRequestResult(
                    UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED,
                    UsernameRecoveryWorkflowStatus.COMPLETED,
                    new UsernameRecoverySchemeRef("sch_1"),
                    "p****c@ah.ozk",
                    UsernameRecoveryDestinationType.EMAIL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("non-accepted request result must not carry success data");
  }

  @Test
  void requiresOutcome() {
    assertThatThrownBy(() -> new UsernameRecoveryRequestResult(null, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void toStringDoesNotDiscloseSchemeRef() {
    var result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
            UsernameRecoveryWorkflowStatus.COMPLETED,
            new UsernameRecoverySchemeRef("sch_secret"),
            "p****c@ah.ozk",
            UsernameRecoveryDestinationType.EMAIL);

    assertThat(result.toString())
        .doesNotContain("sch_secret")
        .contains("outcome=REQUEST_ACCEPTED", "workflowStatus=COMPLETED");
  }
}
