package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.*;
import com.bct.ngtpa.apiservice.adapter.in.web.error.*;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.*;
import com.bct.ngtpa.apiservice.adapter.in.web.support.ReactorCurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.*;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationGrantService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.security.*;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.bct.ngtpa.apiservice.shared.masking.*;
import com.bct.ngtpa.apiservice.shared.web.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Real loopback HTTP, production filters/handlers/controller and real session/grant orchestration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordValidationHttpTest {
  private static final String PATH = "/api/v1/password-validation";
  private static final String SID = "sid_abcdefghijklmnopqrstuvwx";
  private static final String REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String PURPOSE = "PERSONAL_INFORMATION_UPDATE";
  private static final String PAYLOAD =
      "{\"formVersion\":\"v1\",\"applyToAllAccounts\":false,\"fields\":{\"mailingAddress\":\"reviewed-marker\"}}";
  private static final ObjectMapper JSON = new ObjectMapper();
  private AnnotationConfigApplicationContext context;
  private DisposableServer server;
  private WebTestClient client;
  private Backend backend;
  private Logger root;
  private ListAppender<ILoggingEvent> logs;

  @BeforeAll
  void start() {
    context = new AnnotationConfigApplicationContext();
    TestPropertyValues.of(
            "api.security.require-authentication=false",
            "password-validation.rate-limit.secret="
                + Base64.getEncoder().encodeToString(new byte[32]))
        .applyTo(context);
    context.register(HttpConfiguration.class);
    context.refresh();
    server =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle(
                new ReactorHttpHandlerAdapter(
                    WebHttpHandlerBuilder.applicationContext(context).build()))
            .bindNow();
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + server.port())
            .responseTimeout(Duration.ofSeconds(15))
            .build();
    backend = context.getBean(Backend.class);
  }

  @AfterAll
  void stop() {
    if (server != null) server.disposeNow();
    if (context != null) context.close();
  }

  @BeforeEach
  void resetBackend() {
    backend.reset();
    context
        .getBean(
            org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall.class)
        .setAllowSemicolon(true);
    root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    logs = new ListAppender<>();
    logs.start();
    root.addAppender(logs);
  }

  @AfterEach
  void detachLogs() {
    root.detachAppender(logs);
    logs.stop();
  }

  @Test
  void correctPasswordIssuesBoundTokenFromServerIdentityWithoutLoggingOrEchoingInput()
      throws Exception {
    var result =
        post(body(" Mixed Case "))
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectHeader()
            .valueEquals(RequestCorrelation.REQUEST_ID_HEADER, "m3-http")
            .expectBody()
            .returnResult();
    var json = JSON.readTree(result.getResponseBody());
    assertThat(json.path("success").asBoolean()).isTrue();
    assertThat(json.path("status").asText()).isEqualTo("SUCCESS");
    String token = json.at("/result/data/token").asText();
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    assertThat(json.at("/result/data/expiresInSeconds").asInt()).isBetween(1, 300);
    assertThat(json.path("errors").isEmpty()).isTrue();
    assertThat(json.path("messages").isEmpty()).isTrue();
    assertThat(backend.passwordCalls).hasValue(1);
    assertThat(backend.lastCheck.get().customerId()).isEqualTo("authenticated-actor-marker");
    assertThat(backend.lastCheck.get().password()).isEqualTo(" Mixed Case ");
    var consumed =
        context
            .getBean(PasswordValidationGrantService.class)
            .consume(
                new AuthenticatedSessionId(SID),
                new AuthenticatedAccountRef(REF),
                PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                token,
                "test")
            .block();
    assertThat(consumed.status()).isEqualTo(PasswordValidationGrantService.Status.CONSUMED);
    assertThat(consumed.binding())
        .isEqualTo(new JsonOperationPayloadCanonicalizer().digest(PAYLOAD));
    String response = new String(result.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8);
    assertThat(response)
        .doesNotContain(
            "Mixed Case",
            "authenticated-actor-marker",
            SID,
            "reviewed-marker",
            "fail-count",
            "requestId",
            "DOWNSTREAM");
    assertSafeLogs(token, consumed.binding().sha256());
  }

  @ParameterizedTest
  @EnumSource(
      value = PasswordCheckResult.class,
      names = {"INCORRECT", "CUSTOMER_NOT_REGISTERED", "UNAVAILABLE"})
  void downstreamOutcomesUseApprovedCodesAndNeverReplaceGrant(PasswordCheckResult outcome) {
    String prior = issueToken();
    String state = backend.session().featureState().get(grantKey());
    backend.check.set(command -> Mono.just(outcome));
    int status = outcome == PasswordCheckResult.INCORRECT ? 401 : 503;
    String code =
        outcome == PasswordCheckResult.INCORRECT
            ? "err.password-validation.password.invalid"
            : "err.password-validation.unavailable";
    failure(
        post(body("x")),
        status,
        outcome == PasswordCheckResult.INCORRECT ? "BUSINESS_REJECTED" : "SYSTEM_ERROR",
        code);
    assertThat(backend.passwordCalls).hasValue(2);
    assertThat(backend.session().featureState()).containsEntry(grantKey(), state);
    assertThat(
            context
                .getBean(PasswordValidationGrantService.class)
                .consume(
                    new AuthenticatedSessionId(SID),
                    new AuthenticatedAccountRef(REF),
                    PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                    prior,
                    "test")
                .block()
                .status())
        .isEqualTo(PasswordValidationGrantService.Status.CONSUMED);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void lockedAccountAlwaysClearsCookieAndRevocationFailureIsUnavailable(boolean failRevocation) {
    backend.check.set(command -> Mono.just(PasswordCheckResult.ACCOUNT_LOCKED));
    backend.failTerminal = failRevocation;
    var result =
        failure(
            post(body("x")),
            failRevocation ? 503 : 423,
            failRevocation ? "SYSTEM_ERROR" : "BUSINESS_REJECTED",
            failRevocation
                ? "err.password-validation.unavailable"
                : "err.password-validation.account.locked");
    var cookie = result.getResponseCookies().getFirst("portal_sid");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge()).isZero();
    assertThat(cookie.getPath()).isEqualTo("/api");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("None");
    assertThat(backend.terminalAttempts).hasValue(1);
    assertThat(backend.passwordCalls).hasValue(1);
    assertThat(backend.session().status())
        .isEqualTo(
            failRevocation
                ? AuthenticatedSessionStatus.ACTIVE
                : AuthenticatedSessionStatus.REVOKED);
    assertThat(backend.session().featureState()).doesNotContainKey(grantKey());
  }

  @Test
  void eleventhStructurallyValidRequestIsThrottledAndMalformedRequestsDoNotCount() {
    backend.check.set(command -> Mono.just(PasswordCheckResult.INCORRECT));
    for (int i = 0; i < 10; i++) {
      failure(post("{}"), 400, "VALIDATION_FAILED", "err.password-validation.request.invalid");
      failure(
          post(body("x")), 401, "BUSINESS_REJECTED", "err.password-validation.password.invalid");
    }
    failure(
        post(body("x")).expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60"),
        429,
        "BUSINESS_REJECTED",
        "err.password-validation.rate-limited");
    assertThat(backend.passwordCalls).hasValue(10);
    assertThat(backend.limiterCalls).hasValue(11);
  }

  @Test
  void limiterFailureFailsClosedBeforeDownstream() {
    backend.failLimiter = true;
    failure(post(body("x")), 503, "SYSTEM_ERROR", "err.password-validation.unavailable");
    assertThat(backend.passwordCalls).hasValue(0);
    assertThat(backend.limiterCalls).hasValue(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "grant-error",
        "grant-conflict",
        "grant-lost-ack",
        "downstream-error",
        "downstream-empty",
        "session-gone"
      })
  void failuresAndAuthenticationLossNeverReturnTokenOrRepeatDownstream(String fault) {
    backend.fault = fault;
    failure(
        post(body("x")),
        fault.equals("session-gone") ? 401 : 503,
        fault.equals("session-gone") ? "BUSINESS_REJECTED" : "SYSTEM_ERROR",
        fault.equals("session-gone")
            ? "err.security.authentication.required"
            : "err.password-validation.unavailable");
    assertThat(backend.passwordCalls).hasValue(1);
    int expectedAttempts =
        switch (fault) {
          case "grant-conflict" -> 3;
          case "grant-error", "grant-lost-ack" -> 1;
          default -> 0;
        };
    assertThat(backend.grantAttempts.get()).isEqualTo(expectedAttempts);
    assertSafeLogs();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "null",
        "[]",
        "{}",
        "{\"password\":false}",
        "{\"password\":\"x\",\"purpose\":3}",
        "{\"password\":\"x\",\"purpose\":\"FUND_SWITCH\",\"operationPayload\":{}}",
        "{\"password\":\"x\",\"purpose\":\"personal_information_update\",\"operationPayload\":{}}",
        "{\"password\":\"x\",\"purpose\":\"PERSONAL_INFORMATION_UPDATE\",\"operationPayload\":null}",
        "{\"password\":\"x\",\"purpose\":\"PERSONAL_INFORMATION_UPDATE\",\"operationPayload\":[]}",
        "{\"password\":\"x\",\"purpose\":\"PERSONAL_INFORMATION_UPDATE\",\"operationPayload\":{\"fields\":{}}}",
        "{\"password\":\"x\",\"purpose\":\"PERSONAL_INFORMATION_UPDATE\",\"operationPayload\":{\"formVersion\":\"v1\",\"applyToAllAccounts\":\"false\",\"fields\":{\"x\":1}}}"
      })
  void invalidEnvelopeOrStructureIsRejectedBeforeThrottle(String raw) {
    failure(post(raw), 400, "VALIDATION_FAILED", "err.password-validation.request.invalid");
    assertThat(backend.limiterCalls).hasValue(0);
    assertThat(backend.passwordCalls).hasValue(0);
    assertSafeLogs();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cust-id", "username", "actorUserId", "accountEnv", "extra"})
  void unknownEnvelopeMemberCannotOverrideIdentity(String name) {
    String raw = body("x");
    raw = raw.substring(0, raw.length() - 1) + ",\"" + name + "\":\"override-marker\"}";
    failure(post(raw), 400, "VALIDATION_FAILED", "err.password-validation.request.invalid");
    assertThat(backend.passwordCalls).hasValue(0);
    assertThat(backend.limiterCalls).hasValue(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"top", "payload", "fields", "array", "trailing", "malformed"})
  void duplicateMembersAnywhereOrMalformedJsonAreRejectedBeforeLossyMapping(String variant) {
    String raw = body("x");
    raw =
        switch (variant) {
          case "top" -> raw.replace("\"password\":\"x\"", "\"password\":\"x\",\"password\":\"y\"");
          case "payload" ->
              raw.replace(
                  "\"formVersion\":\"v1\"", "\"formVersion\":\"v1\",\"formVersion\":\"v2\"");
          case "fields" ->
              raw.replace("\"mailingAddress\":\"reviewed-marker\"", "\"a\":1,\"\\u0061\":2");
          case "array" ->
              raw.replace(
                  "\"mailingAddress\":\"reviewed-marker\"", "\"array\":[{\"x\":1,\"x\":2}]");
          case "trailing" -> raw + "{}";
          default -> raw.substring(0, raw.length() - 3);
        };
    failure(post(raw), 400, "VALIDATION_FAILED", "err.password-validation.request.invalid");
    assertThat(backend.passwordCalls).hasValue(0);
    assertThat(backend.limiterCalls).hasValue(0);
    assertSafeLogs();
  }

  @Test
  void decimalPrecisionAndUnknownNestedValuesSurviveRawBinding() throws Exception {
    String operation =
        PAYLOAD.replace(
            "\"reviewed-marker\"",
            "{\"n\":12345678901234567890.1234567890123456789,\"a\":[null,true,\"x\"]}");
    var response =
        post(body("x").replace(PAYLOAD, operation))
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult();
    String token = JSON.readTree(response.getResponseBody()).at("/result/data/token").asText();
    var result =
        context
            .getBean(PasswordValidationGrantService.class)
            .consume(
                new AuthenticatedSessionId(SID),
                new AuthenticatedAccountRef(REF),
                PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                token,
                "test")
            .block();
    assertThat(result.binding())
        .isEqualTo(new JsonOperationPayloadCanonicalizer().digest(operation));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 128, 129})
  void configuredLoginLengthBoundsAreUsedWithoutComplexityPolicy(int length) {
    String raw = body("a".repeat(length));
    if (length == 1 || length == 128) post(raw).expectStatus().isOk();
    else failure(post(raw), 400, "VALIDATION_FAILED", "err.password-validation.request.invalid");
    assertThat(backend.passwordCalls).hasValue(length == 1 || length == 128 ? 1 : 0);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "session-missing",
        "session-malformed",
        "session-unknown",
        "session-expired",
        "csrf-missing",
        "csrf-invalid",
        "account-missing",
        "account-unknown"
      })
  void transportFailuresKeepExistingMappingsAndNeverCallDownstream(String failure) {
    String sid =
        switch (failure) {
          case "session-malformed" -> "invalid";
          case "session-unknown" -> "sid_012345678901234567890123";
          default -> SID;
        };
    if (failure.equals("session-expired")) backend.current.set(null);
    var request = client.post().uri(PATH).header(RequestCorrelation.REQUEST_ID_HEADER, "m3-http");
    if (!failure.equals("session-missing")) request.cookie("portal_sid", sid);
    if (!failure.equals("account-missing"))
      request.header(
          RequestHeaderContextKeys.ACCOUNT_REF_HEADER,
          failure.equals("account-unknown") ? "acc_012345678901234567890123" : REF);
    if (!failure.equals("csrf-missing"))
      request.header(
          RequestHeaderContextKeys.XSRF_TOKEN_HEADER,
          failure.equals("csrf-invalid") ? "wrong" : "csrf-fixture");
    var response = request.contentType(MediaType.APPLICATION_JSON).bodyValue(body("x")).exchange();
    int status =
        switch (failure) {
          case "account-missing" -> 400;
          case "csrf-invalid" -> 403;
          default -> 401;
        };
    String code =
        switch (failure) {
          case "account-missing" -> "err.member.context.invalid";
          case "csrf-invalid" -> "err.security.access.denied";
          default -> "err.security.authentication.required";
        };
    failure(response, status, "BUSINESS_REJECTED", code);
    assertThat(backend.passwordCalls).hasValue(0);
    assertThat(backend.limiterCalls).hasValue(0);
  }

  @Test
  void boundedDecoderAndJsonDepthFailuresAreSafeValidationErrors() {
    failure(
        post(body("x").replace("reviewed-marker", "a".repeat(300_000))),
        400,
        "VALIDATION_FAILED",
        "err.password-validation.request.invalid");
    failure(
        post(body("x").replace("\"reviewed-marker\"", "[".repeat(1100) + "0" + "]".repeat(1100))),
        400,
        "VALIDATION_FAILED",
        "err.password-validation.request.invalid");
    assertThat(backend.passwordCalls).hasValue(0);
    assertThat(backend.limiterCalls).hasValue(0);
    assertSafeLogs();
  }

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
    "PUT,/api/v1/Personal-information,false"
  })
  void actualHandlersAndConfidentialityAgreeForEveryPathVariant(
      String method, String path, boolean reachesHandler) {
    var response =
        variantRequest(method, path, "csrf-fixture")
            .bodyValue(method.equals("POST") ? body("x") : PAYLOAD)
            .exchange();
    response.expectStatus().isEqualTo(reachesHandler ? 200 : 404);
    assertThat(backend.passwordCalls).hasValue(reachesHandler && method.equals("POST") ? 1 : 0);
    assertThat(backend.personalCalls).hasValue(reachesHandler && method.equals("PUT") ? 1 : 0);
    if (reachesHandler) {
      response.expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
      assertSafeLogs();
    }
    assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage))
        .noneMatch(message -> message.contains("header-marker"));
  }

  @ParameterizedTest
  @CsvSource({
    "POST,/api/v1/password-validation",
    "POST,/api/v1/password-validation;x=1",
    "POST,/api/v1/%70assword-validation",
    "PUT,/api/v1/personal-information",
    "PUT,/api/v1/personal-information;x=1",
    "PUT,/api/v1/%70ersonal-information"
  })
  void actualChainRendersCsrfForAllRoutedVariantsBeforeEitherHandler(String method, String path) {
    failure(
        variantRequest(method, path, "wrong").bodyValue("{}").exchange(),
        403,
        "BUSINESS_REJECTED",
        "err.security.access.denied");
    assertThat(backend.passwordCalls).hasValue(0);
    assertThat(backend.personalCalls).hasValue(0);
    assertSafeLogs();
  }

  @ParameterizedTest
  @CsvSource({
    "POST,/api/v1/password-validation",
    "POST,/api/v1/password-validation;x=1",
    "POST,/api/v1/%70assword-validation",
    "PUT,/api/v1/personal-information",
    "PUT,/api/v1/personal-information;x=1",
    "PUT,/api/v1/%70ersonal-information"
  })
  void realHttpEscapedFailureOmitsTextInRequestLoggerOnRoutedVariants(String method, String path) {
    variantRequest(method, path, "csrf-fixture")
        .header("X-Guard-Failure", "true")
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
    var requestEvents =
        logs.list.stream()
            .filter(event -> event.getLoggerName().equals(RequestLoggingWebFilter.class.getName()))
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    assertThat(requestEvents)
        .anyMatch(message -> message.contains("http.request.error"))
        .noneMatch(
            message ->
                message.contains("guard-error-marker")
                    || message.contains("errorMessage")
                    || message.contains("requestBody")
                    || message.contains("responseBody")
                    || message.contains("header-marker"));
  }

  private WebTestClient.RequestBodySpec variantRequest(String method, String path, String csrf) {
    return client
        .method(HttpMethod.valueOf(method))
        .uri(java.net.URI.create("http://127.0.0.1:" + server.port() + path))
        .contentType(MediaType.APPLICATION_JSON)
        .cookie("portal_sid", SID)
        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, REF)
        .header(RequestHeaderContextKeys.XSRF_TOKEN_HEADER, csrf)
        .header(RequestCorrelation.REQUEST_ID_HEADER, "m3-path-variant")
        .header("x-password-validation-token", "header-marker");
  }

  @ParameterizedTest
  @CsvSource({"POST,/api/v1/password-validation;x=1", "PUT,/api/v1/personal-information;x=1"})
  void defaultFirewallRejectsSemicolonBeforeHandlerWithoutBeingTheConfidentialityBoundary(
      String method, String path) {
    context
        .getBean(
            org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall.class)
        .setAllowSemicolon(false);
    variantRequest(method, path, "csrf-fixture")
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
    assertThat(backend.passwordCalls).hasValue(0);
    assertThat(backend.personalCalls).hasValue(0);
    assertSafeLogs();
  }

  private String issueToken() {
    try {
      var response = post(body("x")).expectStatus().isOk().expectBody().returnResult();
      return JSON.readTree(response.getResponseBody()).at("/result/data/token").asText();
    } catch (java.io.IOException exception) {
      throw new AssertionError("Invalid fixture response");
    }
  }

  private WebTestClient.ResponseSpec post(String raw) {
    return client
        .post()
        .uri(PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .cookie("portal_sid", SID)
        .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, REF)
        .header(RequestHeaderContextKeys.XSRF_TOKEN_HEADER, "csrf-fixture")
        .header(RequestCorrelation.REQUEST_ID_HEADER, "m3-http")
        .header("X-Password-Validation-Token", "header-marker")
        .bodyValue(raw)
        .exchange();
  }

  private org.springframework.test.web.reactive.server.EntityExchangeResult<byte[]> failure(
      WebTestClient.ResponseSpec response, int http, String status, String code) {
    return response
        .expectStatus()
        .isEqualTo(http)
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
        .expectHeader()
        .exists(RequestCorrelation.REQUEST_ID_HEADER)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo(status)
        .jsonPath("$.errors[0].code")
        .isEqualTo(code)
        .jsonPath("$.errors[0].message")
        .doesNotExist()
        .jsonPath("$.errors[0].severity")
        .isEqualTo("ERROR")
        .jsonPath("$.errors[0].source")
        .isEqualTo("SERVER")
        .jsonPath("$.messages")
        .isEmpty()
        .jsonPath("$.requestId")
        .doesNotExist()
        .jsonPath("$.result.data.token")
        .doesNotExist()
        .returnResult();
  }

  private void assertSafeLogs(String... additional) {
    String text =
        logs.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(java.util.stream.Collectors.joining("\n"));
    assertThat(text)
        .doesNotContain(
            "Mixed Case",
            "authenticated-actor-marker",
            SID,
            "reviewed-marker",
            "header-marker",
            "requestBody",
            "responseBody");
    for (String value : additional) assertThat(text).doesNotContain(value);
  }

  private static String body(String password) {
    return "{\"password\":\""
        + password
        + "\",\"purpose\":\""
        + PURPOSE
        + "\",\"operationPayload\":"
        + PAYLOAD
        + "}";
  }

  private static String grantKey() {
    return "password-validation:" + REF + ":" + PURPOSE;
  }

  @Configuration
  @EnableWebFlux
  @ComponentScan(
      basePackages = {
        "com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation",
        "com.bct.ngtpa.apiservice.config.auth.passwordvalidation"
      })
  @Import({
    RequestLoggingWebFilter.class,
    RequiredRequestIdWebFilter.class,
    PortalAccessContextWebFilter.class,
    AuthenticatedPortalContextWebExceptionHandler.class,
    ExceptionMappingRegistryConfig.class,
    ErrorResponseRenderer.class,
    ReactorCurrentPortalAccessContextResolver.class,
    DefaultAuthenticatedSessionCookieReader.class,
    SecurityConfig.class,
    com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger.class
  })
  static class HttpConfiguration {
    @Bean
    org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall firewall() {
      // Permit matrix parameters only in this probe so routing, not firewall rejection, is
      // exercised.
      var firewall =
          new org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall();
      firewall.setAllowSemicolon(true);
      return firewall;
    }

    @Bean
    @org.springframework.core.annotation.Order(0)
    org.springframework.web.server.WebFilter escapedFailure() {
      return (exchange, chain) -> {
        if (exchange.getRequest().getHeaders().containsHeader("X-Guard-Failure")) {
          return Mono.error(new IllegalStateException("guard-error-marker"));
        }
        return chain.filter(exchange);
      };
    }

    @Bean
    com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController
        personalInformation(
            Backend backend,
            CurrentPortalAccessContextResolver resolver,
            AuthenticatedSessionCookieReader cookies) {
      var update =
          mock(com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase.class);
      when(update.execute(any()))
          .thenAnswer(
              invocation -> {
                backend.personalCalls.incrementAndGet();
                return Mono.just(List.of());
              });
      var requestMapper =
          mock(
              com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper
                  .class);
      when(requestMapper.toCommand(any(), any()))
          .thenReturn(
              new UpdatePersonalInformationCommand(false, Map.of("field", "reviewed-marker")));
      var responseMapper =
          mock(
              com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper
                  .class);
      when(responseMapper.toResponse(anyList()))
          .thenReturn(
              new com.bct.ngtpa.apiservice.adapter.in.web.response
                  .UpdatePersonalInformationResponse(
                  true,
                  com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus.UPDATED,
                  null,
                  List.of(),
                  List.of()));
      var validator =
          mock(
              com.bct.ngtpa.apiservice.adapter.in.web.validation
                  .PersonalInformationUpdateYamlValidator.class);
      var access =
          mock(
              com.bct.ngtpa.apiservice.adapter.in.web.validation
                  .PersonalInformationFieldAccessChecker.class);
      when(access.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
      return new com.bct.ngtpa.apiservice.adapter.in.web.personalinformation
          .PersonalInformationController(
          mock(com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase.class),
          mock(com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper.class),
          resolver,
          update,
          requestMapper,
          responseMapper,
          validator,
          cookies,
          access);
    }

    @Bean
    ObjectMapper objectMapper() {
      return JSON;
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    Backend backend() {
      return new Backend();
    }

    @Bean
    CorsProperties corsProperties() {
      return new CorsProperties();
    }

    @Bean
    AuthWorkflowProperties workflowProperties() {
      var properties = new AuthWorkflowProperties();
      var login = new AuthWorkflowProperties.Login();
      login.getCredential().setPasswordMinLength(1);
      login.getCredential().setPasswordMaxLength(128);
      properties.setLogin(login);
      return properties;
    }

    @Bean
    AuthenticatedSessionCookieWriter cookies() {
      return new AuthenticatedSessionCookieWriter(new AuthWorkflowProperties.AuthenticatedCookie());
    }

    @Bean
    RequestLoggingProperties loggingProperties() {
      var properties = new RequestLoggingProperties();
      properties.setLogHeaders(true);
      properties.setHeaderAllowlist(List.of("X-Password-Validation-Token", "Content-Type"));
      properties.getBodyLogging().setEnabled(true);
      for (String method : List.of("POST", "PUT")) {
        var rule = new RequestLoggingProperties.EndpointRule();
        rule.setMethod(method);
        rule.setPathPattern("/**");
        rule.setLogRequestBody(true);
        rule.setLogResponseBody(true);
        properties.getBodyLogging().getEndpoints().add(rule);
      }
      return properties;
    }

    @Bean
    LoggingSanitizer sanitizer() {
      return new LoggingSanitizer(JSON, new MaskingService(new MaskingProperties()));
    }

    @Bean
    com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor executor(
        Backend backend) {
      var executor =
          mock(com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor.class);
      when(executor.postEnvelope(any()))
          .thenAnswer(
              invocation ->
                  Mono.defer(
                      () -> {
                        var exchange =
                            (com.bct.ngtpa.apiservice.adapter.out.apim.execution
                                        .ApimExchangeRequest<
                                    ?, ?>)
                                invocation.getArgument(0);
                        assertThat(exchange.resubmissionPolicy())
                            .isEqualTo(
                                com.bct.ngtpa.apiservice.adapter.out.apim.execution
                                    .ApimResubmissionPolicy.NEVER);
                        assertThat(exchange.databaseScope())
                            .isEqualTo(
                                com.bct.ngtpa.apiservice.adapter.out.apim.credential
                                    .ApimDatabaseScope.NON_ACCOUNT);
                        assertThat(exchange.retryEligibility())
                            .isEqualTo(
                                com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility
                                    .NON_RETRYABLE);
                        assertThat(exchange.credentialContext().accountEnv()).isNull();
                        var request =
                            (com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto
                                    .TrpPwdValidationRequest)
                                exchange.request();
                        var command =
                            new PasswordCheckCommand(
                                request.customerId(),
                                request.password(),
                                exchange.credentialContext().actorUserRole());
                        backend.passwordCalls.incrementAndGet();
                        backend.lastCheck.set(command);
                        if (backend.fault.equals("session-gone")) backend.current.set(null);
                        if (backend.fault.equals("downstream-error"))
                          return Mono.error(new IllegalStateException("synthetic"));
                        if (backend.fault.equals("downstream-empty")) return Mono.empty();
                        return backend
                            .check
                            .get()
                            .apply(command)
                            .map(
                                outcome -> {
                                  String raw =
                                      switch (outcome) {
                                        case CORRECT ->
                                            "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":true}]}}";
                                        case INCORRECT ->
                                            "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":false}]}}";
                                        case ACCOUNT_LOCKED ->
                                            "{\"response\":{\"err-message\":\"err.tpa.account.locked\"}}";
                                        case CUSTOMER_NOT_REGISTERED ->
                                            "{\"response\":{\"err-message\":\"err.tpa.cust.id.not.registered\"}}";
                                        case UNAVAILABLE ->
                                            "{\"response\":{\"err-message\":\"unknown-fixture\"}}";
                                      };
                                  try {
                                    return JSON.readValue(
                                        raw,
                                        JSON.getTypeFactory()
                                            .constructParametricType(
                                                com.bct.ngtpa.apiservice.adapter.out.apim.dto
                                                    .ApimResponseEnvelope.class,
                                                com.bct.ngtpa.apiservice.adapter.out.apim
                                                    .passwordvalidation.dto.TrpPwdValidationDataItem
                                                    .class));
                                  } catch (java.io.IOException exception) {
                                    throw new IllegalStateException("Invalid fixture envelope");
                                  }
                                });
                      }));
      return executor;
    }

    @Bean
    RateLimitPort rateLimit(Backend backend) {
      return command ->
          Mono.defer(
              () -> {
                int count = backend.limiterCalls.incrementAndGet();
                if (backend.failLimiter) return Mono.error(new IllegalStateException("synthetic"));
                return Mono.just(
                    count <= command.maxAttempts()
                        ? RateLimitResult.allowed(
                            command.bucket(),
                            command.maxAttempts(),
                            0,
                            Instant.now().plusSeconds(60))
                        : RateLimitResult.blocked(
                            command.bucket(),
                            command.maxAttempts(),
                            60,
                            Instant.now().plusSeconds(60)));
              });
    }

    @Bean
    AuthenticatedSessionConcurrencyService concurrency(Backend backend, Clock clock) {
      return backend.concurrency(clock);
    }

    @Bean
    AuthenticatedSessionLifecycleService lifecycle(
        Backend backend, AuthenticatedSessionConcurrencyService writer, Clock clock) {
      return new AuthenticatedSessionLifecycleService(
          backend.sessions,
          backend.protector,
          new SecureAuthenticatedSessionIdGenerator(),
          new SecureAuthenticatedSessionRevisionGenerator(),
          writer,
          Duration.ofSeconds(600),
          Duration.ofSeconds(900),
          Duration.ofSeconds(30),
          clock);
    }

    @Bean
    ValidateAuthenticatedSessionUseCase validation(AuthenticatedSessionLifecycleService lifecycle) {
      return new AuthenticatedSessionValidationService(lifecycle, new Sha256CsrfTokenHasher());
    }

    @Bean
    PortalAccessContextPort portal(AuthenticatedSessionLifecycleService lifecycle) {
      return new AuthenticatedAccountContextResolver(lifecycle);
    }
  }

  static class Backend {
    final AuthenticatedSessionProtectorPort protector =
        new AesGcmAuthenticatedSessionProtector(
            Base64.getEncoder().encodeToString(new byte[32]), JSON);
    final AuthenticatedSessionPort sessions = mock(AuthenticatedSessionPort.class);
    final AtomicReference<ProtectedAuthenticatedSession> current = new AtomicReference<>();
    final AtomicReference<PasswordCheckCommand> lastCheck = new AtomicReference<>();
    final AtomicInteger passwordCalls = new AtomicInteger();
    final AtomicInteger personalCalls = new AtomicInteger();
    final AtomicInteger limiterCalls = new AtomicInteger();
    final AtomicInteger grantAttempts = new AtomicInteger();
    final AtomicInteger terminalAttempts = new AtomicInteger();
    final AtomicReference<Function<PasswordCheckCommand, Mono<PasswordCheckResult>>> check =
        new AtomicReference<>();
    volatile boolean failLimiter;
    volatile boolean failTerminal;
    volatile String fault = "";

    Backend() {
      when(sessions.load(any()))
          .thenAnswer(
              invocation ->
                  Mono.fromSupplier(
                      () -> {
                        var command = (LoadAuthenticatedSessionCommand) invocation.getArgument(0);
                        var stored = current.get();
                        return stored != null
                                && stored
                                    .authenticatedSessionId()
                                    .equals(command.authenticatedSessionId().value())
                            ? Optional.of(stored)
                            : Optional.empty();
                      }));
    }

    void reset() {
      var now = Instant.now();
      current.set(
          protector.protect(
              new AuthenticatedSession(
                  new AuthenticatedSessionId(SID),
                  new ActorContext("authenticated-actor-marker", ActorUserRole.ROLE_USER_MBR),
                  List.of(
                      new AuthenticatedSessionAccount(
                          new AuthenticatedAccountRef(REF),
                          "TEST",
                          "fixture-policy",
                          "fixture-cert",
                          "TEST",
                          null,
                          null,
                          null)),
                  AuthenticatedSessionStatus.ACTIVE,
                  new Sha256CsrfTokenHasher().hash("csrf-fixture"),
                  now,
                  now,
                  now.plusSeconds(600),
                  now.plusSeconds(900),
                  new SessionRevision("a".repeat(32)),
                  null,
                  Map.of("unrelated", "keep"))));
      passwordCalls.set(0);
      personalCalls.set(0);
      limiterCalls.set(0);
      grantAttempts.set(0);
      terminalAttempts.set(0);
      failLimiter = false;
      failTerminal = false;
      fault = "";
      lastCheck.set(null);
      check.set(command -> Mono.just(PasswordCheckResult.CORRECT));
    }

    AuthenticatedSession session() {
      return protector.unprotect(current.get());
    }

    AuthenticatedSessionConcurrencyService concurrency(Clock clock) {
      return new AuthenticatedSessionConcurrencyService(
          sessions,
          protector,
          command ->
              Mono.<AuthenticatedSessionCasUpdateOutcome>defer(
                      () -> {
                        if (command.replacementProtectedSession().status()
                            != AuthenticatedSessionStatus.ACTIVE) {
                          terminalAttempts.incrementAndGet();
                          if (failTerminal)
                            return Mono.just(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE);
                        }
                        if (command.usableUntil() != null) {
                          grantAttempts.incrementAndGet();
                          if (fault.equals("grant-error"))
                            return Mono.error(new IllegalStateException("synthetic"));
                          if (fault.equals("grant-conflict"))
                            return Mono.just(
                                AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH);
                        }
                        synchronized (this) {
                          var existing = current.get();
                          if (existing == null)
                            return Mono.just(AuthenticatedSessionCasUpdateOutcome.NOT_FOUND);
                          if (!existing.sessionRevision().equals(command.expectedRevision()))
                            return Mono.just(
                                AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH);
                          current.set(command.replacementProtectedSession());
                        }
                        if (command.usableUntil() != null && fault.equals("grant-lost-ack"))
                          return Mono.error(new IllegalStateException("synthetic"));
                        return Mono.just(AuthenticatedSessionCasUpdateOutcome.APPLIED);
                      })
                  .map(AuthenticatedSessionCasUpdateResult::of),
          new SecureAuthenticatedSessionRevisionGenerator(),
          clock);
    }
  }
}
