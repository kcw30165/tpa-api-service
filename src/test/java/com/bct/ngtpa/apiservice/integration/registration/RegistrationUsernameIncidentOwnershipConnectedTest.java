package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.RegistrationController;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config
    .RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CancelRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper
    .RegistrationUsernameCheckWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequiredRequestIdWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.client.ApimContextPropagationExchangeFilter;
import com.bct.ngtpa.apiservice.adapter.out.apim.client.ApimRequestLoggingExchangeFilter;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCertificateHelper;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeOutcomeLogger;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.DefaultApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.ApimForgotPasswordUsernameLookupAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.ApimRegistrationUsernameAvailabilityAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.SharedApimTrpChkCustIdTransport;
import com.bct.ngtpa.apiservice.adapter.out.config.ConfigBackedDownstreamErrorClassificationAdapter;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.UsernamePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationEligibleTrustSet;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassification;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassificationRequest;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionMutationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RegistrationUsernameCheckEncryptedState;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.RevisionedAuthFlowSessionMutationCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.RegistrationUsernameAvailabilityCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start
    .StartForgotPasswordUsernameLookupCommand;
import com.bct.ngtpa.apiservice.infrastructure.logging.ExecutionLoggingAspect;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import com.bct.ngtpa.apiservice.infrastructure.security.SecurityConfig;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = RegistrationUsernameIncidentOwnershipConnectedTest.TestApplication.class,
    properties = {
      "config-service.base-url=",
      "api.security.require-authentication=false",
      "spring.main.banner-mode=off",
      "apim.encryption.enabled=false",
      "apim.retry.business-call.max-attempts=3",
      "apim.retry.business-call.backoff-milliseconds=0",
      "request-logging.enabled=true",
      "request-logging.body-logging.enabled=false"
    })
@AutoConfigureWebTestClient
@Execution(ExecutionMode.SAME_THREAD)
class RegistrationUsernameIncidentOwnershipConnectedTest {
  private static final String SESSION_ID = "afs_r1c_session";
  private static final String USERNAME_MARKER = "r1c-username-marker-01";
  private static final String TRUST_CODE_MARKER = "r1c-trust-code-marker";
  private static final String ACCOUNT_ENV_MARKER = "r1c-account-env-marker";
  private static final String TOKEN_MARKER = "r1c-token-marker";
  private static final String API_KEY_MARKER = "r1c-api-key-marker";
  private static final String CLIENT_SECRET_MARKER = "r1c-client-secret-marker";
  private static final String CYPHER_MARKER = "r1c-csrf-marker";
  private static final String APIM_PATH = SharedApimTrpChkCustIdTransport.API_NAME;
  private static final String REQUEST_PATH = "/api/v1/auth/registration/username/check";

  @Autowired WebTestClient webTestClient;
  @Autowired ConfigurableApplicationContext applicationContext;
  @Autowired Environment environment;
  @Autowired ObjectMapper objectMapper;
  @Autowired ApimProperties apimProperties;
  @Autowired RequestLoggingWebFilter requestLoggingWebFilter;
  @Autowired RegistrationController registrationController;
  @Autowired CheckRegistrationUsernameService usernameService;
  @Autowired ApimRegistrationUsernameAvailabilityAdapter usernameAdapter;
  @Autowired ConfigBackedDownstreamErrorClassificationAdapter classificationAdapter;
  @Autowired SharedApimTrpChkCustIdTransport sharedTransport;
  @Autowired DefaultApimExchangeExecutor executor;
  @Autowired ApimWebClientFacade facade;
  @Autowired ApimExchangeOutcomeLogger outcomeLogger;
  @Autowired ExecutionLoggingAspect executionLoggingAspect;
  @Autowired StatefulSessions sessions;
  @Autowired ScenarioProfileResolver profileResolver;
  @Autowired ScenarioTokenService tokenService;
  @Autowired ScenarioExchange scenarioExchange;
  @Autowired ExecutionProbe executionProbe;
  @Autowired TestProtector protector;

  private Logger rootLogger;
  private ThreadSafeAppender applicationEvents;

  @BeforeEach
  void captureApplicationEvents() {
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    applicationEvents = new ThreadSafeAppender();
    applicationEvents.start();
    rootLogger.addAppender(applicationEvents);
    sessions.reset();
    protector.reset();
    scenarioExchange.reset();
    profileResolver.useSuccessfulProfile();
    tokenService.useSuccessfulToken();
  }

  @AfterEach
  void detachApplicationEventCapture() {
    rootLogger.detachAppender(applicationEvents);
    applicationEvents.stop();
  }

  @Test
  void springContextLoads() {
    assertThat(webTestClient).isNotNull();
  }

  @Test
  void missingSessionRequestTraversesRealHttpFilterAndControllerWithoutIncidentError() {
    String requestId = "r1c-missing-session-request";
    int startIndex = applicationEvents.size();

    webTestClient
        .post()
        .uri(REQUEST_PATH)
        .header("X-Request-Id", requestId)
        .header("X-XSRF-TOKEN", CYPHER_MARKER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody(USERNAME_MARKER))
        .exchange()
        .expectStatus()
        .isBadRequest();

    List<ILoggingEvent> requestEvents = awaitRequestCompletion(requestId, startIndex);
    assertThat(errorEvents(requestEvents)).isEmpty();
    assertThat(scenarioExchange.calls()).isZero();
    assertHttpObservation(requestEvents, requestId, 400, Level.WARN);
    assertNoSensitiveMarkers(requestEvents, USERNAME_MARKER, TRUST_CODE_MARKER, CYPHER_MARKER);
  }

  @Test
  void explicitBusinessRejectionDispatchesOnceWithoutIncidentError() {
    String csrf = "r1c-csrf-explicit-marker";
    String requestId = "r1c-explicit-rejection-request";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.OK,
        "{\"response\":{\"err-message\":\"err.tpa.username.taken\",\"data\":[]}}");
    int startIndex = applicationEvents.size();

    usernameRequest(csrf, requestId)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.registration.username.isRegistered");

