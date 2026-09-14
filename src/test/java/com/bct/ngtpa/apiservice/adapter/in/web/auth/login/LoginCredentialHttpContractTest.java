package com.bct.ngtpa.apiservice.adapter.in.web.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginStartWebMapper;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LoginCredentialHttpContractTest {
  @Test
  void incorrectPasswordIsSafeBusinessRejectionWithHttp401() {
    var mapped =
        new LoginStartWebMapper()
            .toMutationResponse(
                new StartLoginResult(
                    StartLoginStatus.INCORRECT_PASSWORD, null, null, null, List.of(), 1, null),
                "en");
    var response = LoginHttpResponses.toResponse(mapped);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(mapped.status().name()).isEqualTo("BUSINESS_REJECTED");
    assertThat(mapped.status().name()).doesNotContain("DOWNSTREAM_ERROR");
    assertThat(mapped.errors()).hasSize(1);
    assertThat(mapped.errors().getFirst().code()).isEqualTo("err.login.credentials.invalid");
    assertThat(mapped.errors().getFirst().source()).isEqualTo("BFF");
  }
}
