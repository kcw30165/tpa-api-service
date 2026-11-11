package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class PasswordValidationRouteMatcherTest {
  @ParameterizedTest
  @CsvSource({
    "POST,/api/v1/password-validation,true",
    "POST,/api/v1/password-validation;x=1,true",
    "POST,/api/v1/%70assword-validation,true",
    "POST,/api/v1/password-validation/,false",
    "POST,/api/v1/Password-validation,false",
    "PUT,/api/v1/personal-information,true",
    "PUT,/api/v1/personal-information;x=1,true",
    "PUT,/api/v1/%70ersonal-information,true",
    "PUT,/api/v1/personal-information/,false",
    "PUT,/api/v1/Personal-information,false",
    "POST,/api/v1/investment-choice/instructions,true",
    "POST,/api/v1/investment-choice/instructions;x=1,true",
    "POST,/api/v1/%69nvestment-choice/instructions,true",
    "POST,/api;x=1/v1/investment-choice/instructions,true",
    "POST,/api/v1/investment-choice/instructions/cancel,true",
    "POST,/api/v1/investment-choice/instructions/%63ancel,true",
    "POST,/api/v1/investment-choice/instructions/cancel;x=1,true",
    "POST,/api/v1/investment-choice/instructions/,false",
    "POST,/api/v1/investment-choice/instructions/cancel/,false",
    "POST,/api/v1/Investment-choice/instructions,false",
    "POST,/api/v1/investment-choice/instructions/Cancel,false",
    "GET,/api/v1/password-validation,false",
    "GET,/api/v1/investment-choice,false",
    "GET,/api/v1/investment-choice/instructions,false",
    "PUT,/api/v1/investment-choice/instructions,false",
    "DELETE,/api/v1/investment-choice/instructions,false",
    "GET,/api/v1/investment-choice/instructions/cancel,false",
    "PUT,/api/v1/investment-choice/instructions/cancel,false",
    "POST,/api/v1/personal-information,false",
    "PUT,/api/v1/password-validation,false",
    "POST,/api/v1/other,false"
  })
  void matchesRoutingSegmentsWithoutBroadeningMethodsCaseOrTrailingSlash(
      String method, String path, boolean expected) {
    var request =
        MockServerHttpRequest.method(HttpMethod.valueOf(method), URI.create(path)).build();
    assertThat(PasswordValidationRouteMatcher.matches(request)).isEqualTo(expected);
  }

  @Test
  void contextPathIsExcludedExactlyAsForRouting() {
    var request =
        MockServerHttpRequest.method(
                HttpMethod.POST, URI.create("/bff/api/v1/%70assword-validation;x=1"))
            .contextPath("/bff")
            .build();
    assertThat(PasswordValidationRouteMatcher.matches(request)).isTrue();
  }

  @Test
  void tokenGatePurposeMappingIsClosedAndUsesTheNormalizedPath() {
    var personalInformation =
        MockServerHttpRequest.method(HttpMethod.PUT, URI.create("/api/v1/%70ersonal-information"))
            .build();
    var investmentChoice =
        MockServerHttpRequest.method(
                HttpMethod.POST, URI.create("/api/v1/%69nvestment-choice/instructions;x=1"))
            .build();
    var cancelInvestmentChoice =
        MockServerHttpRequest.method(
                HttpMethod.POST, URI.create("/api/v1/investment-choice/instructions/%63ancel;x=1"))
            .build();
    var issuance = MockServerHttpRequest.post("/api/v1/password-validation").build();
    var unlistedMethod =
        MockServerHttpRequest.put("/api/v1/investment-choice/instructions").build();

    assertThat(PasswordValidationRouteMatcher.purposeForTokenGate(personalInformation))
        .contains(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE);
    assertThat(PasswordValidationRouteMatcher.purposeForTokenGate(investmentChoice))
        .contains(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE);
    assertThat(PasswordValidationRouteMatcher.purposeForTokenGate(cancelInvestmentChoice))
        .contains(PasswordValidationPurpose.parse("CANCEL_INVESTMENT_CHOICE"));
    assertThat(PasswordValidationRouteMatcher.purposeForTokenGate(issuance)).isEmpty();
    assertThat(PasswordValidationRouteMatcher.purposeForTokenGate(unlistedMethod)).isEmpty();
  }
}
