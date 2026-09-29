package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationWorkflowMutationResponseMapperTest {

  @Test
  void defaultConstructorDelegatesToRealWorkflowMutationResponseMapperAndWrapsNonNullPayload() {
    var mapper = new RegistrationWorkflowMutationResponseMapper();
    var workflowResult =
        WorkflowResult.success(WorkflowNextStep.NONE, "payload-data");
    var response = mapper.toMutationResponse(workflowResult);
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result()).isNotNull();
  }

  @Test
  void explicitDelegateConstructorProducesEquivalentBehaviorForFailureStatus() {
    var mapper =
        new RegistrationWorkflowMutationResponseMapper(new WorkflowMutationResponseMapper());
    var workflowResult =
        WorkflowResult.failure(
            com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus
                .VALIDATION_FAILED,
            WorkflowNextStep.NONE,
            List.of());
    var response = mapper.toMutationResponse(workflowResult);
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.result()).isNotNull();
  }

  @Test
  void nullDelegateResultProducesNullPayload() {
    WorkflowMutationResponseMapper nullResultDelegate =
        new WorkflowMutationResponseMapper() {
          @Override
          public <T> MutationResponse<WorkflowResultPayload<T>> toMutationResponse(
              WorkflowResult<T> result) {
            return new MutationResponse<>(
                true, ApiStatus.SUCCESS, null, List.<ApiMessage>of(), List.<ApiError>of());
          }
        };
    var mapper = new RegistrationWorkflowMutationResponseMapper(nullResultDelegate);
    var workflowResult = WorkflowResult.success(WorkflowNextStep.NONE, "ignored");
    var response = mapper.toMutationResponse(workflowResult);
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
    assertThat(response.result()).isNull();
  }
}
