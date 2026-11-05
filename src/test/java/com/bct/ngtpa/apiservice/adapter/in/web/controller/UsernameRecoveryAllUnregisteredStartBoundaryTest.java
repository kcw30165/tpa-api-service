package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.COOKIE_PATH;
import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.START_PATH;
import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.unregisteredItem;
import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestException;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryRequestProtectedExecutionResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Boundary-level proof for the approved correction. A TRPSearchMember response whose only records
 * are unregistered travels through the real APIM adapter and mapper, the real Start operation and
 * the real controller to the ordinary successful SELECT_ACCOUNT public response; the session it
 * creates then rejects an unregistered /request without any delivery-side effect.
 */
class UsernameRecoveryAllUnregisteredStartBoundaryTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final UsernameRecoveryBoundaryFixture fixture = new UsernameRecoveryBoundaryFixture();

  @Test
  void anAllUnregisteredDownstreamResultReachesTheSuccessfulSelectAccountResponse()
      throws Exception {
    fixture.givenApimReturns("", List.of(unregisteredItem("OI", "JPM", "JP MORGAN CHASE - JMCI")));

    EntityExchangeResult<byte[]> exchange = start();

    JsonNode body = JSON.readTree(exchange.getResponseBodyContent());
    assertThat(body.at("/success").asBoolean()).isTrue();
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(body.at("/result/nextStep").asText()).isEqualTo("SELECT_ACCOUNT");
    assertThat(body.at("/result/data/csrfToken").asText())
        .isEqualTo(UsernameRecoveryStartOperationHarness.RAW_CSRF);
    assertThat(body.at("/result/data/expiresInSeconds").asInt()).isEqualTo(600);
    assertThat(body.at("/result/data/schemes")).hasSize(1);
    JsonNode scheme = body.at("/result/data/schemes/0");
    assertThat(scheme.at("/accountEnv").asText()).isEqualTo("OI");
    assertThat(scheme.at("/trustCode").asText()).isEqualTo("JPM");
    assertThat(scheme.at("/isRegistered").asBoolean(true)).isFalse();
    assertThat(scheme.at("/schemeRef").asText()).isNotBlank();
    assertThat(body.at("/messages")).isEmpty();
    assertThat(body.at("/errors")).isEmpty();
    assertThat(new String(exchange.getResponseBodyContent(), StandardCharsets.UTF_8))
        .doesNotContain(
            "err.usernamerecovery.registration.required",
            "label.usernamerecovery.complete.registration",
            "RESTART_FLOW");

    assertThat(exchange.getResponseCookies().getFirst("auth_flow_sid"))
        .isNotNull()
        .satisfies(
            cookie -> {
              assertThat(cookie.getValue())
                  .isEqualTo(fixture.harness.storedSession().authFlowSessionId().value());
              assertThat(cookie.getPath()).isEqualTo(COOKIE_PATH);
              assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(600);
              assertThat(cookie.isHttpOnly()).isTrue();
              assertThat(cookie.isSecure()).isTrue();
              assertThat(cookie.getSameSite()).isEqualTo("Strict");
            });

    verify(fixture.apim).postEnvelope(any());
    assertThat(fixture.harness.creates.get()).isEqualTo(1);
    var context = fixture.harness.binding.load(fixture.harness.storedSession());
    assertThat(context.workflowStatus()).isEqualTo(UsernameRecoveryWorkflowStatus.ACTIVE);
    assertThat(context.candidates())
        .singleElement()
        .satisfies(c -> assertThat(c.registered()).isFalse());
  }

  @Test
  void severalUnregisteredDownstreamRecordsAreAllReturnedWithDistinctSchemeRefs() throws Exception {
    fixture.givenApimReturns(
        "",
        List.of(
            unregisteredItem("OI", "JPM", "JP MORGAN CHASE - JMCI"),
            unregisteredItem("DB", "DBA", "DB SCHEME")));

    JsonNode body = JSON.readTree(start().getResponseBodyContent());

    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode schemes = body.at("/result/data/schemes");
    assertThat(schemes).hasSize(2);
    assertThat(schemes.get(0).at("/isRegistered").asBoolean(true)).isFalse();
    assertThat(schemes.get(1).at("/isRegistered").asBoolean(true)).isFalse();
    assertThat(schemes.get(0).at("/schemeRef").asText())
        .isNotEqualTo(schemes.get(1).at("/schemeRef").asText());
  }

  @Test
  void anUnregisteredRequestAgainstTheIssuedSessionIsRejectedWithoutAnyDeliverySideEffect()
      throws Exception {
    fixture.givenApimReturns("", List.of(unregisteredItem("OI", "JPM", "JP MORGAN CHASE - JMCI")));
    EntityExchangeResult<byte[]> exchange = start();
    JsonNode started = JSON.readTree(exchange.getResponseBodyContent());
    String cookie = exchange.getResponseCookies().getFirst("auth_flow_sid").getValue();
    String csrf = started.at("/result/data/csrfToken").asText();
    String schemeRef = started.at("/result/data/schemes/0/schemeRef").asText();
    var before = fixture.harness.stored.get();

    StepVerifier.create(
            fixture.controller.request(
                cookie,
                "idem_boundary_req1",
                csrf,
                schemeRef,
                "https://portal.example",
                "https://portal.example/ur",
                "en",
                "req_boundary_2",
                new MockServerHttpResponse()))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(entity.getBody().success()).isFalse();
              assertThat(entity.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
              assertThat(entity.getBody().result().nextStep())
                  .isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .satisfies(
                      error ->
                          assertThat(error.code())
                              .isEqualTo("err.usernamerecovery.registration.required"));
            })
        .verifyComplete();

    verifyNoInteractions(fixture.delivery, fixture.destinations, fixture.templates);
    assertThat(fixture.harness.updates.get()).isZero();
    assertThat(fixture.harness.stored.get()).isSameAs(before);
  }

  @Test
  void sessionCsrfAndSchemeRefChecksStayEnforcedForAnAllUnregisteredSession() throws Exception {
    fixture.givenApimReturns("", List.of(unregisteredItem("OI", "JPM", "JP MORGAN CHASE - JMCI")));
    EntityExchangeResult<byte[]> exchange = start();
    JsonNode started = JSON.readTree(exchange.getResponseBodyContent());
    String csrf = started.at("/result/data/csrfToken").asText();
    String schemeRef = started.at("/result/data/schemes/0/schemeRef").asText();
    AuthFlowSessionId sessionId = fixture.harness.storedSession().authFlowSessionId();

    // wrong CSRF with a valid session and an unregistered Scheme-Ref
    assertPreDispatchFailure(
        run(sessionId, schemeRef, "wrong-csrf"),
        UsernameRecoveryRequestException.Reason.SECURITY_INVALID);
    // unknown Scheme-Ref with a valid session and CSRF
    StepVerifier.create(run(sessionId, "sch_does_not_exist", csrf))
        .assertNext(
            result -> {
              assertThat(result.completed()).isTrue();
              assertThat(result.result().outcome())
                  .isEqualTo(UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF);
            })
        .verifyComplete();
    // no session
    fixture.harness.stored.set(null);
    assertPreDispatchFailure(
        run(sessionId, schemeRef, csrf), UsernameRecoveryRequestException.Reason.SESSION_ABSENT);

    verifyNoInteractions(fixture.delivery, fixture.destinations, fixture.templates);
    assertThat(fixture.harness.updates.get()).isZero();
  }

  private EntityExchangeResult<byte[]> start() {
    return fixture
        .client
        .post()
        .uri(START_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "en")
        .header("X-Request-Id", "req-boundary-1")
        .header("Idempotency-Key", "idem-boundary-1")
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Request-Id", "req-boundary-1")
        .expectHeader()
        .exists(HttpHeaders.SET_COOKIE)
        .expectBody()
        .returnResult();
  }

  private Mono<UsernameRecoveryRequestProtectedExecutionResult> run(
      AuthFlowSessionId sessionId, String schemeRef, String csrf) {
    return fixture.requestOperation.execute(
        new UsernameRecoveryRequestCommand(
            sessionId, new UsernameRecoverySchemeRef(schemeRef), "idem_key_1234", null),
        csrf,
        null,
        null,
        "corr-boundary",
        UsernameRecoveryStartOperationHarness.NOW);
  }

  private static void assertPreDispatchFailure(
      Mono<UsernameRecoveryRequestProtectedExecutionResult> execution,
      UsernameRecoveryRequestException.Reason reason) {
    StepVerifier.create(execution)
        .assertNext(
            result -> {
              assertThat(result.completed()).isFalse();
              assertThat(result.failure()).isInstanceOf(UsernameRecoveryRequestException.class);
              assertThat(((UsernameRecoveryRequestException) result.failure()).reason())
                  .isEqualTo(reason);
            })
        .verifyComplete();
  }
}