    List<ILoggingEvent> requestEvents = awaitRequestCompletion(requestId, startIndex);
    assertThat(scenarioExchange.calls()).isEqualTo(1);
    assertThat(
            classificationAdapter.classify(
                new DownstreamErrorClassificationRequest(
                    "registration-username-check",
                    "APIM_TAS",
                    "TRPChkCustId",
                    "err.tpa.username.taken")))
        .isEqualTo(
            new DownstreamErrorClassification(
                "DOWNSTREAM_REJECTION",
                "ERROR",
                "err.registration.username.isRegistered",
                422,
                "username",
                false,
                null,
                false));
    assertThat(scenarioExchange.observations())
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.path()).isEqualTo("/apim" + APIM_PATH);
              assertThat(observation.headerNames()).isNotEmpty();
            });
    assertThat(errorEvents(requestEvents)).isEmpty();
    assertHttpObservation(requestEvents, requestId, 422, Level.WARN);
    assertThat(requestEvents)
        .filteredOn(
            event ->
                event
                    .getFormattedMessage()
                    .contains("\"event\":\"http.request.failed\""))
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    assertNoAdapterExecutionEvents(requestEvents);
    assertNoSensitiveMarkers(requestEvents, requestMarkers(csrf));
  }

  @Test
  void unmappedBusinessRejectionUsesConfiguredFallbackWithoutIncidentError() {
    String csrf = "r1c-csrf-fallback-marker";
    String downstreamMarker = "r1c-unmapped-code-private-marker";
    String requestId = "r1c-business-fallback-request";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.OK,
        "{\"response\":{\"err-message\":\"" + downstreamMarker + "\",\"data\":[]}}");
    int startIndex = applicationEvents.size();

    usernameRequest(csrf, requestId)
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.registration.username.checkFailed");

    List<ILoggingEvent> requestEvents = awaitRequestCompletion(requestId, startIndex);
    assertThat(scenarioExchange.calls()).isEqualTo(1);
    assertThat(
            classificationAdapter.classify(
                new DownstreamErrorClassificationRequest(
                    "registration-username-check",
                    "APIM_TAS",
                    "TRPChkCustId",
                    downstreamMarker)))
        .isEqualTo(
            new DownstreamErrorClassification(
                "DOWNSTREAM_REJECTION",
                "ERROR",
                "err.registration.username.checkFailed",
                422,
                "PAGE",
                false,
                null,
                true));
    assertThat(errorEvents(requestEvents)).isEmpty();
    assertHttpObservation(requestEvents, requestId, 422, Level.WARN);
    assertNoAdapterExecutionEvents(requestEvents);
    assertNoSensitiveMarkers(requestEvents, requestMarkers(csrf));
    assertNoSensitiveMarkers(requestEvents, downstreamMarker);
  }

  @Test
  void http503RetriesThroughMaximumSafeBusinessAttempts() throws IOException {
    String csrf = "r1c-csrf-http-503";
    String marker = "r1c-http-503-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"" + marker + "\"}");
    List<ILoggingEvent> requestEvents =
        assertTechnicalFailure(
            "http-503",
            csrf,
            "r1c-http-503-request",
            "r1c-http503-user1!",
            marker,
            3,
            "DOWNSTREAM_TRANSPORT",
            "APIM_TECHNICAL");
    List<ILoggingEvent> facadeWarnings =
        requestEvents.stream()
            .filter(
                event ->
                    event.getLoggerName().equals(ApimWebClientFacade.class.getName())
                        && event.getLevel() == Level.WARN)
            .toList();
    assertThat(facadeWarnings).hasSize(5);
    assertThat(facadeWarnings)
        .filteredOn(
            event ->
                event.getFormattedMessage().equals("APIM request failed statusClass=HTTP_ERROR"))
        .hasSize(3);
    assertThat(facadeWarnings)
        .filteredOn(
            event ->
                event
                    .getFormattedMessage()
                    .startsWith("Retrying transient APIM business call path="))
        .hasSize(2);
  }

  @Test
  void http400DoesNotRetryNonTransientBusinessCall() throws IOException {
    String csrf = "r1c-csrf-http-400";
    String marker = "r1c-http-400-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(HttpStatus.BAD_REQUEST, "{\"error\":\"" + marker + "\"}");
    assertTechnicalFailure(
        "http-400",
        csrf,
        "r1c-http-400-request",
        "r1c-http400-user1!",
        marker,
        1,
        "DOWNSTREAM_TRANSPORT",
        "APIM_TECHNICAL");
  }

  @Test
  void emptyProfileResolutionFailsBeforeExchange() throws IOException {
    String csrf = "r1c-csrf-empty-profile";
    String marker = "r1c-empty-profile-private-marker";
    sessions.seedOtpVerified(csrf);
    profileResolver.useEmptyProfile();
    // This marker is only the synthetic username used to prove it is not logged, not resolver context.
    assertTechnicalFailure(
        "profile-empty",
        csrf,
        "r1c-profile-empty-request",
        marker,
        marker,
        0,
        "LOCAL_SYSTEM",
        "LOCAL_SYSTEM");
  }

  @Test
  void failedProfileResolutionFailsBeforeExchange() throws IOException {
    String csrf = "r1c-csrf-failed-profile";
    String marker = "client_secret=r1c-profile-error-private-marker";
    sessions.seedOtpVerified(csrf);
    profileResolver.failProfile(new IllegalStateException(marker));
    assertTechnicalFailure(
        "profile-error",
        csrf,
        "r1c-profile-error-request",
        "r1c-profileerror-user1!",
        marker,
        0,
        "LOCAL_SYSTEM",
        "LOCAL_SYSTEM");
  }

  @Test
  void connectionRefusedRetriesThroughMaximumSafeBusinessAttempts() throws IOException {
    String csrf = "r1c-csrf-connect-failure";
    String marker = "r1c-connect-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.failWith(
        new WebClientRequestException(
            new ConnectException(marker),
            HttpMethod.POST,
            URI.create("http://127.0.0.1/apim/ws/NGTPA/v1/TRPChkCustId"),
            new HttpHeaders()));
    assertTechnicalFailure(
        "connection-refused",
        csrf,
        "r1c-connection-refused-request",
        "r1c-connect-user1!",
        marker,
        3,
        "DOWNSTREAM_TRANSPORT",
        "APIM_TECHNICAL");
  }

  @Test
  void timeoutRetriesThroughMaximumSafeBusinessAttempts() throws IOException {
    String csrf = "r1c-csrf-timeout";
    String marker = "r1c-timeout-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.failWith(new TimeoutException(marker));
    assertTechnicalFailure(
        "timeout",
        csrf,
        "r1c-timeout-request",
        "r1c-timeout-user1!",
        marker,
        3,
        "DOWNSTREAM_TRANSPORT",
        "APIM_TECHNICAL");
  }

  @Test
  void contractInvalidResponseDispatchesOnceWithOneTerminalErrorAndWarnObservation() {
    String csrf = "r1c-csrf-contract-marker";
    String requestId = "r1c-contract-invalid-request";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.OK, "{\"response\":{\"err-message\":\"\",\"data\":[]}}");
    int startIndex = applicationEvents.size();

    usernameRequest(csrf, requestId)
        .expectStatus()
        .isEqualTo(502)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].type")
        .isEqualTo("BUSINESS")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.workflow.downstream.unavailable");

    List<ILoggingEvent> requestEvents = awaitRequestCompletion(requestId, startIndex);
    assertThat(scenarioExchange.calls()).isEqualTo(1);
    assertThat(scenarioExchange.observations())
        .singleElement()
        .satisfies(
            observation -> assertThat(observation.path()).isEqualTo("/apim" + APIM_PATH));

    List<ILoggingEvent> errors = errorEvents(requestEvents);
    assertThat(errors)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLoggerName())
                  .isEqualTo(CheckRegistrationUsernameService.class.getName());
              assertThat(event.getFormattedMessage())
                  .contains(
                      "event=registration.usernameCheck.failure",
                      "origin=APIM_TECHNICAL",
                      "requestId=" + requestId);
              assertThat(event.getThrowableProxy()).isNull();
            });

    assertThat(requestEvents)
        .anyMatch(
            event ->
                event.getLevel() == Level.WARN
                    && event
                        .getFormattedMessage()
                        .contains("\"event\":\"apim.contract.invalid\""));
    assertThat(requestEvents)
        .anyMatch(
            event ->
                event.getLevel() == Level.WARN
                    && event
                        .getFormattedMessage()
                        .contains("event=registration.usernameCheck.apimFailureDiagnostic")
                    && event.getFormattedMessage().contains("category=DOWNSTREAM_CONTRACT"));
    assertHttpObservation(requestEvents, requestId, 502, Level.WARN);
    assertNoAdapterExecutionEvents(requestEvents);
    assertNoSensitiveMarkers(requestEvents, requestMarkers(csrf));
  }

  @Test
  void malformedResponseDtoFailsAfterOneExchangeWithoutLeakingMarker() throws IOException {
    String csrf = "r1c-csrf-malformed-dto";
    String marker = "r1c-malformed-data-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.OK,
        "{\"response\":{\"err-message\":\"\",\"data\":\"" + marker + "\"}}");

    List<ILoggingEvent> requestEvents =
        assertTechnicalFailure(
            "crypto-dto-mapping",
            csrf,
            "r1c-crypto-dto-mapping-request",
            "r1c-dtofailure-user1!",
            marker,
            1,
            "DOWNSTREAM_INVALID_RESPONSE",
            "APIM_TECHNICAL");

    assertThat(allEventText(requestEvents)).contains("ApimResponseMappingException");
  }

  @Test
  void protectionFailureProducesLocalStateIncidentAfterOneSuccessfulExchange() throws IOException {
    String csrf = "r1c-csrf-protect-failure";
    String marker = "r1c-protect-failure-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.OK,
        "{\"response\":{\"err-message\":\"\",\"data\":[{\"cust-id\":\"synthetic-customer\","
            + "\"isExist\":false}]}}");
    protector.failProtect(new IllegalStateException(marker));

    assertLocalStateFailure(
        "protection-failure",
        csrf,
        "r1c-protection-failure-request",
        "r1c-protectionfailure-user1!",
        marker);
  }

  @Test
  void persistenceFailureProducesLocalStateIncidentAfterOneSuccessfulExchange()
      throws IOException {
    String csrf = "r1c-csrf-persistence-failure";
    String marker = "r1c-persistence-failure-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.OK,
        "{\"response\":{\"err-message\":\"\",\"data\":[{\"cust-id\":\"synthetic-customer\","
            + "\"isExist\":false}]}}");
    sessions.failUpdates(new IllegalStateException(marker));

    assertLocalStateFailure(
        "persistence-failure",
        csrf,
        "r1c-persistence-failure-request",
        "r1c-persistencefailure-user1!",
        marker);
  }

  @Test
  void sameUsernameRepeatUsesStoredProtectedStateWithoutSecondExchange() throws IOException {
    String csrf = "r1c-csrf-same-username";
    String requestId = "r1c-same-username-first-request";
    String repeatedRequestId = "r1c-same-username-repeat-request";
    String username = "r1c-repeat-user1!";
    String marker = "r1c-same-username-response-private-marker";
    sessions.seedOtpVerified(csrf);
    scenarioExchange.respond(
        HttpStatus.OK,
        "{\"response\":{\"err-message\":\"\",\"data\":[{\"cust-id\":\""
            + marker
            + "\",\"isExist\":false}]}}");
    int startIndex = applicationEvents.size();

    usernameRequest(csrf, requestId, username)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("SUCCESS");
    List<ILoggingEvent> firstEvents = awaitRequestCompletion(requestId, startIndex);
    assertThat(errorEvents(firstEvents)).isEmpty();
    assertThat(scenarioExchange.calls()).isEqualTo(1);
    assertThat(protector.unprotectCalls()).isZero();

    int repeatStartIndex = applicationEvents.size();
    int unprotectCallsBeforeRepeat = protector.unprotectCalls();
    usernameRequest(csrf, repeatedRequestId, username)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("SUCCESS");
    List<ILoggingEvent> repeatEvents =
        awaitRequestCompletion(repeatedRequestId, repeatStartIndex);

    assertThat(errorEvents(repeatEvents)).isEmpty();
    assertThat(scenarioExchange.calls()).isEqualTo(1);
    // USERNAME_VERIFIED checks existingUsernameCheck once, and it calls unprotect once.
    assertThat(protector.unprotectCalls()).isEqualTo(unprotectCallsBeforeRepeat + 1);
    List<ILoggingEvent> bothRequests = applicationEvents.eventsFrom(startIndex);
    if (Boolean.getBoolean("r1c.evidence")) {
      writeEventTable("same-username-repeat", bothRequests);
    }
    assertNoSensitiveMarkers(bothRequests, requestMarkers(csrf));
    assertNoSensitiveMarkers(bothRequests, username, marker);
  }

  @Test
  void legacyForgotPasswordCallerRetainsApimObserverErrorOwnership() throws IOException {
    String requestId = "r1c-legacy-forgot-password-request";
    String username = "r1c-legacy-user1!";
    String marker = "r1c-legacy-response-private-marker";
    scenarioExchange.respond(
        HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"" + marker + "\"}");
    ApimForgotPasswordUsernameLookupAdapter legacyAdapter =
        new ApimForgotPasswordUsernameLookupAdapter(executor, Map.of());
    int startIndex = applicationEvents.size();

    StepVerifier.create(
            legacyAdapter
                .lookup(
                    new StartForgotPasswordUsernameLookupCommand(
                        username, requestId, ActorUserRole.ROLE_USER_MBR))
                .contextWrite(
                    Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, requestId)))
        .expectError()
        .verify();

    List<ILoggingEvent> requestEvents = applicationEvents.eventsFrom(startIndex);
    if (Boolean.getBoolean("r1c.evidence")) {
      writeEventTable("legacy-forgot-password", requestEvents);
    }
    // The seven-argument request constructor defaults to ALLOW_RECOVERY/APIM_OBSERVER; this
    // caller sets SAFE_BUSINESS_CALL and the facade allows three attempts (ApimProperties).
    assertThat(scenarioExchange.calls()).isEqualTo(3);
    List<ILoggingEvent> errors = errorEvents(requestEvents);
    assertThat(errors).hasSize(5);
    assertThat(errors)
        .filteredOn(event -> event.getLoggerName().equals(ApimWebClientFacade.class.getName()))
        .hasSize(3);
    assertThat(errors)
        .filteredOn(event -> event.getLoggerName().equals(ApimExchangeOutcomeLogger.class.getName()))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getFormattedMessage())
                    .contains("\"event\":\"apim.exchange.failed\""));
    assertThat(errors)
        .filteredOn(
            event ->
                event.getLoggerName()
                    .equals(ApimForgotPasswordUsernameLookupAdapter.class.getName()))
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getFormattedMessage())
                    .contains("event=forgot-password.username-lookup.failed"));
    assertThat(requestEvents)
        .filteredOn(
            event ->
                event.getLoggerName().equals(ApimWebClientFacade.class.getName())
                    && event.getLevel() == Level.WARN)
        .hasSize(2);
    assertThat(requestEvents)
        .noneMatch(
            event ->
                event.getFormattedMessage().contains("\"event\":\"http.request.failed\""));
    assertThat(requestEvents)
        .noneMatch(
            event ->
                event.getFormattedMessage().contains("\"event\":\"method.execution")
                    && event
                        .getFormattedMessage()
                        .contains(ApimForgotPasswordUsernameLookupAdapter.class.getSimpleName()));
    assertNoSensitiveMarkers(requestEvents, username, marker);
  }

  @Test
  void focusedSpringGraphAndExecutionAspectAreActuallyWired() throws Exception {
    assertThat(environment.getProperty("apim.encryption.enabled", Boolean.class)).isFalse();
    assertThat(environment.getProperty("config-service.base-url")).isBlank();
    assertThat(apimProperties.getEncryption().isEnabled()).isFalse();
    assertThat(apimProperties.getRetry().getBusinessCall().getMaxAttempts()).isEqualTo(3);
    assertThat(webTestClient).isNotNull();
    assertThat(requestLoggingWebFilter).isExactlyInstanceOf(RequestLoggingWebFilter.class);
    assertThat(registrationController).isExactlyInstanceOf(RegistrationController.class);
    assertThat(usernameService).isExactlyInstanceOf(CheckRegistrationUsernameService.class);
    assertThat(usernameAdapter)
        .isExactlyInstanceOf(ApimRegistrationUsernameAvailabilityAdapter.class);
    assertThat(sharedTransport).isExactlyInstanceOf(SharedApimTrpChkCustIdTransport.class);
    assertThat(executor).isExactlyInstanceOf(DefaultApimExchangeExecutor.class);
    assertThat(facade).isExactlyInstanceOf(ApimWebClientFacade.class);
    assertThat(outcomeLogger).isExactlyInstanceOf(ApimExchangeOutcomeLogger.class);
    assertThat(executionLoggingAspect).isNotNull();
    assertThat(AopUtils.isAopProxy(executionProbe)).isTrue();

    assertThat(
            ApimRegistrationUsernameAvailabilityAdapter.class
                .getMethod(
                    "checkAvailability", RegistrationUsernameAvailabilityCommand.class)
                .getAnnotation(LogExecution.class))
        .isNull();

    int startIndex = applicationEvents.size();
    assertThat(executionProbe.probe()).isEqualTo("probe");
    List<ILoggingEvent> probeEvents = applicationEvents.eventsFrom(startIndex);
    assertThat(probeEvents)
        .anySatisfy(
            event ->
                assertThat(event.getFormattedMessage())
                    .contains(
                        "\"event\":\"method.execution.start\"",
                        "\"className\":\"ExecutionProbe\"",
                        "\"methodName\":\"probe\""));
  }

  @Test
  void focusedContextExcludesRedisConnectionFactory() {
    List<String> names = infrastructureBeanNames();
    assertThat(names)
        .contains(ConfigBackedDownstreamErrorClassificationAdapter.class.getName());
    assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
  }

  private WebTestClient.ResponseSpec usernameRequest(String csrf, String requestId) {
    return usernameRequest(csrf, requestId, USERNAME_MARKER);
  }

  private WebTestClient.ResponseSpec usernameRequest(
      String csrf, String requestId, String username) {
    return webTestClient
        .post()
        .uri(REQUEST_PATH)
        .cookie("auth_flow_sid", SESSION_ID)
        .header("X-Request-Id", requestId)
        .header("X-XSRF-TOKEN", csrf)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody(username))
        .exchange();
  }

  private List<ILoggingEvent> assertTechnicalFailure(
      String caseName,
      String csrf,
      String requestId,
      String username,
      String marker,
      int expectedExchanges,
      String expectedCategory,
      String expectedOrigin)
      throws IOException {
    int startIndex = applicationEvents.size();
    EntityExchangeResult<byte[]> response =
        usernameRequest(csrf, requestId, username).expectBody().returnResult();
    List<ILoggingEvent> requestEvents = awaitRequestCompletion(requestId, startIndex);
    if (Boolean.getBoolean("r1c.evidence")) {
      writeEventTable(caseName, requestEvents);
    }

    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getResponseBody()).isNotNull();
    JsonNode body = objectMapper.readTree(response.getResponseBody());
    assertThat(body.path("status").asText()).isEqualTo("SYSTEM_ERROR");
    assertThat(body.path("errors").path(0).path("type").asText()).isEqualTo("BUSINESS");
    assertThat(body.path("errors").path(0).path("code").asText())
        .isEqualTo("err.workflow.downstream.unavailable");

    // SharedApimTrpChkCustIdTransport.java:38-41 selects SAFE_BUSINESS_CALL and ALLOW_RECOVERY.
    // ApimWebClientFacade.java:352-378 caps transient retries at maxAttempts; ApimProperties.java:69
    // sets three total attempts. HTTP 400 stops after one, and profile resolution precedes exchange.
    assertThat(scenarioExchange.calls()).isEqualTo(expectedExchanges);
    List<ILoggingEvent> errors = errorEvents(requestEvents);
    assertThat(errors).hasSize(1);
    ILoggingEvent terminalError = errors.get(0);
    assertThat(terminalError.getLoggerName())
        .isEqualTo(CheckRegistrationUsernameService.class.getName());
    assertThat(terminalError.getFormattedMessage())
        .contains(
            "event=registration.usernameCheck.failure",
            "origin=" + expectedOrigin,
            "requestId=" + requestId);
    assertThat(terminalError.getThrowableProxy()).isNull();

    List<ILoggingEvent> adapterDiagnostics =
        requestEvents.stream()
            .filter(
                event ->
                    event.getLevel() == Level.WARN
                        && event.getLoggerName()
                            .equals(ApimRegistrationUsernameAvailabilityAdapter.class.getName())
                        && event
                            .getFormattedMessage()
                            .contains("event=registration.usernameCheck.apimFailureDiagnostic"))
            .toList();
    assertThat(adapterDiagnostics)
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getFormattedMessage())
                    .contains("category=" + expectedCategory));
    assertHttpObservation(requestEvents, requestId, 502, Level.WARN);
    assertNoAdapterExecutionEvents(requestEvents);
    assertNoSensitiveMarkers(requestEvents, requestMarkers(csrf));
    assertNoSensitiveMarkers(requestEvents, username, marker);
    return requestEvents;
  }

  private void assertLocalStateFailure(
      String caseName, String csrf, String requestId, String username, String marker)
      throws IOException {
    int startIndex = applicationEvents.size();
    EntityExchangeResult<byte[]> response =
        usernameRequest(csrf, requestId, username).expectBody().returnResult();
    List<ILoggingEvent> requestEvents = awaitRequestCompletion(requestId, startIndex);
    if (Boolean.getBoolean("r1c.evidence")) {
      writeEventTable(caseName, requestEvents);
    }

    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getResponseBody()).isNotNull();
    JsonNode body = objectMapper.readTree(response.getResponseBody());
    assertThat(body.path("status").asText()).isEqualTo("SYSTEM_ERROR");
    assertThat(body.path("errors").path(0).path("type").asText()).isEqualTo("BUSINESS");
    assertThat(body.path("errors").path(0).path("code").asText())
        .isEqualTo("err.workflow.downstream.unavailable");
    assertThat(scenarioExchange.calls()).isEqualTo(1);

    assertThat(errorEvents(requestEvents))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLoggerName())
                  .isEqualTo(CheckRegistrationUsernameService.class.getName());
              assertThat(event.getFormattedMessage())
                  .contains(
                      "event=registration.usernameCheck.failure",
                      "origin=LOCAL_STATE_FAILURE",
                      "requestId=" + requestId)
                  .doesNotContain("origin=APIM_TECHNICAL");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(requestEvents)
        .noneMatch(
            event ->
                event.getLoggerName()
                        .equals(ApimRegistrationUsernameAvailabilityAdapter.class.getName())
                    && event
                        .getFormattedMessage()
                        .contains("event=registration.usernameCheck.apimFailureDiagnostic"));
    assertHttpObservation(requestEvents, requestId, 502, Level.WARN);
    assertNoAdapterExecutionEvents(requestEvents);
    assertNoSensitiveMarkers(requestEvents, requestMarkers(csrf));
    assertNoSensitiveMarkers(requestEvents, username, marker);
  }

  private static void writeEventTable(String caseName, List<ILoggingEvent> events)
      throws IOException {
    Path directory = Path.of(System.getProperty("r1c.evidence.dir", "target/evidence"));
    Files.createDirectories(directory);
    StringBuilder table = new StringBuilder("logger\tlevel\tevent\n");
    for (ILoggingEvent event : events) {
      table
          .append(event.getLoggerName())
          .append('\t')
          .append(event.getLevel())
          .append('\t')
          .append(eventName(event))
          .append('\n');
    }
    Files.writeString(directory.resolve("events-" + caseName + ".txt"), table);
  }

  private static String eventName(ILoggingEvent event) {
    String message = event.getFormattedMessage();
    int jsonEvent = message.indexOf("\"event\":\"");
    int eventStart = jsonEvent >= 0 ? jsonEvent + 9 : message.indexOf("event=");
    if (eventStart < 0) {
      return "<none>";
    }
    if (jsonEvent < 0) {
      eventStart += "event=".length();
    }
    int eventEnd = message.indexOf(jsonEvent >= 0 ? '"' : ' ', eventStart);
    return eventEnd < 0 ? message.substring(eventStart) : message.substring(eventStart, eventEnd);
  }

  private static String requestBody(String username) {
    return "{\"selectedTrust\":{\"trustCode\":\""
        + TRUST_CODE_MARKER
        + "\",\"accountEnv\":\""
        + ACCOUNT_ENV_MARKER
        + "\"},\"username\":\""
        + username
        + "\"}";
  }

  private static List<String> requestMarkers(String csrf) {
    return List.of(
        USERNAME_MARKER,
        TRUST_CODE_MARKER,
        ACCOUNT_ENV_MARKER,
        TOKEN_MARKER,
        API_KEY_MARKER,
        CLIENT_SECRET_MARKER,
        csrf);
  }

  private void assertNoSensitiveMarkers(
      List<ILoggingEvent> requestEvents, String... markers) {
    assertThat(allEventText(requestEvents)).doesNotContain(markers);
  }

  private void assertNoSensitiveMarkers(
      List<ILoggingEvent> requestEvents, List<String> markers) {
    assertNoSensitiveMarkers(requestEvents, markers.toArray(String[]::new));
  }

  private void assertNoAdapterExecutionEvents(List<ILoggingEvent> requestEvents) {
    assertThat(requestEvents)
        .noneMatch(
            event -> {
              String message = eventText(event);
              return message.contains("\"event\":\"method.execution")
                  && message.contains(
                      ApimRegistrationUsernameAvailabilityAdapter.class.getSimpleName());
            });
  }

  private static void assertHttpObservation(
      List<ILoggingEvent> requestEvents, String requestId, int status, Level expectedLevel) {
    assertThat(requestEvents)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(expectedLevel);
              assertThat(event.getFormattedMessage())
                  .contains(
                      "\"event\":\"http.request.failed\"",
                      "\"requestId\":\"" + requestId + "\"",
                      "\"status\":" + status);
            });
  }

  private List<ILoggingEvent> awaitRequestCompletion(String requestId, int startIndex) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (true) {
      List<ILoggingEvent> events = applicationEvents.eventsFrom(startIndex);
      if (events.stream().anyMatch(event -> isCompletionEvent(event, requestId))) {
        return events;
      }
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("Timed out waiting for HTTP observation for " + requestId);
      }
      Thread.onSpinWait();
    }
  }

  private static boolean isCompletionEvent(ILoggingEvent event, String requestId) {
    String message = event.getFormattedMessage();
    return message.contains("\"requestId\":\"" + requestId + "\"")
        && (message.contains("\"event\":\"http.request.end\"")
            || message.contains("\"event\":\"http.request.failed\"")
            || message.contains("\"event\":\"http.request.error\""));
  }

  private static List<ILoggingEvent> errorEvents(List<ILoggingEvent> events) {
    return events.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
  }

  private static String allEventText(List<ILoggingEvent> events) {
    return events.stream().map(RegistrationUsernameIncidentOwnershipConnectedTest::eventText)
        .reduce("", (left, right) -> left + "\n" + right);
  }

  private List<String> infrastructureBeanNames() {
    return Arrays.stream(applicationContext.getBeanFactory().getBeanDefinitionNames())
        .filter(
            name -> {
              Class<?> type = applicationContext.getBeanFactory().getType(name, false);
              return containsInfrastructureTerm(name)
                  || (type != null && containsInfrastructureTerm(type.getName()));
            })
        .sorted()
        .toList();
  }

  private static boolean containsInfrastructureTerm(String value) {
    String normalized = value.toLowerCase(Locale.ROOT);
    return normalized.contains("redis")
        || normalized.contains("lettuce")
        || normalized.contains("datasource")
        || normalized.contains("jdbc")
        || normalized.contains("config");
  }

  private static String eventText(ILoggingEvent event) {
    StringBuilder text = new StringBuilder();
    text.append(event.getMessage()).append('\n').append(event.getFormattedMessage());
    if (event.getArgumentArray() != null) {
      text.append('\n').append(Arrays.deepToString(event.getArgumentArray()));
    }
    event.getMDCPropertyMap()
        .forEach((key, value) -> text.append('\n').append(key).append('=').append(value));
    appendThrowable(text, event.getThrowableProxy(), Collections.newSetFromMap(new IdentityHashMap<>()));
    return text.toString();
  }

  private static void appendThrowable(
      StringBuilder text, IThrowableProxy throwable, Set<IThrowableProxy> visited) {
    if (throwable == null || !visited.add(throwable)) {
      return;
    }
    text.append('\n')
        .append(throwable.getClassName())
        .append(": ")
        .append(throwable.getMessage());
    appendThrowable(text, throwable.getCause(), visited);
    for (IThrowableProxy suppressed : throwable.getSuppressed()) {
      appendThrowable(text, suppressed, visited);
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static ApimCredentialProfile profile() {
    return new ApimCredentialProfile(
        "r1c-profile",
        "r1c-client",
        CLIENT_SECRET_MARKER,
        API_KEY_MARKER,
        "http://127.0.0.1/token",
        "http://127.0.0.1/apim",
        List.of(),
        null);
  }

  private static final class ThreadSafeAppender extends AppenderBase<ILoggingEvent> {
    private final CopyOnWriteArrayList<ILoggingEvent> events = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      events.add(event);
    }

    private int size() {
      return events.size();
    }

    private List<ILoggingEvent> eventsFrom(int startIndex) {
      List<ILoggingEvent> snapshot = List.copyOf(events);
      return snapshot.subList(Math.min(startIndex, snapshot.size()), snapshot.size());
    }
  }

  static final class ScenarioExchange implements ExchangeFunction {
    private final AtomicInteger calls = new AtomicInteger();
    private final CopyOnWriteArrayList<RequestObservation> observations =
        new CopyOnWriteArrayList<>();
    private final AtomicReference<Supplier<ClientResponse>> responseFactory =
        new AtomicReference<>(() -> ClientResponse.create(HttpStatus.OK).body("{}").build());
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    void reset() {
      calls.set(0);
      observations.clear();
      failure.set(null);
      responseFactory.set(() -> ClientResponse.create(HttpStatus.OK).body("{}").build());
    }

    void respond(HttpStatus status, String body) {
      responseFactory.set(
          () ->
              ClientResponse.create(status)
                  .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                  .body(body)
                  .build());
    }

    void failWith(Throwable exception) {
      failure.set(exception);
    }

    int calls() {
      return calls.get();
    }

    List<RequestObservation> observations() {
      return List.copyOf(observations);
    }

    @Override
    public Mono<ClientResponse> exchange(ClientRequest request) {
      calls.incrementAndGet();
      observations.add(
          new RequestObservation(
              request.url().getPath(), Set.copyOf(request.headers().headerNames())));
      Throwable configuredFailure = failure.get();
      if (configuredFailure != null) {
        return Mono.error(configuredFailure);
      }
      return Mono.just(responseFactory.get().get());
    }
  }

  private record RequestObservation(String path, Set<String> headerNames) {}

  static final class ScenarioProfileResolver implements ApimCredentialProfileResolver {
    private final AtomicReference<Supplier<Mono<ApimCredentialProfile>>> result =
        new AtomicReference<>(() -> Mono.just(profile()));

    void useSuccessfulProfile() {
      result.set(() -> Mono.just(profile()));
    }

    void useEmptyProfile() {
      result.set(Mono::empty);
    }

    void failProfile(RuntimeException failure) {
      result.set(() -> Mono.error(failure));
    }

    @Override
    public Mono<ApimCredentialProfile> resolve(
        ApimDatabaseScope scope, ApimCredentialResolutionContext context) {
      return result.get().get();
    }
  }

  static final class ScenarioTokenService extends ApimTokenService {
    private final AtomicReference<Supplier<Mono<String>>> result =
        new AtomicReference<>(() -> Mono.just(TOKEN_MARKER));

    ScenarioTokenService(WebClient.Builder builder, ApimProperties properties) {
      super(builder, properties);
    }

    void useSuccessfulToken() {
      result.set(() -> Mono.just(TOKEN_MARKER));
    }

    @Override
    public Mono<String> getAccessToken(ApimCredentialProfile profile) {
      return result.get().get();
    }
  }

  static final class StatefulSessions implements AuthFlowSessionPort {
    private AuthFlowSession session;
    private long revision;
    private RuntimeException updateFailure;

    synchronized void reset() {
      session = null;
      revision = 0;
      updateFailure = null;
    }

    synchronized void failUpdates(RuntimeException failure) {
      updateFailure = failure;
    }

    synchronized void seedOtpVerified(String csrf) {
      Instant now = Instant.parse("2026-07-10T00:00:00Z");
      RegistrationLookupAccount eligibleTrust =
          new RegistrationLookupAccount(
              ACCOUNT_ENV_MARKER,
              TRUST_CODE_MARKER,
              null,
              null,
              null,
              null,
              null,
              Boolean.FALSE,
              null);
      session =
          new AuthFlowSession(
              new AuthFlowSessionId(SESSION_ID),
              AuthFlowScenario.REGISTRATION,
              AuthFlowStatus.OTP_VERIFIED,
              sha256(csrf),
              "synthetic-otp-challenge",
              new AuthFlowSessionData(
                  "EMAIL",
                  "synthetic-masked-destination",
                  Map.of(
                      RegistrationEligibleTrustSet.ATTRIBUTE_NAME,
                      RegistrationEligibleTrustSet.encode(List.of(eligibleTrust)))),
              now.minusSeconds(300),
              now.minusSeconds(60),
              now.plusSeconds(1800));
    }

    synchronized AuthFlowSession current() {
      return session;
    }

    @Override
    public Mono<AuthFlowSession> create(AuthFlowSessionCreateCommand command) {
      return Mono.error(new UnsupportedOperationException("Session creation is outside this fixture"));
    }

    @Override
    public synchronized Mono<Optional<AuthFlowSession>> load(AuthFlowSessionId id) {
      return Mono.just(Optional.ofNullable(session));
    }

    @Override
    public synchronized Mono<Optional<AuthFlowSessionSnapshot>> loadSnapshot(
        AuthFlowSessionId id) {
      return Mono.just(
          Optional.ofNullable(session)
              .map(current -> new AuthFlowSessionSnapshot(current, currentRevision())));
    }

    @Override
    public synchronized Mono<AuthFlowSessionMutationResult> updateRevisioned(
        RevisionedAuthFlowSessionMutationCommand command) {
      if (updateFailure != null) {
        RuntimeException failure = updateFailure;
        updateFailure = null;
        return Mono.error(failure);
      }
      if (session == null) {
        return Mono.just(
            AuthFlowSessionMutationResult.outcome(
                AuthFlowSessionMutationOutcome.SESSION_NOT_FOUND));
      }
      if (!session.equals(command.expected().session())
          || !currentRevision().equals(command.expected().revision())) {
        return Mono.just(
            AuthFlowSessionMutationResult.outcome(
                AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED));
      }
      session = command.updated();
      revision++;
      return Mono.just(
          new AuthFlowSessionMutationResult(
              AuthFlowSessionMutationOutcome.APPLIED,
              new AuthFlowSessionSnapshot(session, currentRevision())));
    }

    private SessionRevision currentRevision() {
      return new SessionRevision(String.format("%032x", revision));
    }
  }

  static final class TestPolicy implements AuthWorkflowPolicyPort {
    @Override
    public RegistrationAuthWorkflowPolicy registrationPolicy() {
      return new RegistrationAuthWorkflowPolicy(
          null,
          null,
          null,
          new UsernamePolicy(4, 128, true, true, true, true, "!@#$%^&*()_-"),
          null);
    }

    @Override
    public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
      return Optional.empty();
    }
  }

  static final class TestProtector implements RegistrationUsernameCheckProtectorPort {
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicInteger unprotectCalls = new AtomicInteger();
    private final AtomicReference<RuntimeException> protectFailure = new AtomicReference<>();
    private final Map<String, UsernameCheckState> protectedStates =
        new java.util.concurrent.ConcurrentHashMap<>();

    void reset() {
      sequence.set(0);
      unprotectCalls.set(0);
      protectFailure.set(null);
      protectedStates.clear();
    }

    void failProtect(RuntimeException failure) {
      protectFailure.set(failure);
    }

    int unprotectCalls() {
      return unprotectCalls.get();
    }

    @Override
    public RegistrationUsernameCheckEncryptedState protect(UsernameCheckState state) {
      RuntimeException failure = protectFailure.getAndSet(null);
      if (failure != null) {
        throw failure;
      }
      String ciphertext = "synthetic-ciphertext-" + sequence.incrementAndGet();
      protectedStates.put(ciphertext, state);
      return new RegistrationUsernameCheckEncryptedState(ciphertext, "test-v1", "TEST_REVERSIBLE");
    }

    @Override
    public UsernameCheckState unprotect(RegistrationUsernameCheckEncryptedState state) {
      unprotectCalls.incrementAndGet();
      return protectedStates.get(state.ciphertext());
    }
  }

  static class ExecutionProbe {
    @LogExecution("test.registration.incident")
    public String probe() {
      return "probe";
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      // The fixture uses plain HTTP and does not exercise TLS metrics.
      excludeName = {
          "org.springframework.boot.micrometer.metrics.autoconfigure.ssl.SslMetricsAutoConfiguration",
          "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
          "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration",
          "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
          "org.springframework.boot.data.redis.autoconfigure.health.DataRedisHealthContributorAutoConfiguration",
          "org.springframework.boot.data.redis.autoconfigure.health.DataRedisReactiveHealthContributorAutoConfiguration",
          "org.springframework.boot.data.redis.autoconfigure.observation.LettuceObservationAutoConfiguration"
      })
  @EnableConfigurationProperties(
      {CorsProperties.class, ApimProperties.class, BffPagesProperties.class})
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  @Import({SecurityConfig.class, ConfigBackedDownstreamErrorClassificationAdapter.class})
  static class TestApplication {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    MaskingProperties maskingProperties() {
      return new MaskingProperties();
    }

    @Bean
    MaskingService maskingService(MaskingProperties properties) {
      return new MaskingService(properties);
    }

    @Bean
    LoggingSanitizer loggingSanitizer(ObjectMapper mapper, MaskingService maskingService) {
      return new LoggingSanitizer(mapper, maskingService);
    }

    @Bean
    RequestLoggingProperties requestLoggingProperties() {
      RequestLoggingProperties properties = new RequestLoggingProperties();
      properties.setEnabled(true);
      properties.setLogHeaders(false);
      properties.setHeaderAllowlist(List.of());
      return properties;
    }

    @Bean
    RequestLoggingWebFilter requestLoggingWebFilter(
        RequestLoggingProperties properties,
        LoggingSanitizer loggingSanitizer,
        ObjectMapper mapper) {
      return new RequestLoggingWebFilter(properties, loggingSanitizer, mapper);
    }

    @Bean
    RequiredRequestIdWebFilter requiredRequestIdWebFilter(ObjectMapper mapper) {
      return new RequiredRequestIdWebFilter(mapper);
    }

    @Bean
    ExecutionLoggingAspect executionLoggingAspect(
        LoggingSanitizer loggingSanitizer, ObjectMapper mapper) {
      return new ExecutionLoggingAspect(loggingSanitizer, mapper);
    }

    @Bean
    ExecutionProbe executionProbe() {
      return new ExecutionProbe();
    }

    @Bean
    ScenarioExchange scenarioExchange() {
      return new ScenarioExchange();
    }

    @Bean
    WebClient apimWebClient(ScenarioExchange exchange) {
      return WebClient.builder().exchangeFunction(exchange).build();
    }

    @Bean
    WebClient.Builder webClientBuilder(ScenarioExchange exchange) {
      return WebClient.builder().exchangeFunction(exchange);
    }

    @Bean
    ScenarioProfileResolver profileResolver() {
      return new ScenarioProfileResolver();
    }

    @Bean
    ScenarioTokenService tokenService(WebClient.Builder builder, ApimProperties properties) {
      return new ScenarioTokenService(builder, properties);
    }

    @Bean
    ApimCertificateService certificateService(
        WebClient.Builder builder, ApimProperties properties) {
      return new ApimCertificateService(builder, properties, null) {
        @Override
        public Mono<PublicKey> getPublicKey(ApimCredentialProfile profile) {
          return Mono.error(
              new AssertionError("Certificate retrieval must not run with encryption disabled"));
        }
      };
    }

    @Bean
    ApimAppCertificateService appCertificateService(ApimProperties properties) {
      return new ApimAppCertificateService(
          properties, new ApimCertificateHelper(new BouncyCastleProvider()));
    }

    @Bean
    ApimContextPropagationExchangeFilter contextFilter() {
      return new ApimContextPropagationExchangeFilter();
    }

    @Bean
    ApimRequestLoggingExchangeFilter apimRequestFilter(
        RequestLoggingProperties requestLoggingProperties,
        ApimProperties properties,
        ObjectMapper mapper) {
      return new ApimRequestLoggingExchangeFilter(
          requestLoggingProperties, properties, mapper);
    }

    @Bean
    ApimWebClientFacade facade(
        WebClient apimWebClient,
        WebClient.Builder builder,
        ObjectMapper mapper,
        ScenarioProfileResolver profileResolver,
        ScenarioTokenService tokenService,
        ApimCertificateService certificateService,
        ApimProperties properties,
        ApimAppCertificateService appCertificateService,
        LoggingSanitizer loggingSanitizer,
        ApimContextPropagationExchangeFilter contextFilter,
        ApimRequestLoggingExchangeFilter apimRequestFilter) {
      return new ApimWebClientFacade(
          apimWebClient,
          builder,
          mapper,
          profileResolver,
          tokenService,
          certificateService,
          properties,
          appCertificateService,
          loggingSanitizer,
          contextFilter,
          apimRequestFilter);
    }

    @Bean
    ApimPayloadFieldTransformer apimPayloadFieldTransformer() {
      return new ApimPayloadFieldTransformer();
    }

    @Bean
    ApimRsaPayloadCrypto apimRsaPayloadCrypto(ObjectMapper mapper) {
      return new ApimRsaPayloadCrypto(mapper);
    }

    @Bean
    ApimAesKeyFactory apimAesKeyFactory() {
      return new ApimAesKeyFactory();
    }

    @Bean
    ApimPayloadCryptoService crypto(
        ApimProperties properties,
        ObjectMapper mapper,
        ApimPayloadFieldTransformer fieldTransformer,
        ApimRsaPayloadCrypto rsaCrypto,
        ApimAesKeyFactory aesKeyFactory,
        ApimAppCertificateService appCertificateService) {
      return new ApimPayloadCryptoService(
          properties,
          mapper,
          fieldTransformer,
          rsaCrypto,
          aesKeyFactory,
          appCertificateService);
    }

    @Bean
    ApimExchangeOutcomeLogger outcomeLogger(ObjectMapper mapper) {
      return new ApimExchangeOutcomeLogger(mapper);
    }

    @Bean
    DefaultApimExchangeExecutor executor(
        ApimWebClientFacade facade,
        ApimPayloadCryptoService crypto,
        ApimProperties properties,
        ApimExchangeOutcomeLogger outcomeLogger) {
      return new DefaultApimExchangeExecutor(facade, crypto, properties, outcomeLogger);
    }

    @Bean
    SharedApimTrpChkCustIdTransport sharedTransport(DefaultApimExchangeExecutor executor) {
      return new SharedApimTrpChkCustIdTransport(executor);
    }

    @Bean
    ApimRegistrationUsernameAvailabilityAdapter usernameAdapter(
        SharedApimTrpChkCustIdTransport transport) {
      return new ApimRegistrationUsernameAvailabilityAdapter(transport);
    }

    @Bean
    StatefulSessions sessions() {
      return new StatefulSessions();
    }

    @Bean
    AuthFlowSessionExpiryRefreshPort expiry(StatefulSessions sessions) {
      return (id, at) -> Mono.justOrEmpty(sessions.current());
    }

    @Bean
    AuthWorkflowPolicyPort policy() {
      return new TestPolicy();
    }

    @Bean
    TestProtector protector() {
      return new TestProtector();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    CheckRegistrationUsernameService usernameService(
        AuthFlowSessionPort sessions,
        AuthFlowSessionExpiryRefreshPort expiry,
        ApimRegistrationUsernameAvailabilityAdapter adapter,
        AuthWorkflowPolicyPort policy,
        ConfigBackedDownstreamErrorClassificationAdapter classification,
        TestProtector protector,
        Clock clock) {
      return new CheckRegistrationUsernameService(
          sessions, expiry, adapter, policy, classification, protector, clock);
    }

    @Bean
    AuthFlowCookieWriter cookieWriter() {
      return AuthFlowCookieWriter.defaultWriter();
    }

    @Bean
    RegistrationUsernameCheckWebMapper usernameMapper() {
      return new RegistrationUsernameCheckWebMapper();
    }

    @Bean
    RegistrationController controller(
        CheckRegistrationUsernameService usernameService,
        RegistrationUsernameCheckWebMapper usernameMapper,
        AuthFlowCookieWriter cookieWriter) {
      return new RegistrationController(
          mock(StartRegistrationUseCase.class),
          mock(RegistrationStartWebMapper.class),
          cookieWriter,
          mock(CompleteRegistrationUseCase.class),
          mock(CompleteRegistrationWebMapper.class),
          mock(CancelRegistrationUseCase.class),
          mock(CancelRegistrationWebMapper.class),
          mock(ResendRegistrationOtpUseCase.class),
          mock(RegistrationOtpResendWebMapper.class),
          mock(VerifyRegistrationOtpUseCase.class),
          mock(RegistrationOtpVerifyWebMapper.class),
          mock(RegistrationIdempotencyProperties.class),
          usernameService,
          usernameMapper);
    }
  }
}
