package com.bct.ngtpa.apiservice.adapter.in.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowMutationResponseMapperCoverageTest {
  private final WorkflowMutationResponseMapper mapper = new WorkflowMutationResponseMapper();

  @Test
  void mapsUpdatedStatus() {
    var result =
        new WorkflowResult<String>(
            WorkflowResultStatus.UPDATED, WorkflowNextStep.NONE, "updated", List.of(), List.of());
    assertThat(mapper.toMutationResponse(result).status()).isEqualTo(ApiStatus.UPDATED);
  }

  @Test
  void mapsSuccessAndWarningMessages() {
    var result =
        new WorkflowResult<String>(
            WorkflowResultStatus.SUCCESS,
            WorkflowNextStep.NONE,
            "ok",
            List.of(
                new WorkflowMessageDescriptor(
                    WorkflowMessageType.SUCCESS, "success.code", "done", "PAGE"),
                new WorkflowMessageDescriptor(
                    WorkflowMessageType.WARNING, "warning.code", "careful", "PAGE")),
            List.of());
    var response = mapper.toMutationResponse(result);
    assertThat(response.messages())
        .extracting(com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage::type)
        .containsExactly("SUCCESS", "WARNING");
  }

  @Test
  void mapsNullErrorTargetsToEmptyListAndDefaultErrorType() {
    var result =
        WorkflowResult.failure(
            WorkflowResultStatus.BUSINESS_FAILED,
            WorkflowNextStep.NONE,
            List.of(
                new WorkflowErrorDescriptor(
                    WorkflowErrorType.BUSINESS,
                    "business.code",
                    "rejected",
                    null,
                    WorkflowErrorSeverity.ERROR,
                    "SERVER")));
    var response = mapper.toMutationResponse(result);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.type()).isEqualTo("BUSINESS");
              assertThat(error.targets()).isEmpty();
            });
  }
}
