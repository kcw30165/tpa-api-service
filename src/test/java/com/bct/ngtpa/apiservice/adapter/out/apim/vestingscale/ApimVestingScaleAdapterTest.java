package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleCommand;
import com.bct.ngtpa.apiservice.application.exception.VestingScaleRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class ApimVestingScaleAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPGetVestingScale";
  private static final String POLICY_NO = "policy-sensitive-42";
  private static final String CERT_NO = "cert-sensitive-17";
  private static final String ACTOR_ID = "actor-sensitive-88";
  private static final String REQUEST_ID = "request-id-123";
  private static final ObjectMapper JSON = new ObjectMapper();

  private ApimExchangeExecutor executor;
  private FailureEventPort failureEventPort;
  private List<FailureEvent> events;
  private Logger mapperLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    executor = mock(ApimExchangeExecutor.class);
    failureEventPort = mock(FailureEventPort.class);
    events = new ArrayList<>();
    doAnswer(
            invocation -> {
              events.add(invocation.getArgument(0));
              return null;
            })
        .when(failureEventPort)
        .publish(any(FailureEvent.class));

    mapperLogger = (Logger) LoggerFactory.getLogger(TrpGetVestingScaleApimMapper.class);
    appender = new ListAppender<>();
    appender.start();
    mapperLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    mapperLogger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void buildsTheAccountEnvironmentReadRequestFromTheApplicationCommand() throws Exception {
    respond(successEnvelope());

    StepVerifier.create(adapter().load(command()).contextWrite(requestContext()))
        .assertNext(result -> assertThat(result.plans()).isEmpty())
        .verifyComplete();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ApimExchangeRequest<TrpGetVestingScaleApimRequest, JsonNode>> captor =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    org.mockito.Mockito.verify(executor).postEnvelope(captor.capture());
    ApimExchangeRequest<TrpGetVestingScaleApimRequest, JsonNode> exchange = captor.getValue();
    assertThat(exchange.path()).isEqualTo(PATH);
    assertThat(exchange.request())
        .isEqualTo(
            new TrpGetVestingScaleApimRequest(ACTOR_ID, "role_user_mbr", POLICY_NO, CERT_NO));
    assertThat(exchange.requestType()).isEqualTo(TrpGetVestingScaleApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(JsonNode.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(exchange.credentialContext().accountEnv()).isEqualTo("DB");
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(exchange.resubmissionPolicy()).isEqualTo(ApimResubmissionPolicy.ALLOW_RECOVERY);
    assertThat(exchange.incidentOwnership()).isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "err.tpa.login.user.id.invalid",
        "err.tpa.cert.no.not.found",
        "err.tpa.cert.no.required"
      })
  void classifiesAccountIdentityMessagesAsContractFailuresWithoutLoggingIdentifiers(String code) {
    respond(envelope(code, null));

    StepVerifier.create(adapter().load(command()).contextWrite(requestContext()))
        .expectError(VestingScaleRetrievalException.class)
        .verify();

    FailureEvent event = events.getFirst();
    assertThat(event.origin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
    assertThat(event.requestId()).isEqualTo(REQUEST_ID);
    assertThat(event.safeDimensions())
        .containsExactly(Map.entry("reason", "ACCOUNT_CONTEXT_MISMATCH"));
    assertThat(event.safeDimensions().keySet()).containsExactly("reason");
    assertThat(event.internalCode()).isNull();
    assertThat(event.publicCode()).isEqualTo("err.vesting-scale.retrieval.unavailable");
    assertThat(event.httpStatus()).isEqualTo(503);
    assertThat(event.retryable()).isTrue();
    assertThat(event.toString()).doesNotContain(POLICY_NO, CERT_NO, ACTOR_ID, code);
  }

  @Test
  void mapsOtherNonBlankErrorMessagesToTheGenericContractReasonBeforeReadingData() {
    respond(envelope("err.tpa.some.other.failure", null));

    StepVerifier.create(adapter().load(command()))
        .expectError(VestingScaleRetrievalException.class)
        .verify();

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().origin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
    assertThat(events.getFirst().safeDimensions())
        .containsExactly(Map.entry("reason", "UNMAPPED_ERROR_CODE"));
    assertThat(events.getFirst().internalCode()).isNull();
  }

  @ParameterizedTest
  @MethodSource("malformedEnvelopes")
  void reportsMissingOrInvalidEnvelopeCardinalityAsMalformed(Object response) {
    if (response == null) {
      when(executor.<TrpGetVestingScaleApimRequest, JsonNode>postEnvelope(any()))
          .thenReturn(Mono.empty());
    } else {
      @SuppressWarnings("unchecked")
      ApimResponseEnvelope<JsonNode> envelope = (ApimResponseEnvelope<JsonNode>) response;
      respond(envelope);
    }

    StepVerifier.create(adapter().load(command()))
        .expectError(VestingScaleRetrievalException.class)
        .verify();

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().origin()).isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
    assertThat(events.getFirst().safeDimensions())
        .containsExactly(Map.entry("reason", "MALFORMED_RESPONSE"));
  }

  @Test
  void logsUnknownConfigItemIdAndReturnsSuccessfulData() throws Exception {
    JsonNode item =
        JSON.readTree(
            """
            {
              "member": {},
              "plans": [],
              "config": [{"item-id":"future-field","config-value":"READONLY"}]
            }
            """);
    respond(envelope("", List.of(item)));

    StepVerifier.create(adapter().load(command()))
        .assertNext(
            result -> {
              assertThat(result.plans()).isEmpty();
              assertThat(result.config()).isEmpty();
            })
        .verifyComplete();

    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel().toString()).isEqualTo("WARN");
              assertThat(event.getFormattedMessage()).contains("future-field");
              assertThat(event.getFormattedMessage())
                  .doesNotContain(POLICY_NO, CERT_NO, ACTOR_ID, "member");
            });
    assertThat(events).isEmpty();
  }

  @Test
  void wrapsTransportFailureAndPublishesItsTechnicalOrigin() {
    when(executor.<TrpGetVestingScaleApimRequest, JsonNode>postEnvelope(any()))
        .thenReturn(Mono.error(new TimeoutException("sensitive downstream text")));

    StepVerifier.create(adapter().load(command()))
        .expectError(VestingScaleRetrievalException.class)
        .verify();

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().origin()).isEqualTo(FailureOrigin.DOWNSTREAM_TRANSPORT);
    assertThat(events.getFirst().toString()).doesNotContain("sensitive downstream text");
  }

  static List<org.junit.jupiter.params.provider.Arguments> malformedEnvelopes() {
    var noResponse = new ApimResponseEnvelope<JsonNode>();
    var missingData = envelope("", null);
    var emptyData = envelope("", List.of());
    var multipleData = envelope("", List.of(json("{}"), json("{}")));
    var nullItem = envelope("", Arrays.asList((JsonNode) null));
    return List.of(
        org.junit.jupiter.params.provider.Arguments.of((Object) null),
        org.junit.jupiter.params.provider.Arguments.of(noResponse),
        org.junit.jupiter.params.provider.Arguments.of(missingData),
        org.junit.jupiter.params.provider.Arguments.of(emptyData),
        org.junit.jupiter.params.provider.Arguments.of(multipleData),
        org.junit.jupiter.params.provider.Arguments.of(nullItem));
  }

  private ApimVestingScaleAdapter adapter() {
    return new ApimVestingScaleAdapter(executor, failureEventPort, PATH);
  }

  private VestingScaleCommand command() {
    return new VestingScaleCommand("DB", POLICY_NO, CERT_NO, ACTOR_ID, ActorUserRole.ROLE_USER_MBR);
  }

  private Context requestContext() {
    return Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, REQUEST_ID);
  }

  private void respond(ApimResponseEnvelope<JsonNode> response) {
    when(executor.<TrpGetVestingScaleApimRequest, JsonNode>postEnvelope(any()))
        .thenReturn(Mono.just(response));
  }

  private static ApimResponseEnvelope<JsonNode> successEnvelope() throws Exception {
    return envelope(
        "",
        List.of(
            JSON.readTree(
                """
                {"member":{},"plans":[],"config":[]}
                """)));
  }

  private static ApimResponseEnvelope<JsonNode> envelope(String errMessage, List<JsonNode> data) {
    return new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data));
  }

  private static JsonNode json(String value) {
    try {
      return JSON.readTree(value);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
