package com.bct.ngtpa.apiservice.adapter.in.web.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountGainLossPublicContractTest {
  @Test
  void mapsApprovedPublicFailures() {
    var switching =
        AccountGainLossRetrievalResponsePolicy.map(
            AccountGainLossRetrievalException.fundSwitchingInProgress());
    assertThat(switching.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(switching.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(switching.getBody().errors().getFirst().code())
        .isEqualTo("err.fund.switching.in.progress");
    var system =
        AccountGainLossRetrievalResponsePolicy.map(
            AccountGainLossRetrievalException.systemFailure());
    assertThat(system.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(system.getBody().status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  }

  @Test
  void controllerUsesTrustedContextAndDefaultPeriod() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/accountgainloss/AccountGainLossController.java"));
    assertThat(source)
        .contains(
            "/account-gain-loss",
            "defaultValue = \"SINCE_SCHEME_INCEPTION\"",
            "CurrentPortalAccessContextResolver",
            "account.accountEnv()",
            "account.policyNo()",
            "account.certNo()",
            "actor.actorUserId()",
            "actor.actorUserRole()")
        .doesNotContain("Account-Ref", ".block(", ".subscribe(");
  }

  @Test
  void publicFinancialFieldsAreStrings() throws Exception {
    assertThat(
            Files.readString(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/response/accountgainloss/AccountGainLossFundResponse.java")))
        .contains("String gainLoss");
    assertThat(
            Files.readString(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/response/accountgainloss/AccountGainLossOverviewResponse.java")))
        .contains("String totalGainLoss");
  }
}
