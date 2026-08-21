package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryCancelWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryRequestWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.session.cookie.HttpSessionCookieAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartException;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * HTTP-level Start contract integration against the real controller, mapper, policy and cookie
 * writer.
 */
class UsernameRecoveryStartPublicContractIntegrationTest {
  private static final String PATH = "/api/v1/auth/username-recovery/start";
  private static final String COOKIE_PATH = "/api/v1/auth/username-recovery";

  @Test
  void registeredResultPublishesExactPublicEnvelopeAndCookies() {
    AtomicReference<UsernameRecoveryStartCommand> captured = new AtomicReference<>();
    StartUsernameRecoveryUseCase start =
        command -> {
          captured.set(command);
          return Mono.just(
              new UsernameRecoveryStartResult(
                  UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                  new AuthFlowSessionId("afs_contract-1"),
                  "csrf-contract-value",
                  600,
                  List.of(
                      new UsernameRecoverySchemeProjection(
                          new UsernameRecoverySchemeRef("sch_contract-1"), "DB", "DBA", true),
                      new UsernameRecoverySchemeProjection(
                          new UsernameRecoverySchemeRef("sch_contract-2"), "HK", "HKA", false))));
        };

    client(start)
        .post()
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "zh-HK")
        .header("X-Request-Id", "req-contract-1")
        .header("Idempotency-Key", "idem-contract-1")
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Request-Id", "req-contract-1")
        .expectHeader()
        .values(
            HttpHeaders.SET_COOKIE,
            values -> {
              String combined = String.join("\n", values);
              assertThat(values).hasSize(1);
              assertThat(combined)
                  .contains("auth_flow_sid=afs_contract-1")
                  .doesNotContain("XSRF-TOKEN")
                  .contains("Path=" + COOKIE_PATH)
                  .contains("Max-Age=600")
                  .contains("SameSite=Strict")
                  .contains("Secure")
                  .contains("HttpOnly");
            })
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.result.nextStep")
        .isEqualTo("SELECT_ACCOUNT")
        .jsonPath("$.result.data.csrfToken")
        .isEqualTo("csrf-contract-value")
        .jsonPath("$.result.data.expiresInSeconds")
        .isEqualTo(600)
        .jsonPath("$.result.data.schemes.length()")
        .isEqualTo(2)
        .jsonPath("$.result.data.schemes[0].schemeRef")
        .isEqualTo("sch_contract-1")
        .jsonPath("$.result.data.schemes[0].accountEnv")
        .isEqualTo("DB")
        .jsonPath("$.result.data.schemes[0].trustCode")
        .isEqualTo("DBA")
        .jsonPath("$.result.data.schemes[0].isRegistered")
        .isEqualTo(true)
        .jsonPath("$.messages.length()")
        .isEqualTo(0)
        .jsonPath("$.errors.length()")
        .isEqualTo(0)
        .jsonPath("$.result.data.username")
        .doesNotExist()
        .jsonPath("$.result.data.destination")
        .doesNotExist()
        .jsonPath("$.result.data.dateOfBirth")
        .doesNotExist();

    assertThat(captured.get())
        .satisfies(
            command -> {
              assertThat(command.identityType()).isEqualTo(MemberIdentityType.STAFFNO);
              assertThat(command.language()).isEqualTo(Locale.forLanguageTag("zh-HK"));
              assertThat(command.idempotencyKey()).isEqualTo("idem-contract-1");
            });
  }

  @Test
  void memberNotFoundPublishesApproved422WithoutCookiesOrProtectedData() {
    StartUsernameRecoveryUseCase start =
        command ->
            Mono.just(
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null, null, List.of()));

    client(start)
        .post()
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "en")
        .header("Idempotency-Key", "idem-contract-2")
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(422))
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.result.nextStep")
        .isEqualTo("RESTART_FLOW")
        .jsonPath("$.result.data")
        .isEmpty()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.usernamerecovery.verification.failed")
        .jsonPath("$.errors[0].source")
        .isEqualTo("BFF")
        .jsonPath("$.errors[0].message")
        .doesNotExist();
  }

  @Test
  void zeroRegisteredPublishesSafeRegistrationReminderAndNoSessionMaterial() {
    StartUsernameRecoveryUseCase start =
        command ->
            Mono.just(
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
                    null,
                    null,
                    List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false))));

    client(start)
        .post()
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "en")
        .header("Idempotency-Key", "idem-contract-3")
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(422))
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.result.nextStep")
        .isEqualTo("RESTART_FLOW")
        .jsonPath("$.result.data")
        .isEqualTo(null)
        .jsonPath("$.messages[0].code")
        .isEqualTo("label.usernamerecovery.complete.registration")
        .jsonPath("$.errors.length()")
        .isEqualTo(0);
  }

  @Test
  void missingIdempotencyKeyFailsBeforeUseCaseAndKeepsPublicEnvelope() {
    StartUsernameRecoveryUseCase start =
        command ->
            Mono.error(new AssertionError("Start use case must not run without Idempotency-Key"));

    client(start)
        .post()
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(validRequest())
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
        .jsonPath("$.result")
        .doesNotExist()
        .jsonPath("$.errors[0].code")
        .exists();
  }

  @Test
  void typedSystemFailurePublishes503SystemErrorWithoutRawFailureText() {
    StartUsernameRecoveryUseCase start =
        command ->
            Mono.error(
                UsernameRecoveryStartException.systemFailure(
                    new IllegalStateException("redis-secret-failure")));

    client(start)
        .post()
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Idempotency-Key", "idem-contract-4")
        .bodyValue(validRequest())
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
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.workflow.system.unavailable")
        .jsonPath("$.errors[0].message")
        .doesNotExist()
        .consumeWith(
            result ->
                assertThat(new String(result.getResponseBody()))
                    .doesNotContain("redis-secret-failure"));
  }

  private static WebTestClient client(StartUsernameRecoveryUseCase start) {
    RequestUsernameRecoveryUseCase request =
        (command, csrf, origin, referer) ->
            Mono.error(new AssertionError("Request use case must not run"));
    CancelUsernameRecoveryUseCase cancel =
        (command, csrf, origin, referer) ->
            Mono.error(new AssertionError("Cancel use case must not run"));
    SessionCookiePolicy authFlow =
        new SessionCookiePolicy(
            "auth_flow_sid", COOKIE_PATH, 600, true, true, SessionCookieSameSite.STRICT);
    UsernameRecoveryController controller =
        new UsernameRecoveryController(
            start,
            new UsernameRecoveryStartWebMapper(),
            new UsernameRecoveryStartResponsePolicy(),
            request,
            new UsernameRecoveryRequestWebMapper(),
            new UsernameRecoveryRequestResponsePolicy(),
            cancel,
            new UsernameRecoveryCancelWebMapper(),
            new UsernameRecoveryCancelResponsePolicy(),
            new UsernameRecoveryCookieWriter(authFlow),
            new HttpSessionCookieAdapter());
    return WebTestClient.bindToController(controller).build();
  }

  private static String validRequest() {
    return """
                {
                  "identityType": "STAFF_NO",
                  "identityValue": "3030609",
                  "dateOfBirth": "04/07/1978",
                  "email": "member@example.com",
                  "mobile": null,
                  "captchaChallengeId": "cap_contract-1"
                }
                """;
  }
}
