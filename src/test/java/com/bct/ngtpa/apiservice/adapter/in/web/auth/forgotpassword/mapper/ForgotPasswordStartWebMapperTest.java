package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.ForgotPasswordErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordError;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordStartWebMapperTest {
  private final ForgotPasswordStartWebMapper mapper =
      new ForgotPasswordStartWebMapper(Duration.ofMinutes(5));

  @Test
  void systemFailureDoesNotExposeRawDownstreamMessages() {
    var response =
        mapper.toMutationResponse(
            new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.SYSTEM_FAILED,
                WorkflowNextStep.NONE,
                null,
                null,
                null,
                List.of(
                    new StartForgotPasswordError(
                        "err.workflow.system.unavailable",
                        "PAGE",
                        "raw downstream message with cust-id=db01 and user-id=3010008")),
                null),
            "en");

    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.workflow.system.unavailable");
              assertThat(error.message()).isNull();
              assertThat(error.targets()).isEmpty();
            });
  }

  @Test
  void usernameNotFoundMapsToBusinessRejectedWithoutDownstreamStatus() {
    var response =
        mapper.toMutationResponse(
            new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.USERNAME_NOT_FOUND,
                WorkflowNextStep.ENTER_USERNAME,
                null,
                null,
                null,
                List.of(
                    new StartForgotPasswordError(
                        "err.forgotpassword.username.notFound", "username", null)),
                null),
            "en");

    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.status())
        .isNotIn(ApiStatus.DOWNSTREAM_REJECTED, ApiStatus.DOWNSTREAM_ERROR);
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.ENTER_USERNAME);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("err.forgotpassword.username.notFound");
              assertThat(error.targets()).containsExactly("username");
            });
  }

  @Test
  void authFlowInvalidMapsToRestartFlowWithoutPageTargetLeak() {
    var response =
        mapper.toMutationResponse(
            new StartForgotPasswordResult(
                StartForgotPasswordResultStatus.AUTH_FLOW_INVALID,
                WorkflowNextStep.RESTART_FLOW,
                null,
                null,
                null,
                List.of(
                    new StartForgotPasswordError(
                        ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value(),
                        "PAGE",
                        "stale auth flow must restart")),
                null),
            "en");

    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo(ForgotPasswordErrorKey.AUTH_FLOW_INVALID.value());
              assertThat(error.targets()).isEmpty();
              assertThat(error.message()).isNull();
            });
  }
}
