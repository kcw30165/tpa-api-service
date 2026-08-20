package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Web mapper tests for Username Recovery /request result projections.
 *
 * <p>Only {@code REQUEST_ACCEPTED} carries an approved success projection. Non-accepted outcomes
 * project null data with nextStep NONE; Task 023 owns the response policies (status, errors,
 * nextStep) for those outcomes.
 */
class UsernameRecoveryRequestWebMapperTest {

  private final UsernameRecoveryRequestWebMapper mapper = new UsernameRecoveryRequestWebMapper();

  @Test
  void partiallyRequestedProjectsSelectAccountWithApprovedData() {
    var result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
            UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            "p****c@ah.ozk",
            UsernameRecoveryDestinationType.EMAIL);

    var response = mapper.toResult(result);

    assertThat(response.nextStep()).isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
    assertThat(response.data()).isNotNull();
    assertThat(response.data().status()).isEqualTo("PARTIALLY_REQUESTED");
    assertThat(response.data().schemeRef()).isEqualTo("sch_opaque-1");
    assertThat(response.data().maskedDestination()).isEqualTo("p****c@ah.ozk");
    assertThat(response.data().destinationType()).isEqualTo("EMAIL");
    assertThat(response.data().requestAccepted()).isTrue();
  }

  @Test
  void completedProjectsReturnToLogin() {
    var result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
            UsernameRecoveryWorkflowStatus.COMPLETED,
            new UsernameRecoverySchemeRef("sch_opaque-2"),
            "p****c@ah.ozk",
            UsernameRecoveryDestinationType.EMAIL);

    var response = mapper.toResult(result);

    assertThat(response.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    assertThat(response.data()).isNotNull();
    assertThat(response.data().status()).isEqualTo("COMPLETED");
    assertThat(response.data().schemeRef()).isEqualTo("sch_opaque-2");
    assertThat(response.data().requestAccepted()).isTrue();
  }

  @Test
  void mobileDestinationProjectsDestinationTypeName() {
    var result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
            UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            "6****7",
            UsernameRecoveryDestinationType.MOBILE);

    var response = mapper.toResult(result);

    assertThat(response.data().destinationType()).isEqualTo("MOBILE");
    assertThat(response.data().maskedDestination()).isEqualTo("6****7");
  }

  @Test
  void maskedDestinationIsCopiedNeverRecomputed() {
    // The mapper only ever sees the application-masked value. It must copy it
    // verbatim into the projection and never attempt to mask a raw destination.
    var result =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
            UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            "p****c@ah.ozk",
            UsernameRecoveryDestinationType.EMAIL);

    var response = mapper.toResult(result);

    assertThat(response.data().maskedDestination()).isEqualTo("p****c@ah.ozk");
    assertThat(response.toString()).doesNotContain("p****c@ah.ozk", "sch_opaque-1");
  }

  @Test
  void nonAcceptedOutcomesProjectNoDataAndNoNextStepDecision() {
    List<UsernameRecoveryRequestResult.Outcome> nonAccepted =
        List.of(
            UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED,
            UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED,
            UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE,
            UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF,
            UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED,
            UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME);

    for (UsernameRecoveryRequestResult.Outcome outcome : nonAccepted) {
      var result = new UsernameRecoveryRequestResult(outcome, null, null, null, null);
      var response = mapper.toResult(result);

      assertThat(response.data()).as("outcome %s must carry no data", outcome).isNull();
      assertThat(response.nextStep())
          .as("outcome %s must not choose a next step", outcome)
          .isEqualTo(WorkflowNextStep.NONE);
    }
  }

  @Test
  void nullRequestResultFailsClosed() {
    assertThatThrownBy(() -> mapper.toResult(null)).isInstanceOf(NullPointerException.class);
  }
}
