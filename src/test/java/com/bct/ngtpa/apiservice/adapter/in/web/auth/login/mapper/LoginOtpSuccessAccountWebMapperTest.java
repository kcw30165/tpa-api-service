package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginOtpSuccessAccountWebMapperTest {
  private final LoginOtpVerifyWebMapper mapper = new LoginOtpVerifyWebMapper();

  @Test
  void readyResponseExposesOnlyOpaqueAccountRefs() {
    var account =
        new LoginSessionAccount(
            new LoginAccountRef("acc_abcdefghijklmnopqrstuvwx"),
            "DB",
            "12",
            "00000000012",
            "1",
            "0001",
            "TR",
            "OE",
            null,
            null);
    var response =
        mapper.toMutationResponse(
            new VerifyLoginOtpResult(
                VerifyLoginOtpStatus.ACCOUNTS_READY,
                null,
                false,
                List.of(account),
                new com.bct.ngtpa.apiservice.application.dto.session.authenticated
                    .AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
                "authenticated_csrf_token"));
    assertThat(response.success()).isTrue();
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
    assertThat(response.result().data().csrfToken()).isEqualTo("authenticated_csrf_token");
    assertThat(response.result().data().toString()).doesNotContain("authenticated_csrf_token");
    assertThat(response.result().data().accounts())
        .extracting(a -> a.accountRef())
        .containsExactly("acc_abcdefghijklmnopqrstuvwx");
    assertThat(response.toString()).doesNotContain("00000000012", "0001", "DB", "TR");
  }

  @Test
  void accountFailuresMapToCanonicalCodes() {
    assertThat(
            mapper
                .toMutationResponse(
                    new VerifyLoginOtpResult(
                        VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE, null, false, List.of(), null, null))
                .errors()
                .getFirst()
                .code())
        .isEqualTo("err.login.accountList.unavailable");
    assertThat(
            mapper
                .toMutationResponse(
                    new VerifyLoginOtpResult(
                        VerifyLoginOtpStatus.ZERO_VALID_ACCOUNTS, null, false, List.of(), null, null))
                .errors()
                .getFirst()
                .code())
        .isEqualTo("err.login.accountList.empty");
  }
}
