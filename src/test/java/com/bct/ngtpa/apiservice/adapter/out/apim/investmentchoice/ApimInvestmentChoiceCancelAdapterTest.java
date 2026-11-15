package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureCategory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseMappingException;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCnlCIMReqApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCnlCIMReqApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceCancellation;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelNotCancellableException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceCancelUnavailableException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class ApimInvestmentChoiceCancelAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPCnclCIMReq";
  private static final String USER_ID = "USER1234";
  private static final String POLICY_NO = "POLICY1234";
  private static final String CERT_NO = "CERT1234";
  private static final String ACCOUNT_ENV = "DB";
  private static final String REFERENCE_NO = "260001451";
  private static final String RESPONSE_REFERENCE_NO = "260001452";
  private static final String REQUEST_ID = "request-id-123";
  private static final String CAPABILITY = "INVESTMENT_CHOICE";
  private static final String OPERATION = "TRPCnclCIMReq";
  private static final String EVENT = "investment-choice.cancel.downstream_failure";
  private static final String NOT_CANCELLABLE_CODE = "err.request.cannot.be.cancelled";
  private static final String INVALID_REQUEST_CODE = "err.request.invalid";
  private static final String NOT_CANCELLABLE_PUBLIC_CODE =
      "err.investment-choice.cancel.not-cancellable";
  private static final String UNAVAILABLE_PUBLIC_CODE = "err.investment-choice.cancel.unavailable";
  private static final String DOWNSTREAM_CONTRACT_REASON = FailureOrigin.DOWNSTREAM_CONTRACT.name();

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ApimExchangeExecutor executor;
  private FailureEventPort failureEventPort;
  private List<FailureEvent> events;
  private Logger logger;
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
    logger = (Logger) org.slf4j.LoggerFactory.getLogger(ApimInvestmentChoiceCancelAdapter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void buildsExactStringRequestAndReturnsRequestedReferenceWithoutInspectingSuccess()
      throws Exception {
    respond(envelope("", List.of(new TrpCnlCIMReqApimDataItem(false, RESPONSE_REFERENCE_NO))));

    StepVerifier.create(adapter().cancel(command()).contextWrite(requestContext()))
        .expectNext(new InvestmentChoiceCancellation(REFERENCE_NO))
        .verifyComplete();

    ArgumentCaptor<ApimExchangeRequest> captured =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor, times(1)).postEnvelope(captured.capture());
    var exchange = captured.getValue();
    assertThat(exchange.path()).isEqualTo(PATH);
    assertThat(exchange.requestType()).isEqualTo(TrpCnlCIMReqApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(TrpCnlCIMReqApimDataItem.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_INPUT);
    assertThat(exchange.credentialContext().accountEnv()).isEqualTo(ACCOUNT_ENV);
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.NON_RETRYABLE);
    assertThat(exchange.resubmissionPolicy()).isEqualTo(ApimResubmissionPolicy.NEVER);
    assertThat(exchange.incidentOwnership()).isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(exchange.request())))
        .isEqualTo(
            objectMapper.readTree(
                """
                {
                  "login-user-id": "%s",
                  "login-user-role": "role_input",
                  "policy-no": "%s",
                  "cert-no": "%s",
                  "ref-no": "%s"
                }
                """
                    .formatted(USER_ID, POLICY_NO, CERT_NO, REFERENCE_NO)));
    assertThat(events).isEmpty();
  }

  @Test
  void absentSuccessFlagAndResponseReferenceAreIgnored() throws Exception {
    respond(parseEnvelope(cancelResponseWithoutSuccess().formatted(RESPONSE_REFERENCE_NO)));

    StepVerifier.create(adapter().cancel(command()))
        .expectNext(new InvestmentChoiceCancellation(REFERENCE_NO))
        .verifyComplete();
    assertThat(events).isEmpty();
  }

  @Test
  void cannotBeCancelledPublishesOneBusinessEventAndThrowsNotCancellable() {
    respond(envelope(NOT_CANCELLABLE_CODE, null));

    StepVerifier.create(adapter().cancel(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceCancelNotCancellableException.class)
        .verify();

    assertSingleEvent(
        FailureOrigin.DOWNSTREAM_BUSINESS,
        NOT_CANCELLABLE_CODE,
        NOT_CANCELLABLE_PUBLIC_CODE,
        422,
        REQUEST_ID,
        FailureOrigin.DOWNSTREAM_BUSINESS.name());
  }

  @Test
  void terminalOwnedTransportFailureLeavesOnlyTheFailureEventAtError() {
    try (var capture = TerminalApimFailureLogCapture.start()) {
      var terminalAdapter =
          new ApimInvestmentChoiceCancelAdapter(capture.executor(), capture.failureEvents(), PATH);

      StepVerifier.create(terminalAdapter.cancel(command()).contextWrite(requestContext()))
          .expectError(InvestmentChoiceCancelUnavailableException.class)
          .verify();

      capture.assertSingleTerminalError(
          "investment-choice.cancel.downstream_failure", POLICY_NO, CERT_NO, USER_ID, REFERENCE_NO);
    }
  }

  @ParameterizedTest
  @MethodSource("downstreamContractCodes")
  void otherDownstreamErrorMessagesPublishOneContractEvent(String errorCode, String reason) {
    respond(envelope(errorCode, null));

    StepVerifier.create(adapter().cancel(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    assertSingleEvent(
        FailureOrigin.DOWNSTREAM_CONTRACT,
        errorCode,
        UNAVAILABLE_PUBLIC_CODE,
        503,
        REQUEST_ID,
        reason);
  }

  static Stream<Arguments> downstreamContractCodes() {
    return Stream.of(
        Arguments.of("err.tpa.cust.id.not.registered", "DOWNSTREAM_CONTRACT"),
        Arguments.of(INVALID_REQUEST_CODE, DOWNSTREAM_CONTRACT_REASON),
        Arguments.of("err.role.not.allow.access", DOWNSTREAM_CONTRACT_REASON),
        Arguments.of("err.unknown.cancel.code", DOWNSTREAM_CONTRACT_REASON));
  }

  @ParameterizedTest
  @MethodSource("malformedSuccessResponses")
  void malformedSuccessPublishesOneInvalidResponseEvent(String json) throws Exception {
    respond(parseEnvelope(json));

    StepVerifier.create(adapter().cancel(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    assertSingleEvent(
        FailureOrigin.DOWNSTREAM_INVALID_RESPONSE,
        null,
        UNAVAILABLE_PUBLIC_CODE,
        503,
        REQUEST_ID,
        "MALFORMED_RESPONSE");
  }

  @Test
  void mappingThrowableIsClassifiedAndPublishedOnce() {
    respond(mappingFailureEnvelope());

    StepVerifier.create(adapter().cancel(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    assertSingleEvent(
        FailureOrigin.LOCAL_SYSTEM,
        null,
        UNAVAILABLE_PUBLIC_CODE,
        503,
        REQUEST_ID,
        ApimFailureCategory.LOCAL_SYSTEM.name());
  }

  static Stream<Arguments> malformedSuccessResponses() {
    return Stream.of(
        Arguments.of(emptyDataResponse()),
        Arguments.of(
            """
            {"response":{"err-message":"","data":[{"success":true,"ref-no":"%s"},{"success":true,"ref-no":"%s"}]}}
            """
                .formatted(REFERENCE_NO, RESPONSE_REFERENCE_NO)));
  }

  @ParameterizedTest
  @MethodSource("classifiedFailures")
  void executorFailuresPublishOneClassifiedEvent(
      Throwable failure, FailureOrigin origin, String reason) {
    when(executor.<TrpCnlCIMReqApimRequest, TrpCnlCIMReqApimDataItem>postEnvelope(any()))
        .thenReturn(Mono.error(failure));

    StepVerifier.create(adapter().cancel(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    assertSingleEvent(origin, null, UNAVAILABLE_PUBLIC_CODE, 503, REQUEST_ID, reason);
  }

  static Stream<Arguments> classifiedFailures() {
    return Stream.of(
        Arguments.of(
            new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "bad"),
            FailureOrigin.DOWNSTREAM_CONTRACT,
            ApimFailureCategory.DOWNSTREAM_CONTRACT.name()),
        Arguments.of(
            new ApimResponseMappingException(new IllegalArgumentException("mapping")),
            FailureOrigin.DOWNSTREAM_INVALID_RESPONSE,
            ApimFailureCategory.DOWNSTREAM_INVALID_RESPONSE.name()),
        Arguments.of(
            new ConnectException("transport"),
            FailureOrigin.DOWNSTREAM_TRANSPORT,
            ApimFailureCategory.DOWNSTREAM_TRANSPORT.name()),
        Arguments.of(
            new IllegalStateException("local"),
            FailureOrigin.LOCAL_SYSTEM,
            ApimFailureCategory.LOCAL_SYSTEM.name()));
  }

  @Test
  void publishesOnceWhenRequestIdIsAbsent() {
    respond(envelope(NOT_CANCELLABLE_CODE, null));

    StepVerifier.create(adapter().cancel(command()))
        .expectError(InvestmentChoiceCancelNotCancellableException.class)
        .verify();

    assertSingleEvent(
        FailureOrigin.DOWNSTREAM_BUSINESS,
        NOT_CANCELLABLE_CODE,
        NOT_CANCELLABLE_PUBLIC_CODE,
        422,
        null,
        FailureOrigin.DOWNSTREAM_BUSINESS.name());
  }

  @Test
  void eventsAndCapturedLogsDoNotContainSensitiveValues() {
    respond(envelope(INVALID_REQUEST_CODE, null));

    StepVerifier.create(adapter().cancel(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceCancelUnavailableException.class)
        .verify();

    assertSingleEvent(
        FailureOrigin.DOWNSTREAM_CONTRACT,
        INVALID_REQUEST_CODE,
        UNAVAILABLE_PUBLIC_CODE,
        503,
        REQUEST_ID,
        FailureOrigin.DOWNSTREAM_CONTRACT.name());
    String event = events.getFirst().toString();
    String logs =
        appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
    assertThat(event)
        .doesNotContain(POLICY_NO, CERT_NO, USER_ID, REFERENCE_NO, RESPONSE_REFERENCE_NO);
    assertThat(logs)
        .doesNotContain(POLICY_NO, CERT_NO, USER_ID, REFERENCE_NO, RESPONSE_REFERENCE_NO);
  }

  private void assertSingleEvent(
      FailureOrigin origin,
      String internalCode,
      String publicCode,
      int httpStatus,
      String requestId,
      String reason) {
    verify(failureEventPort, times(1)).publish(any(FailureEvent.class));
    assertThat(events).hasSize(1);
    var event = events.getFirst();
    assertThat(event.event()).isEqualTo(EVENT);
    assertThat(event.requestId()).isEqualTo(requestId);
    assertThat(event.capability()).isEqualTo(CAPABILITY);
    assertThat(event.operation()).isEqualTo(OPERATION);
    assertThat(event.origin()).isEqualTo(origin);
    assertThat(event.internalCode()).isEqualTo(internalCode);
    assertThat(event.publicCode()).isEqualTo(publicCode);
    assertThat(event.httpStatus()).isEqualTo(httpStatus);
    assertThat(event.retryable()).isFalse();
    assertThat(event.safeDimensions()).isEqualTo(Map.of("reason", reason));
  }

  private void respond(ApimResponseEnvelope<TrpCnlCIMReqApimDataItem> response) {
    when(executor.<TrpCnlCIMReqApimRequest, TrpCnlCIMReqApimDataItem>postEnvelope(any()))
        .thenReturn(Mono.just(response));
  }

  private ApimResponseEnvelope<TrpCnlCIMReqApimDataItem> parseEnvelope(String json)
      throws java.io.IOException {
    return objectMapper.readValue(
        json,
        objectMapper
            .getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, TrpCnlCIMReqApimDataItem.class));
  }

  private static String cancelResponseWithoutSuccess() {
    return """
        {"response":{"err-message":"","data":[{"ref-no":"%s"}]}}
        """;
  }

  private static String emptyDataResponse() {
    return """
        {"response":{"err-message":"","data":[]}}
        """;
  }

  private ApimResponseEnvelope<TrpCnlCIMReqApimDataItem> envelope(
      String errMessage, List<TrpCnlCIMReqApimDataItem> data) {
    var body = new ApimResponseBody<TrpCnlCIMReqApimDataItem>();
    body.setErrMessage(errMessage);
    body.setData(data);
    var response = new ApimResponseEnvelope<TrpCnlCIMReqApimDataItem>();
    response.setResponse(body);
    return response;
  }

  private ApimResponseEnvelope<TrpCnlCIMReqApimDataItem> mappingFailureEnvelope() {
    var body =
        new ApimResponseBody<TrpCnlCIMReqApimDataItem>() {
          @Override
          public List<TrpCnlCIMReqApimDataItem> getData() {
            throw new IllegalStateException("mapping failure");
          }
        };
    body.setErrMessage("");
    var response = new ApimResponseEnvelope<TrpCnlCIMReqApimDataItem>();
    response.setResponse(body);
    return response;
  }

  private ApimInvestmentChoiceCancelAdapter adapter() {
    var properties = new InvestmentChoiceCancelApimProperties();
    properties.setPath(PATH);
    return new ApimInvestmentChoiceCancelAdapter(executor, failureEventPort, properties);
  }

  private InvestmentChoiceCancelCommand command() {
    return new InvestmentChoiceCancelCommand(
        USER_ID, ActorUserRole.ROLE_INPUT, ACCOUNT_ENV, POLICY_NO, CERT_NO, REFERENCE_NO);
  }

  private static Context requestContext() {
    return Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, REQUEST_ID);
  }
}
