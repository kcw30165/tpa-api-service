package com.bct.ngtpa.apiservice.adapter.in.web.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.LoginErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginStartWebMapperTest {
  private final LoginStartWebMapper mapper = new LoginStartWebMapper();

  @Test
  void startedAndReplayMapToSelectableOtpDestinationResult() {
    for (StartLoginStatus status :
        List.of(StartLoginStatus.STARTED, StartLoginStatus.COMPLETED_REPLAY)) {
      var result =
          new StartLoginResult(
              status,
              "afs_secret",
              "csrf_secret",
              900,
              List.of(new LoginDestinationChoice("dst_opaque", "sms", "****4567")),
              0,
              "rpl_secret");
      var response = mapper.toMutationResponse(result, "en");

      assertThat(response.success()).isTrue();
      assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
      assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.SELECT_OTP_DESTINATION);
      assertThat(response.result().data().csrfToken()).isEqualTo("csrf_secret");
      assertThat(response.result().data().destinations()).hasSize(1);
      assertThat(response.result().data().actions()).isEmpty();
      assertThat(response.toString()).doesNotContain("afs_secret", "rpl_secret", "ciphertext");
    }
  }

  @Test
  void blockingStatusesMapToErrorsNotMessages() {
    var response =
        mapper.toMutationResponse(
            new StartLoginResult(
                StartLoginStatus.INCORRECT_PASSWORD, null, null, null, List.of(), 2, null),
            "en");
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.messages()).isEmpty();
    assertThat(response.errors())
        .singleElement()
        .extracting(error -> error.code())
        .isEqualTo("err.login.credentials.invalid");
  }

  /** Confirms UNAVAILABLE remains SYSTEM_ERROR while public error source is BFF. */
  @Test
  void unavailableStatusMapsToDownstreamSourceAndSystemError() {
    var response =
        mapper.toMutationResponse(
            new StartLoginResult(
                StartLoginStatus.UNAVAILABLE, null, null, null, List.of(), 0, null),
            "en");

    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.source()).isEqualTo("BFF");
              assertThat(error.code()).isEqualTo(LoginErrorKey.CREDENTIALS_UNAVAILABLE.value());
            });
  }
}
