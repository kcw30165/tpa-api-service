package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import org.junit.jupiter.api.Test;

/**
 * Web mapper tests for Username Recovery /cancel result projections.
 *
 * <p>Successful cancellation projects {@code RETURN_TO_LOGIN} with null data. {@code SYSTEM_FAILED}
 * projects null data with nextStep NONE; Task 023 owns the system-error response policy.
 */
class UsernameRecoveryCancelWebMapperTest {

  private final UsernameRecoveryCancelWebMapper mapper = new UsernameRecoveryCancelWebMapper();

  @Test
  void successProjectsReturnToLoginWithNullData() {
    var response =
        mapper.toResult(
            new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS));

    assertThat(response.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
    assertThat(response.data()).isNull();
  }

  @Test
  void systemFailedProjectsNoDataAndNoNextStepDecision() {
    var response =
        mapper.toResult(
            new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED));

    assertThat(response.data()).isNull();
    assertThat(response.nextStep()).isEqualTo(WorkflowNextStep.NONE);
  }

  @Test
  void nullCancelResultFailsClosed() {
    assertThatThrownBy(() -> mapper.toResult(null)).isInstanceOf(NullPointerException.class);
  }
}
