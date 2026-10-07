package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.CompleteForgotPasswordStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordCompleteWebMapperTest {

  @Test
  void duplicateInProgressMapsToBusinessRejected() {
    var mapper = new ForgotPasswordCompleteWebMapper();
    var result =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(
                new WorkflowErrorDescriptor(
                    WorkflowErrorType.BUSINESS,
                    "err.forgot-password.duplicate",
                    null,
                    List.of("id"),
                    WorkflowErrorSeverity.ERROR,
                    "BFF")));

    MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
        response = mapper.toResponse(result);

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.result()).isNotNull();
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.NONE);
    assertThat(response.errors()).singleElement().satisfies(error -> {
      assertThat(error.code()).isEqualTo("err.forgot-password.duplicate");
      assertThat(error.message()).isNull();
      assertThat(error.targets()).containsExactly("id");
    });
  }

  @Test
  void businessRejectedMapsToBusinessRejected() {
    var mapper = new ForgotPasswordCompleteWebMapper();
    var result =
        new CompleteForgotPasswordResult(
            CompleteForgotPasswordStatus.BUSINESS_REJECTED,
            WorkflowNextStep.NONE,
            false,
            false,
            List.of(
                new WorkflowErrorDescriptor(
                    WorkflowErrorType.BUSINESS,
                    "err.forgot-password.rejected",
                    null,
                    List.of(),
                    WorkflowErrorSeverity.ERROR,
                    "BFF")));

    MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>
        response = mapper.toResponse(result);

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.result()).isNotNull();
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.NONE);
    assertThat(response.errors()).singleElement().satisfies(error -> {
      assertThat(error.code()).isEqualTo("err.forgot-password.rejected");
      assertThat(error.message()).isNull();
      assertThat(error.targets()).isEmpty();
    });
  }
}
