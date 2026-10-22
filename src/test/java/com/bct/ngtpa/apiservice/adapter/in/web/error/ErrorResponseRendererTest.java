package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ErrorResponseRendererTest {

  @Test
  void setsStatusContentTypeAndRequestIdHeaderWhenRequestIdPresent() {
    ApiError error = ApiError.form("err.test", "message", List.of(), "SERVER");

    ResponseEntity<MutationResponse<Void>> response =
        ErrorResponseRenderer.render(
            HttpStatus.BAD_REQUEST, ApiStatus.VALIDATION_FAILED, List.of(error), "req-1");

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
    assertEquals("req-1", response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
  }

  @Test
  void omitsRequestIdHeaderWhenRequestIdIsNull() {
    ApiError error = ApiError.system("err.test", "message", "SERVER");

    ResponseEntity<MutationResponse<Void>> response =
        ErrorResponseRenderer.render(
            HttpStatus.INTERNAL_SERVER_ERROR, ApiStatus.SYSTEM_ERROR, List.of(error), null);

    assertNull(response.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER));
  }

  /**
   * SonarQube fix (post-M1 quality gate, java:S1874): {@code HttpStatus.UNPROCESSABLE_ENTITY} is
   * flagged as deprecated in spring-web 7.0.7 in favour of {@code
   * HttpStatus.UNPROCESSABLE_CONTENT}. Production code elsewhere in the codebase (outside this
   * branch's scope) still returns {@code UNPROCESSABLE_ENTITY}, and it is not yet confirmed whether
   * the two constants are reference-equal or distinct enum entries with the same numeric code --
   * swapping this test's literal to {@code UNPROCESSABLE_CONTENT} without that confirmation risks a
   * false failure if production still returns the old constant. Suppressed here rather than
   * guessed; migrating the whole codebase to {@code UNPROCESSABLE_CONTENT} is a separate, broader
   * workstream.
   */
  @SuppressWarnings("deprecation")
  @Test
  void bodyIsAFailureMutationResponseCarryingTheGivenStatusAndErrors() {
    ApiError first = ApiError.business("err.one", "first", List.of(), "SERVER");
    ApiError second = ApiError.business("err.two", "second", List.of(), "SERVER");

    ResponseEntity<MutationResponse<Void>> response =
        ErrorResponseRenderer.render(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiStatus.BUSINESS_REJECTED,
            List.of(first, second),
            "req-2");

    MutationResponse<Void> body = response.getBody();
    assertNotNull(body);
    assertFalse(body.success());
    assertEquals(ApiStatus.BUSINESS_REJECTED, body.status());
    assertNull(body.result());
    assertEquals(2, body.errors().size());
    assertEquals("err.one", body.errors().get(0).code());
    assertEquals("err.two", body.errors().get(1).code());
  }

  @Test
  void statusCanBeADynamicHttpStatusCode() {
    ApiError error = ApiError.downstreamSystem("err.downstream", "message", "SERVER");

    ResponseEntity<MutationResponse<Void>> response =
        ErrorResponseRenderer.render(
            HttpStatus.BAD_GATEWAY, ApiStatus.SYSTEM_ERROR, List.of(error), "req-3");

    assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
  }
}
