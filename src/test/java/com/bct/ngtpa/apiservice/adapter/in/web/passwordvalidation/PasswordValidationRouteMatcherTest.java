package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;

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
    "GET,/api/v1/password-validation,false",
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
}
