package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.HttpSessionCookieAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryCancelException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UsernameRecoveryControllerSecurityTest {

  private static final String COOKIE_PATH = "/api/v1/auth/username-recovery";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void requestMissingCookieReturns401AuthenticationRequiredWithoutSensitiveBody() {
    var controller = controller(missingStart(), missingRequest(), missingCancel());
    StepVerifier.create(
            controller.request(
                null,
                "idem_secret",
                "csrf_secret",
                "sch_secret",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .extracting(error -> error.code())
                  .isEqualTo("err.security.authentication.required");
            })
        .verifyComplete();
  }

  @Test
  void requestMissingCsrfReturns401() {
    var controller = controller(missingStart(), missingRequest(), missingCancel());
    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                null,
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
        .verifyComplete();
  }

  @Test
  void cancelMissingCookieReturns401() {
    var controller = controller(missingStart(), missingRequest(), missingCancel());
    StepVerifier.create(
            controller.cancel(
                null,
                "idem_125",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
        .verifyComplete();
  }

  @Test
  void cancelMissingCsrfReturns401() {
    var controller = controller(missingStart(), missingRequest(), missingCancel());
    StepVerifier.create(
            controller.cancel(
                "afs_opaque",
                "idem_125",
                null,
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED))
        .verifyComplete();
  }

  @Test
  void requestInvalidSecurityTypedExceptionReturns403Forbidden() {
    var controller =
        controller(
            missingStart(),
            (command, csrf, origin, referer) ->
                Mono.error(UsernameRecoveryRequestException.securityInvalid()),
            missingCancel());
    StepVerifier.create(
            controller.request(
                "afs_opaque",
                "idem_124",
                "csrf_abc",
                "sch_1",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_124",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
        .verifyComplete();
  }

  @Test
  void cancelInvalidSecurityTypedExceptionReturns403Forbidden() {
    var controller =
        controller(
            missingStart(),
            missingRequest(),
            (command, csrf, origin, referer) ->
                Mono.error(UsernameRecoveryCancelException.securityInvalid()));
    StepVerifier.create(
            controller.cancel(
                "afs_opaque",
                "idem_125",
                "csrf_abc",
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_125",
                new MockServerHttpResponse()))
        .assertNext(entity -> assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
        .verifyComplete();
  }

  @Test
  void startSuccessBodyLeaksNoSessionCookieOrSecrets() throws Exception {
    var controller =
        controller(
            command ->
                Mono.just(
                    new UsernameRecoveryStartResult(
                        UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                        new AuthFlowSessionId("afs_opaque"),
                        "csrf-value",
                        600,
                        List.of(
                            new UsernameRecoverySchemeProjection(
                                new UsernameRecoverySchemeRef("sch_1"), "DB", "DBA", true)))),
            missingRequest(),
            missingCancel());
    var response = new MockServerHttpResponse();
    var request =
        new UsernameRecoveryStartRequest(
            "STAFF_NO",
            "secretIdentity123",
            "04/07/1978",
            "member@example.com",
            null,
            "cap_secret");

    StepVerifier.create(controller.start(request, "en", "req_999", "idem_secret", response))
        .assertNext(
            entity -> {
              JsonNode body = objectMapper.valueToTree(entity.getBody());
              String json = body.toString();
              assertThat(json)
                  .doesNotContain("afs_opaque", "secretIdentity123", "idem_secret", "cap_secret");
            })
        .verifyComplete();
  }

  private static StartUsernameRecoveryUseCase missingStart() {
    return command -> Mono.error(new AssertionError("start must not be called"));
  }

  private static RequestUsernameRecoveryUseCase missingRequest() {
    return (command, csrf, origin, referer) ->
        Mono.error(new AssertionError("request must not be called"));
  }

  private static CancelUsernameRecoveryUseCase missingCancel() {
    return (command, csrf, origin, referer) ->
        Mono.error(new AssertionError("cancel must not be called"));
  }

  private static UsernameRecoveryController controller(
      StartUsernameRecoveryUseCase start,
      RequestUsernameRecoveryUseCase request,
      CancelUsernameRecoveryUseCase cancel) {
    SessionCookiePolicy authFlowPolicy =
        new SessionCookiePolicy(
            "auth_flow_sid", COOKIE_PATH, 600, true, true, SessionCookieSameSite.STRICT);
    return new UsernameRecoveryController(
        start,
        new UsernameRecoveryStartWebMapper(),
        new UsernameRecoveryStartResponsePolicy(),
        request,
        new UsernameRecoveryRequestWebMapper(),
        new UsernameRecoveryRequestResponsePolicy(),
        cancel,
        new UsernameRecoveryCancelWebMapper(),
        new UsernameRecoveryCancelResponsePolicy(),
        new UsernameRecoveryCookieWriter(authFlowPolicy),
        new HttpSessionCookieAdapter());
  }
}
