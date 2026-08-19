package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class UsernameRecoveryRequestCancelPublicContractIntegrationTest {
  private static final String BASE = "/api/v1/auth/username-recovery";
  private static final String COOKIE_PATH = BASE;

  @Test
  void requestAcceptedPublishesMaskedPublicEnvelopeAndForwardsSecurityContext() {
    AtomicReference<RequestCall> captured = new AtomicReference<>();
    RequestUsernameRecoveryUseCase request =
        (command, csrf, origin, referer) -> {
          captured.set(new RequestCall(command, csrf, origin, referer));
          return Mono.just(
              new UsernameRecoveryRequestResult(
                  UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
                  UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
                  new UsernameRecoverySchemeRef("sch_contract-1"),
                  "m****r@example.com",
                  UsernameRecoveryDestinationType.EMAIL));
        };

    client(request, neverCancel())
        .post()
        .uri(BASE + "/request")
        .cookie("auth_flow_sid", "afs_contract-2")
        .header("Idempotency-Key", "idem-contract-2-request")
        .header("X-XSRF-TOKEN", "csrf-contract-2")
        .header("Scheme-Ref", "sch_contract-1")
        .header("Origin", "https://portal.example")
        .header("Referer", "https://portal.example/username-recovery")
        .header("Accept-Language", "zh-HK")
        .header("X-Request-Id", "req-contract-2-request")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Request-Id", "req-contract-2-request")
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.result.nextStep")
        .isEqualTo("SELECT_ACCOUNT")
        .jsonPath("$.result.data.status")
        .isEqualTo("PARTIALLY_REQUESTED")
        .jsonPath("$.result.data.schemeRef")
        .isEqualTo("sch_contract-1")
        .jsonPath("$.result.data.maskedDestination")
        .isEqualTo("m****r@example.com")
        .jsonPath("$.result.data.destinationType")
        .isEqualTo("EMAIL")
        .jsonPath("$.result.data.requestAccepted")
        .isEqualTo(true)
        .jsonPath("$.result.data.username")
        .doesNotExist()
        .jsonPath("$.result.data.email")
        .doesNotExist()
        .jsonPath("$.result.data.mobile")
        .doesNotExist()
        .jsonPath("$.errors.length()")
        .isEqualTo(0);

    assertThat(captured.get())
        .satisfies(
            call -> {
              assertThat(call.command().authFlowSessionId().value()).isEqualTo("afs_contract-2");
              assertThat(call.command().schemeRef().value()).isEqualTo("sch_contract-1");
              assertThat(call.command().idempotencyKey()).isEqualTo("idem-contract-2-request");
              assertThat(call.csrf()).isEqualTo("csrf-contract-2");
              assertThat(call.origin()).isEqualTo("https://portal.example");
              assertThat(call.referer()).isEqualTo("https://portal.example/username-recovery");
            });
  }

  @Test
  void requestMissingSchemeRefFailsBeforeUseCaseWithCanonicalHeaderTarget() {
    client(neverRequest(), neverCancel())
        .post()
        .uri(BASE + "/request")
        .cookie("auth_flow_sid", "afs_contract-2")
        .header("Idempotency-Key", "idem-contract-2-request")
        .header("X-XSRF-TOKEN", "csrf-contract-2")
        .header("X-Request-Id", "req-contract-2-missing-scheme")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.request.validation.failed")
        .jsonPath("$.errors[0].targets[0]")
        .isEqualTo("Scheme-Ref");
  }

  @Test
  void requestTypedSecurityFailurePublishes403WithoutProtectedDataOrCookies() {
    RequestUsernameRecoveryUseCase request =
        (command, csrf, origin, referer) ->
            Mono.error(UsernameRecoveryRequestException.securityInvalid());
    client(request, neverCancel())
        .post()
        .uri(BASE + "/request")
        .cookie("auth_flow_sid", "afs_contract-2")
        .header("Idempotency-Key", "idem-contract-2-security")
        .header("X-XSRF-TOKEN", "csrf-secret-value")
        .header("Scheme-Ref", "sch_contract-1")
        .header("Origin", "https://portal.example")
        .header("X-Request-Id", "req-contract-2-security")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.usernamerecovery.csrf.invalid")
        .consumeWith(
            result ->
                assertThat(new String(result.getResponseBody()))
                    .doesNotContain("csrf-secret-value", "afs_contract-2"));
  }

  @Test
  void cancelSuccessClearsBothExactPathCookiesAndPublishesReturnToLogin() {
    AtomicReference<CancelCall> captured = new AtomicReference<>();
    CancelUsernameRecoveryUseCase cancel =
        (command, csrf, origin, referer) -> {
          captured.set(new CancelCall(command, csrf, origin, referer));
          return Mono.just(
              new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS));
        };

    client(neverRequest(), cancel)
        .post()
        .uri(BASE + "/cancel")
        .cookie("auth_flow_sid", "afs_contract-2")
        .header("Idempotency-Key", "idem-contract-2-cancel")
        .header("X-XSRF-TOKEN", "csrf-contract-2")
        .header("Origin", "https://portal.example")
        .header("Referer", "https://portal.example/username-recovery")
        .header("X-Request-Id", "req-contract-2-cancel")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Request-Id", "req-contract-2-cancel")
        .expectHeader()
        .values(
            HttpHeaders.SET_COOKIE,
            values -> {
              assertThat(values).hasSize(1);
              assertThat(values.get(0))
                  .contains("auth_flow_sid=")
                  .contains("Path=" + COOKIE_PATH)
                  .contains("Max-Age=0")
                  .contains("HttpOnly")
                  .contains("Secure")
                  .contains("SameSite=Strict")
                  .doesNotContain("XSRF-TOKEN");
            })
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.result.nextStep")
        .isEqualTo("RETURN_TO_LOGIN")
        .jsonPath("$.result.data")
        .isEmpty()
        .jsonPath("$.errors.length()")
        .isEqualTo(0);

    assertThat(captured.get())
        .satisfies(
            call -> {
              assertThat(call.command().authFlowSessionId().value()).isEqualTo("afs_contract-2");
              assertThat(call.command().idempotencyKey()).isEqualTo("idem-contract-2-cancel");
              assertThat(call.csrf()).isEqualTo("csrf-contract-2");
              assertThat(call.origin()).isEqualTo("https://portal.example");
              assertThat(call.referer()).isEqualTo("https://portal.example/username-recovery");
            });
  }

  @Test
  void cancelSystemFailurePublishes503WithoutClearingCookiesOrRawFailureText() {
    CancelUsernameRecoveryUseCase cancel =
        (command, csrf, origin, referer) ->
            Mono.just(
                new UsernameRecoveryCancelResult(
                    UsernameRecoveryCancelResult.Outcome.SYSTEM_FAILED));
    client(neverRequest(), cancel)
        .post()
        .uri(BASE + "/cancel")
        .cookie("auth_flow_sid", "afs_contract-2")
        .header("Idempotency-Key", "idem-contract-2-cancel-failure")
        .header("X-XSRF-TOKEN", "csrf-contract-2")
        .header("X-Request-Id", "req-contract-2-cancel-failure")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.result.nextStep")
        .isEqualTo("RETURN_TO_LOGIN")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.workflow.system.unavailable");
  }

  private static WebTestClient client(
      RequestUsernameRecoveryUseCase request, CancelUsernameRecoveryUseCase cancel) {
    SessionCookiePolicy authFlow =
        new SessionCookiePolicy(
            "auth_flow_sid", COOKIE_PATH, 600, true, true, SessionCookieSameSite.STRICT);
    UsernameRecoveryController controller =
        new UsernameRecoveryController(
            neverStart(),
            new UsernameRecoveryStartWebMapper(),
            new UsernameRecoveryStartResponsePolicy(),
            request,
            new UsernameRecoveryRequestWebMapper(),
            new UsernameRecoveryRequestResponsePolicy(),
            cancel,
            new UsernameRecoveryCancelWebMapper(),
            new UsernameRecoveryCancelResponsePolicy(),
            new UsernameRecoveryCookieWriter(authFlow));
    return WebTestClient.bindToController(controller).build();
  }

  private static StartUsernameRecoveryUseCase neverStart() {
    return command -> Mono.error(new AssertionError("start use case must not run"));
  }

  private static RequestUsernameRecoveryUseCase neverRequest() {
    return (command, csrf, origin, referer) ->
        Mono.error(new AssertionError("request use case must not run"));
  }

  private static CancelUsernameRecoveryUseCase neverCancel() {
    return (command, csrf, origin, referer) ->
        Mono.error(new AssertionError("cancel use case must not run"));
  }

  private record RequestCall(
      UsernameRecoveryRequestCommand command, String csrf, String origin, String referer) {}

  private record CancelCall(
      UsernameRecoveryCancelCommand command, String csrf, String origin, String referer) {}
}
