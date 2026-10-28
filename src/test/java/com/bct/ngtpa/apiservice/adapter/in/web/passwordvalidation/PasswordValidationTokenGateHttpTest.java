package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.*;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.*;
import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.bct.ngtpa.apiservice.adapter.out.redis.*;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.*;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationGrantService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.bct.ngtpa.apiservice.shared.web.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.*;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.util.retry.Retry;

/** Real HTTP/security/account filters and protected Redis/CAS; no application runtime Redis. */
@EnabledIfSystemProperty(named = "m15.redis.server", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordValidationTokenGateHttpTest {
  private static final String PATH = PasswordValidationRouteMatcher.PERSONAL_INFORMATION_PATH;
  private static final String TOKEN = "X-Password-Validation-Token";
  private static final String SID = "sid_abcdefghijklmnopqrstuvwx";
  private static final String REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String OTHER_REF = "acc_012345678901234567890123";
  private static final String OTHER_SID = "sid_012345678901234567890123";
  private static final String PAYLOAD =
      "{\"formVersion\":\"v1\",\"applyToAllAccounts\":false,\"fields\":{\"mailingAddress\":\"reviewed-marker\"}}";
  private static final String GRANT_KEY =
      "password-validation:" + REF + ":PERSONAL_INFORMATION_UPDATE";
  private static final ObjectMapper JSON = new ObjectMapper();
  private final JsonOperationPayloadCanonicalizer bindings =
      new JsonOperationPayloadCanonicalizer();
  private final AtomicInteger dispatches = new AtomicInteger();
  private final AtomicInteger downstream = new AtomicInteger();
  private AnnotationConfigApplicationContext context;
  private DisposableServer http;
  private DisposableServer baselineHttp;
  private WebTestClient client;
  private WebTestClient baselineClient;
  private RedisFixture fixture;
  private Logger root;
  private ListAppender<ILoggingEvent> logs;
  private boolean confidentialRoute;

  @BeforeAll
  void start(@TempDir Path directory) throws Exception {
    fixture = new RedisFixture(directory);
    context = new AnnotationConfigApplicationContext();
    context.getEnvironment().setActiveProfiles("m4-token-gate-test");
    TestPropertyValues.of(
            "api.security.require-authentication=false",
            "password-validation.rate-limit.secret="
                + Base64.getEncoder().encodeToString(new byte[32]))
        .applyTo(context);
    context.registerBean(RedisFixture.class, () -> fixture);
    context.register(HttpConfiguration.class);
    context.refresh();
    var controller = context.getBean(PersonalInformationController.class);
    var update = context.getBean(UpdatePersonalInformationUseCase.class);
    when(update.execute(any()))
        .thenAnswer(
            invocation -> {
              dispatches.incrementAndGet();
              return Mono.fromSupplier(
                  () -> {
                    downstream.incrementAndGet();
                    return List.of();
                  });
            });
    http =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle(
                new ReactorHttpHandlerAdapter(
                    WebHttpHandlerBuilder.applicationContext(context).build()))
            .bindNow();
    baselineHttp =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle(
                new ReactorHttpHandlerAdapter(
                    WebHttpHandlerBuilder.applicationContext(context)
                        .filters(
                            filters ->
                                filters.removeIf(
                                    PasswordValidationTokenGateWebFilter.class::isInstance))
                        .build()))
            .bindNow();
    assertThat(controller).isNotNull();
    client =
        WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + http.port())
            .responseTimeout(Duration.ofSeconds(15))
            .build();
    baselineClient =
        WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + baselineHttp.port())
            .responseTimeout(Duration.ofSeconds(15))
            .build();
  }

  @AfterAll
  void stop() throws Exception {
    if (http != null) http.disposeNow();
    if (baselineHttp != null) baselineHttp.disposeNow();
    if (context != null) context.close();
    if (fixture != null) fixture.close();
  }

  @BeforeEach
  void resetFixture() {
    fixture.reset();
    confidentialRoute = true;
    dispatches.set(0);
    downstream.set(0);
    context.getBean(PasswordValidationHttpTest.Backend.class).reset();
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
  void safeLogs() {
    root.detachAppender(logs);
    logs.stop();
    String text =
        logs.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(java.util.stream.Collectors.joining("\n"));
    assertThat(text)
        .doesNotContain(
            "reviewed-marker",
            "decoder-secret-marker",
            "consume-secret-marker",
            SID,
            "authenticated-actor-marker",
            bindings.digest(PAYLOAD).sha256());
    if (confidentialRoute) assertThat(text).doesNotContain("requestBody", "responseBody");
    logs.list.stream()
        .filter(event -> event.getThrowableProxy() != null)
        .forEach(event -> assertSafeThrowable(event.getThrowableProxy()));
  }

  private void assertSafeThrowable(ch.qos.logback.classic.spi.IThrowableProxy throwable) {
    assertThat(throwable.getMessage())
        .doesNotContain("decoder-secret-marker", "consume-secret-marker", "reviewed-marker");
    if (throwable.getCause() != null) assertSafeThrowable(throwable.getCause());
  }

  @Test
  void validTokenPreservesExistingSuccessBytesAndDispatchesOnce() {
    var before =
        baselineClient
            .put()
            .uri(PATH)
            .cookie("portal_sid", SID)
            .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, REF)
            .header(RequestHeaderContextKeys.XSRF_TOKEN_HEADER, "csrf-fixture")
            .header(RequestCorrelation.REQUEST_ID_HEADER, "m4-http")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(PAYLOAD)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult();
    dispatches.set(0);
    downstream.set(0);
    fixture.bodySubscriptions.set(0);
    fixture.consumedBeforeBody.set(true);
    String token = issue(PAYLOAD);
    var response = put(PATH, PAYLOAD, token).expectStatus().isOk().expectBody().returnResult();
    assertThat(response.getResponseBody()).isEqualTo(before.getResponseBody());
    assertThat(response.getResponseBody())
        .isEqualTo(
            "{\"success\":true,\"status\":\"UPDATED\",\"result\":null,\"messages\":[],\"errors\":[]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(dispatches).hasValue(1);
    assertThat(downstream).hasValue(1);
    assertConsumed();
    reject(put(PATH, PAYLOAD, token), 403, "BUSINESS_REJECTED", authorizationCode());
    assertThat(dispatches).hasValue(1);
    assertThat(downstream).hasValue(1);
    assertThat(logText()).doesNotContain(token);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing",
        "blank",
        "duplicate",
        "comma",
        "malformed",
        "wrong",
        "wrong-account",
        "wrong-session",
        "expired",
        "consumed",
        "missing-grant"
      })
  void everyTokenRejectionDoesNotReadBodyOrDispatchAndWrongTokensPreserveGrant(String row) {
    String token = issue(PAYLOAD);
    String original = fixture.load(SID).featureState().get(GRANT_KEY);
    var request = request(PATH, SID, REF, "csrf-fixture");
    switch (row) {
      case "missing" -> request.headers(headers -> headers.remove(TOKEN));
      case "blank" -> request.header(TOKEN, "");
      case "duplicate" -> request.header(TOKEN, token, token);
      case "comma" -> request.header(TOKEN, token + "," + token);
      case "malformed" -> request.header(TOKEN, "decoder-secret-marker");
      case "wrong" -> request.header(TOKEN, "A".repeat(43));
      case "wrong-account" ->
          request = request(PATH, SID, OTHER_REF, "csrf-fixture").header(TOKEN, token);
      case "wrong-session" ->
          request = request(PATH, OTHER_SID, REF, "csrf-fixture").header(TOKEN, token);
      case "expired" -> {
        fixture.clock.offset = Duration.ofSeconds(300);
        request.header(TOKEN, token);
      }
      case "consumed" -> {
        consume(token);
        request.header(TOKEN, token);
      }
      case "missing-grant" -> {
        fixture.reset();
        request.header(TOKEN, token);
      }
      default -> throw new AssertionError(row);
    }
    reject(request.bodyValue(PAYLOAD).exchange(), 403, "BUSINESS_REJECTED", authorizationCode());
    assertNoDispatch();
    assertThat(fixture.bodySubscriptions).hasValue(0);
    if (!Set.of("consumed", "missing-grant").contains(row)) {
      assertThat(fixture.load(SID).featureState()).containsEntry(GRANT_KEY, original);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"error", "empty", "system", "conflict", "lost-ack", "corrupt"})
  void technicalConsumeFailuresNeverReadOrDispatch(String fault) {
    String token = issue(PAYLOAD);
    if (fault.equals("corrupt")) {
      fixture.write(fixture.load(SID).withFeatureState(GRANT_KEY, "corrupt"));
    } else {
      fixture.fault = fault;
    }
    reject(
        put(PATH, "decoder-secret-marker", token),
        503,
        "SYSTEM_ERROR",
        "err.password-validation.unavailable");
    assertNoDispatch();
    assertThat(fixture.bodySubscriptions).hasValue(0);
    assertThat(fixture.consumeWrites.get())
        .isEqualTo(fault.equals("conflict") ? 3 : fault.equals("corrupt") ? 0 : 1);
    if (fault.equals("lost-ack")) assertConsumed();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "empty",
        "malformed",
        "root-duplicate",
        "nested-duplicate",
        "array-duplicate",
        "trailing",
        "null",
        "array",
        "deep",
        "oversize"
      })
  void invalidRawJsonConsumesBeforeValidationAndNeverDispatches(String row) {
    String token = issue(PAYLOAD);
    String raw =
        switch (row) {
          case "empty" -> "";
          case "malformed" -> PAYLOAD.substring(0, PAYLOAD.length() - 3);
          case "root-duplicate" ->
              PAYLOAD.replace(
                  "\"formVersion\":\"v1\"", "\"formVersion\":\"v1\",\"formVersion\":\"v2\"");
          case "nested-duplicate" ->
              PAYLOAD.replace("\"mailingAddress\":\"reviewed-marker\"", "\"a\":1,\"\\u0061\":2");
          case "array-duplicate" -> PAYLOAD.replace("\"reviewed-marker\"", "[{\"x\":1,\"x\":2}]");
          case "trailing" -> PAYLOAD + "{}";
          case "null" -> "null";
          case "array" -> "[]";
          case "deep" ->
              PAYLOAD.replace("\"reviewed-marker\"", "[".repeat(1100) + "0" + "]".repeat(1100));
          default -> PAYLOAD.replace("reviewed-marker", "a".repeat(300_000));
        };
    reject(put(PATH, raw, token), 400, "VALIDATION_FAILED", "err.request.invalid");
    assertConsumed();
    assertNoDispatch();
    reject(put(PATH, PAYLOAD, token), 403, "BUSINESS_REJECTED", authorizationCode());
  }

  @Test
  void declaredNonUtf8CharsetWithDifferentDecodedTextConsumesAndRejectsWithoutDispatch() {
    String reviewed = PAYLOAD.replace("reviewed-marker", "café");
    byte[] utf8Body = reviewed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(new String(utf8Body, java.nio.charset.StandardCharsets.ISO_8859_1))
        .isNotEqualTo(reviewed);

    String token = issue(reviewed);
    var response =
        request(PATH, SID, REF, "csrf-fixture")
            .contentType(MediaType.parseMediaType("application/json;charset=ISO-8859-1"))
            .header(TOKEN, token)
            .bodyValue(utf8Body)
            .exchange();

    reject(response, 403, "BUSINESS_REJECTED", authorizationCode());
    assertConsumed();
    assertNoDispatch();
    reject(put(PATH, reviewed, token), 403, "BUSINESS_REJECTED", authorizationCode());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "value",
        "type",
        "array",
        "null",
        "apply-all",
        "form-version",
        "unknown",
        "precision"
      })
  void changedRawBindingConsumesAndRejectsWithoutDispatch(String row) {
    String reviewed = PAYLOAD;
    String submitted =
        switch (row) {
          case "value" -> PAYLOAD.replace("reviewed-marker", "changed");
          case "type" -> PAYLOAD.replace("\"reviewed-marker\"", "1");
          case "apply-all" -> PAYLOAD.replace("false", "true");
          case "form-version" -> PAYLOAD.replace("v1", "v2");
          case "unknown" -> PAYLOAD.replace("\"fields\":", "\"extra\":true,\"fields\":");
          case "null" -> PAYLOAD.replace("\"fields\":{", "\"fields\":{\"extra\":null,");
          case "array" -> {
            reviewed = PAYLOAD.replace("\"fields\":{", "\"fields\":{\"extra\":[1,2],");
            yield reviewed.replace("[1,2]", "[2,1]");
          }
          default -> {
            reviewed =
                PAYLOAD.replace(
                    "\"fields\":{", "\"fields\":{\"n\":12345678901234567890.1234567890123456789,");
            yield reviewed.replace(
                "12345678901234567890.1234567890123456789",
                "12345678901234567890.1234567890123456788");
          }
        };
    String token = issue(reviewed);
    reject(put(PATH, submitted, token), 403, "BUSINESS_REJECTED", authorizationCode());
    assertConsumed();
    assertNoDispatch();
  }

  @ParameterizedTest
  @ValueSource(strings = {"order", "number"})
  void canonicalEquivalentBodiesMatchBeforeExistingMapping(String row) {
    String reviewed = PAYLOAD;
    String submitted;
    if (row.equals("order")) {
      submitted =
          "{\"fields\":{\"mailingAddress\":\"reviewed-marker\"},\"applyToAllAccounts\":false,\"formVersion\":\"v1\"}";
    } else {
      reviewed = PAYLOAD.replace("\"fields\":{", "\"fields\":{\"n\":1,");
      submitted = reviewed.replace("\"n\":1", "\"n\":1.0");
    }
    put(PATH, submitted, issue(reviewed)).expectStatus().isOk();
    assertConsumed();
    assertThat(dispatches).hasValue(1);
    assertThat(downstream).hasValue(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"formVersion", "applyToAllAccounts", "fields"})
  void jacksonDtoDecodingDiagnosticsNeverReachLogsOrResponse(String member) {
    String reviewed =
        switch (member) {
          case "formVersion" -> PAYLOAD.replace("\"v1\"", "{\"decoder-secret-marker\":1}");
          case "applyToAllAccounts" -> PAYLOAD.replace("false", "\"decoder-secret-marker\"");
          default ->
              PAYLOAD.replace(
                  "{\"mailingAddress\":\"reviewed-marker\"}", "\"decoder-secret-marker\"");
        };
    var jackson = (Logger) LoggerFactory.getLogger("org.springframework.http.codec.json");
    var previousLevel = jackson.getLevel();
    jackson.setLevel(ch.qos.logback.classic.Level.DEBUG);
    try {
      String token = issue(reviewed);
      var response =
          put(PATH, reviewed, token).expectStatus().isBadRequest().expectBody().returnResult();
      assertThat(new String(response.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8))
          .doesNotContain(
              "decoder-secret-marker",
              "JsonMappingException",
              "Cannot deserialize",
              "through reference chain");
      assertConsumed();
      assertNoDispatch();
    } finally {
      jackson.setLevel(previousLevel);
    }
  }

  @Test
  void parallelPutsWithOneTokenDispatchExactlyOnce() {
    String token = issue(PAYLOAD);
    var statuses =
        Flux.range(0, 12)
            .flatMap(
                ignored ->
                    Mono.fromCallable(
                            () ->
                                put(PATH, PAYLOAD, token)
                                    .expectBody()
                                    .returnResult()
                                    .getStatus()
                                    .value())
                        .subscribeOn(Schedulers.boundedElastic()),
                12)
            .collectList()
            .block(Duration.ofSeconds(20));
    assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
    assertThat(statuses).allMatch(status -> status == 200 || status == 403 || status == 503);
    assertThat(dispatches).hasValue(1);
    assertThat(downstream).hasValue(1);
    assertConsumed();
  }

  @ParameterizedTest
  @ValueSource(strings = {"structure", "yaml", "access", "update"})
  void existingPostBindingValidationAndUpdateFailuresNeverRestoreGrant(String stage) {
    var controller = context.getBean(PersonalInformationController.class);
    var requests =
        (PersonalInformationUpdateWebMapper)
            org.springframework.test.util.ReflectionTestUtils.getField(controller, "requestMapper");
    var yaml =
        (PersonalInformationUpdateYamlValidator)
            org.springframework.test.util.ReflectionTestUtils.getField(controller, "validator");
    var access =
        (PersonalInformationFieldAccessChecker)
            org.springframework.test.util.ReflectionTestUtils.getField(controller, "accessChecker");
    var update = context.getBean(UpdatePersonalInformationUseCase.class);
    var error =
        new ApiError(
            "FIELD", "err.request.invalid", null, List.of("mailingAddress"), "ERROR", "SERVER");
    String token = issue(PAYLOAD);
    try {
      switch (stage) {
        case "structure" ->
            doThrow(
                    new com.bct.ngtpa.apiservice.application.exception
                        .InvalidPersonalInformationUpdateException("Invalid structure"))
                .when(requests)
                .validateStructure(any());
        case "yaml" ->
            when(yaml.validate(any(), any(), any(), any(), any())).thenReturn(List.of(error));
        case "access" ->
            when(access.check(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(List.of(error)));
        case "update" ->
            doAnswer(
                    invocation -> {
                      dispatches.incrementAndGet();
                      downstream.incrementAndGet();
                      return Mono.error(new IllegalStateException("synthetic business failure"));
                    })
                .when(update)
                .execute(any());
        default -> throw new AssertionError(stage);
      }
      var response = put(PATH, PAYLOAD, token).expectBody().returnResult();
      assertThat(response.getStatus().value()).isGreaterThanOrEqualTo(400);
      assertConsumed();
      if (!stage.equals("update")) assertNoDispatch();
      else {
        assertThat(dispatches).hasValue(1);
        assertThat(downstream).hasValue(1);
        assertThat(response.getStatus().value()).isNotEqualTo(503);
      }
      reject(put(PATH, PAYLOAD, token), 403, "BUSINESS_REJECTED", authorizationCode());
    } finally {
      doNothing().when(requests).validateStructure(any());
      when(yaml.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
      when(access.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
      doAnswer(
              invocation -> {
                dispatches.incrementAndGet();
                return Mono.fromSupplier(
                    () -> {
                      downstream.incrementAndGet();
                      return List.of();
                    });
              })
          .when(update)
          .execute(any());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "session-missing,401,err.security.authentication.required",
    "session-invalid,401,err.security.authentication.required",
    "session-unknown,401,err.security.authentication.required",
    "csrf-missing,401,err.security.authentication.required",
    "csrf-invalid,403,err.security.access.denied",
    "account-missing,400,err.member.context.invalid",
    "account-unknown,401,err.security.authentication.required"
  })
  void transportFailuresTakePrecedenceAndDoNotConsume(String row, int httpStatus, String code) {
    String token = issue(PAYLOAD);
    String state = fixture.load(SID).featureState().get(GRANT_KEY);
    var request =
        request(
            PATH,
            switch (row) {
              case "session-missing" -> null;
              case "session-invalid" -> "invalid";
              case "session-unknown" -> "sid_999999999999999999999999";
              default -> SID;
            },
            switch (row) {
              case "account-missing" -> null;
              case "account-unknown" -> "acc_999999999999999999999999";
              default -> REF;
            },
            switch (row) {
              case "csrf-missing" -> null;
              case "csrf-invalid" -> "wrong";
              default -> "csrf-fixture";
            });
    reject(
        request.header(TOKEN, token).bodyValue(PAYLOAD).exchange(),
        httpStatus,
        "BUSINESS_REJECTED",
        code);
    assertThat(fixture.load(SID).featureState()).containsEntry(GRANT_KEY, state);
    assertThat(fixture.consumeWrites).hasValue(0);
    assertThat(fixture.bodySubscriptions).hasValue(0);
    assertNoDispatch();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/personal-information",
        "/api/v1/%70ersonal-information",
        "/api/v1/personal-information;x=1",
        "/api;x=1/v1/personal-information"
      })
  void everyRoutedVariantRequiresTokenAndMatchingBody(String path) {
    String token = issue(PAYLOAD);
    reject(put(path, PAYLOAD), 403, "BUSINESS_REJECTED", authorizationCode());
    assertNoDispatch();
    assertThat(fixture.bodySubscriptions).hasValue(0);
    put(path, PAYLOAD, token).expectStatus().isOk();
    assertThat(dispatches).hasValue(1);
    assertConsumed();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/personal-information/", "/api/v1/Personal-information"})
  void nonRoutedVariantsKeepExistingHandlerMappingAndDoNotConsume(String path) {
    confidentialRoute = false;
    String token = issue(PAYLOAD);
    String original = fixture.load(SID).featureState().get(GRANT_KEY);
    var before =
        baselineClient
            .put()
            .uri(path)
            .cookie("portal_sid", SID)
            .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, REF)
            .header(RequestHeaderContextKeys.XSRF_TOKEN_HEADER, "csrf-fixture")
            .header(RequestCorrelation.REQUEST_ID_HEADER, "m4-http")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectBody()
            .returnResult();
    var after = put(path, "{}", token).expectBody().returnResult();
    assertThat(after.getStatus()).isEqualTo(before.getStatus());
    assertThat(after.getResponseBody()).isEqualTo(before.getResponseBody());
    assertThat(fixture.load(SID).featureState()).containsEntry(GRANT_KEY, original);
    assertNoDispatch();
  }

  private String issue(String raw) {
    var result =
        context
            .getBean(PasswordValidationGrantService.class)
            .issue(
                new AuthenticatedSessionId(SID),
                new AuthenticatedAccountRef(REF),
                PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                bindings.digest(raw),
                PasswordCheckResult.CORRECT,
                "m4-http")
            .block();
    assertThat(result.status()).isEqualTo(PasswordValidationGrantService.Status.ISSUED);
    fixture.consumeWrites.set(0);
    return result.token();
  }

  private void consume(String token) {
    context
        .getBean(PasswordValidationGrantService.class)
        .consume(
            new AuthenticatedSessionId(SID),
            new AuthenticatedAccountRef(REF),
            PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
            token,
            "m4-http")
        .block();
  }

  private WebTestClient.RequestBodySpec request(String path, String sid, String ref, String csrf) {
    var request =
        client
            .put()
            .uri(URI.create("http://127.0.0.1:" + http.port() + path))
            .contentType(MediaType.APPLICATION_JSON)
            .header(RequestCorrelation.REQUEST_ID_HEADER, "m4-http");
    if (sid != null) request.cookie("portal_sid", sid);
    if (ref != null) request.header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, ref);
    if (csrf != null) request.header(RequestHeaderContextKeys.XSRF_TOKEN_HEADER, csrf);
    return request;
  }

  private WebTestClient.ResponseSpec put(String path, String raw, String... tokens) {
    var request = request(path, SID, REF, "csrf-fixture");
    if (tokens.length > 0) request.header(TOKEN, tokens);
    return request.bodyValue(raw).exchange();
  }

  private void reject(
      WebTestClient.ResponseSpec response, int httpStatus, String status, String code) {
    response
        .expectStatus()
        .isEqualTo(httpStatus)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
        .expectHeader()
        .valueEquals(RequestCorrelation.REQUEST_ID_HEADER, "m4-http")
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo(status)
        .jsonPath("$.errors[0].code")
        .isEqualTo(code)
        .jsonPath("$.errors[0].message")
        .doesNotExist()
        .jsonPath("$.messages")
        .isEmpty()
        .jsonPath("$.requestId")
        .doesNotExist();
  }

  private String authorizationCode() {
    return "err.password-validation.authorization.invalid";
  }

  private void assertConsumed() {
    assertThat(fixture.load(SID).featureState()).containsEntry(GRANT_KEY, "consumed");
    assertThat(fixture.consumedBeforeBody).isTrue();
  }

  private void assertNoDispatch() {
    assertThat(dispatches).hasValue(0);
    assertThat(downstream).hasValue(0);
  }

  private String logText() {
    return logs.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  @Configuration
  @Profile("m4-token-gate-test")
  @Import({
    PasswordValidationHttpTest.HttpConfiguration.class,
    com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler.class,
    com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver.class,
    com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger.class
  })
  static class HttpConfiguration {
    @Bean
    com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver messages() {
      return mock(com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver.class);
    }

    @Bean
    @Primary
    AuthenticatedSessionConcurrencyService redisConcurrency(RedisFixture fixture) {
      return fixture.concurrency;
    }

    @Bean
    @Primary
    AuthenticatedSessionLifecycleService redisLifecycle(RedisFixture fixture) {
      return new AuthenticatedSessionLifecycleService(
          fixture.sessions,
          fixture.protector,
          new SecureAuthenticatedSessionIdGenerator(),
          new SecureAuthenticatedSessionRevisionGenerator(),
          fixture.concurrency,
          Duration.ofSeconds(600),
          Duration.ofSeconds(900),
          Duration.ofSeconds(30),
          fixture.clock);
    }

    @Bean
    UpdatePersonalInformationUseCase update() {
      return mock(UpdatePersonalInformationUseCase.class);
    }

    @Bean
    PersonalInformationController personalInformation(
        UpdatePersonalInformationUseCase update,
        CurrentPortalAccessContextResolver contexts,
        AuthenticatedSessionCookieReader cookies) {
      var requests = mock(PersonalInformationUpdateWebMapper.class);
      when(requests.toCommand(any(), any()))
          .thenReturn(
              new UpdatePersonalInformationCommand(false, Map.of("field", "reviewed-marker")));
      var responses = mock(PersonalInformationUpdateResponseMapper.class);
      when(responses.toResponse(anyList()))
          .thenReturn(
              new UpdatePersonalInformationResponse(
                  true, ApiStatus.UPDATED, null, List.of(), List.of()));
      var access = mock(PersonalInformationFieldAccessChecker.class);
      when(access.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
      return new PersonalInformationController(
          mock(GetPersonalInformationUseCase.class),
          mock(PersonalInformationWebMapper.class),
          contexts,
          update,
          requests,
          responses,
          mock(PersonalInformationUpdateYamlValidator.class),
          cookies,
          access);
    }

    @Bean
    @org.springframework.core.annotation.Order(
        org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 2)
    org.springframework.web.server.WebFilter bodyReadProbe(RedisFixture fixture) {
      return (exchange, chain) -> {
        var original = exchange.getRequest();
        return chain.filter(
            exchange
                .mutate()
                .request(
                    new org.springframework.http.server.reactive.ServerHttpRequestDecorator(
                        original) {
                      @Override
                      public Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
                        return Flux.defer(
                            () -> {
                              fixture.bodySubscriptions.incrementAndGet();
                              return fixture
                                  .loadReactive(SID)
                                  .doOnNext(
                                      session -> {
                                        if (!"consumed"
                                            .equals(session.featureState().get(GRANT_KEY))) {
                                          fixture.consumedBeforeBody.set(false);
                                        }
                                      })
                                  .thenMany(original.getBody());
                            });
                      }
                    })
                .build());
      };
    }
  }

  static class RedisFixture implements AutoCloseable {
    private final Process server;
    private final LettuceConnectionFactory connection;
    private final ReactiveStringRedisTemplate redis;
    private final RedisCacheKeyFactory keys = new RedisCacheKeyFactory("m4-http");
    final AuthenticatedSessionProtectorPort protector =
        new AesGcmAuthenticatedSessionProtector(
            Base64.getEncoder().encodeToString(new byte[32]), JSON);
    final AuthenticatedSessionPort sessions;
    final AuthenticatedSessionConcurrencyService concurrency;
    final MutableClock clock = new MutableClock();
    final AtomicInteger bodySubscriptions = new AtomicInteger();
    final AtomicBoolean consumedBeforeBody = new AtomicBoolean(true);
    final AtomicInteger consumeWrites = new AtomicInteger();
    volatile String fault = "";

    RedisFixture(Path directory) throws Exception {
      int port;
      try (var socket = new ServerSocket(0)) {
        port = socket.getLocalPort();
      }
      var builder =
          new ProcessBuilder(
                  System.getProperty("m15.redis.server"),
                  "--bind",
                  "127.0.0.1",
                  "--port",
                  Integer.toString(port),
                  "--save",
                  "",
                  "--appendonly",
                  "no",
                  "--protected-mode",
                  "yes",
                  "--dir",
                  directory.toString())
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD);
      var libraryPath = System.getProperty("m15.redis.libraryPath");
      if (libraryPath != null) builder.environment().put("LD_LIBRARY_PATH", libraryPath);
      server = builder.start();
      connection = new LettuceConnectionFactory("127.0.0.1", port);
      connection.afterPropertiesSet();
      connection.start();
      redis = new ReactiveStringRedisTemplate(connection);
      assertThat(
              Mono.defer(() -> redis.getConnectionFactory().getReactiveConnection().ping())
                  .timeout(Duration.ofSeconds(1))
                  .retryWhen(
                      Retry.fixedDelay(400, Duration.ofMillis(25))
                          .filter(error -> server.isAlive()))
                  .block(Duration.ofSeconds(10)))
          .isEqualTo("PONG");
      sessions =
          new RedisAuthenticatedSessionAdapter(
              new RedisStringCacheAdapter(redis), keys, new RedisAuthenticatedSessionJsonMapper());
      var cas = new RedisAuthenticatedSessionCasUpdateAdapter(redis, keys);
      concurrency =
          new AuthenticatedSessionConcurrencyService(
              sessions,
              protector,
              command ->
                  Mono.defer(
                      () -> {
                        if (command.usableUntil() == null) return cas.updateIfCurrent(command);
                        consumeWrites.incrementAndGet();
                        return switch (fault) {
                          case "error" ->
                              Mono.error(new IllegalStateException("consume-secret-marker"));
                          case "empty" -> Mono.empty();
                          case "system" ->
                              Mono.just(
                                  AuthenticatedSessionCasUpdateResult.of(
                                      AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE));
                          case "conflict" ->
                              Mono.just(
                                  AuthenticatedSessionCasUpdateResult.of(
                                      AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH));
                          case "lost-ack" ->
                              cas.updateIfCurrent(command)
                                  .then(Mono.error(new TimeoutException("consume-secret-marker")));
                          default -> cas.updateIfCurrent(command);
                        };
                      }),
              new SecureAuthenticatedSessionRevisionGenerator(),
              clock);
    }

    void reset() {
      fault = "";
      clock.offset = Duration.ZERO;
      bodySubscriptions.set(0);
      consumedBeforeBody.set(true);
      consumeWrites.set(0);
      for (String id : List.of(SID, OTHER_SID)) {
        Instant now = clock.instant();
        write(
            new AuthenticatedSession(
                new AuthenticatedSessionId(id),
                new ActorContext("authenticated-actor-marker", ActorUserRole.ROLE_USER_MBR),
                List.of(account(REF), account(OTHER_REF)),
                AuthenticatedSessionStatus.ACTIVE,
                new Sha256CsrfTokenHasher().hash("csrf-fixture"),
                now,
                now,
                now.plusSeconds(600),
                now.plusSeconds(900),
                new SessionRevision("a".repeat(32)),
                null,
                Map.of("unrelated", "keep")));
      }
    }

    private AuthenticatedSessionAccount account(String ref) {
      return new AuthenticatedSessionAccount(
          new AuthenticatedAccountRef(ref),
          "TEST",
          "fixture-policy",
          "fixture-cert",
          "TEST",
          null,
          null,
          null);
    }

    void write(AuthenticatedSession session) {
      var protectedSession = protector.protect(session);
      var json = new RedisAuthenticatedSessionJsonMapper().toJson(protectedSession);
      redis
          .opsForValue()
          .set(key(protectedSession.authenticatedSessionId()), json, Duration.ofSeconds(600))
          .block();
    }

    AuthenticatedSession load(String id) {
      return loadReactive(id).block();
    }

    Mono<AuthenticatedSession> loadReactive(String id) {
      return sessions
          .load(new LoadAuthenticatedSessionCommand(new AuthenticatedSessionId(id), "m4-http"))
          .map(stored -> protector.unprotect(stored.orElseThrow()));
    }

    private String key(String id) {
      return keys.key("auth:authenticated-session", id);
    }

    @Override
    public void close() throws Exception {
      connection.destroy();
      server.destroy();
      if (!server.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
        server.destroyForcibly().waitFor();
    }
  }

  static class MutableClock extends Clock {
    volatile Duration offset = Duration.ZERO;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.now().plus(offset);
    }
  }
}
