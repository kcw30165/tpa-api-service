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
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseMappingException;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCIMReqApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpCIMReqApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRow;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmission;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionRejectedException;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceSubmissionUnknownOutcomeException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class ApimInvestmentChoiceInstructionAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPCIMReq";
  private static final String POLICY_NO = "policy-secret-991";
  private static final String CERT_NO = "cert-secret-422";
  private static final String USER_ID = "user-secret-773";
  private static final String REQUEST_ID = "request-id-123";
  private static final String PUBLIC_CODE = "err.investment-choice.submission.unavailable";

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
    logger = (Logger) LoggerFactory.getLogger(ApimInvestmentChoiceInstructionAdapter.class);
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
  void submitsOrderedRowsWithExactFieldsAndMapsResponseWithoutInspectingSuccess() throws Exception {
    respond(
        envelope(
            "",
            List.of(new TrpCIMReqApimDataItem(false, "06/11/2026", "18:23:41", "CIM-REF-100"))));

    StepVerifier.create(adapter().submit(command()).contextWrite(requestContext()))
        .expectNext(
            new InvestmentChoiceSubmission(
                "CIM-REF-100", LocalDate.of(2026, 11, 6), LocalTime.of(18, 23, 41)))
        .verifyComplete();

    ArgumentCaptor<ApimExchangeRequest> captured =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor, times(1)).postEnvelope(captured.capture());
    var request = captured.getValue();
    assertThat(request.path()).isEqualTo(PATH);
    assertThat(request.requestType()).isEqualTo(TrpCIMReqApimRequest.class);
    assertThat(request.responseDataType()).isEqualTo(TrpCIMReqApimDataItem.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_INPUT);
    assertThat(request.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.NON_RETRYABLE);
    assertThat(request.resubmissionPolicy()).isEqualTo(ApimResubmissionPolicy.NEVER);
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(request.request())))
        .isEqualTo(
            objectMapper.readTree(
                "{\"login-user-id\":\""
                    + USER_ID
                    + "\",\"login-user-role\":\"role_input\",\"policy-no\":\""
                    + POLICY_NO
                    + "\",\"cert-no\":\""
                    + CERT_NO
                    + "\",\"CIMInstr\":["
                    + "{\"switch-src\":\"BO\",\"fund-code\":\"FBAL\",\"pcnt\":60},"
                    + "{\"switch-src\":\"EE\",\"fund-code\":\"FHKM\",\"pcnt\":40}]}"));
  }

  @Test
  void absentSuccessFlagDoesNotPreventMapping() throws Exception {
    respond(
        parseEnvelope(
            "{\"response\":{\"err-message\":\"\",\"data\":["
                + "{\"submit-date\":\"06/11/2026\",\"submit-time\":\"18:23:41\","
                + "\"ref-no\":\"CIM-REF-100\"}]}}"));

    StepVerifier.create(adapter().submit(command()))
        .expectNext(
            new InvestmentChoiceSubmission(
                "CIM-REF-100", LocalDate.of(2026, 11, 6), LocalTime.of(18, 23, 41)))
        .verifyComplete();
  }

  @ParameterizedTest
  @CsvSource({
    "err.account.invalid",
    "err.role.not.allow.access",
    "err.investment-choice.future-code"
  })
  void errorMessageIsARejectedSubmissionAndPublishesOneContractEvent(String errorCode)
      throws Exception {
    respond(envelope(errorCode, null));

    StepVerifier.create(adapter().submit(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceSubmissionRejectedException.class)
        .verify();

    assertSingleEvent(FailureOrigin.DOWNSTREAM_CONTRACT, errorCode, REQUEST_ID);
  }

  @ParameterizedTest
  @MethodSource("malformedSuccessResponses")
  void malformedSuccessIsUnknownAndPublishesOneInvalidResponseEvent(String json) throws Exception {
    respond(parseEnvelope(json));

    StepVerifier.create(adapter().submit(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceSubmissionUnknownOutcomeException.class)
        .verify();

    assertSingleEvent(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE, null, REQUEST_ID);
  }

  static Stream<Arguments> malformedSuccessResponses() {
    return Stream.of(
        Arguments.of("{\"response\":{\"err-message\":\"\",\"data\":[]}}"),
        Arguments.of(
            "{\"response\":{\"err-message\":\"\",\"data\":["
                + "{\"submit-date\":\"06/11/2026\",\"submit-time\":\"18:23:41\",\"ref-no\":\"R1\"},"
                + "{\"submit-date\":\"06/11/2026\",\"submit-time\":\"18:23:41\",\"ref-no\":\"R2\"}]}}"),
        Arguments.of(
            "{\"response\":{\"err-message\":\"\",\"data\":["
                + "{\"submit-date\":\"06/11/2026\",\"submit-time\":\"18:23:41\"}]}}"),
        Arguments.of(
            "{\"response\":{\"err-message\":\"\",\"data\":["
                + "{\"submit-date\":\"06/11/2026\",\"submit-time\":\"18:23:41\",\"ref-no\":\" \"}]}}"),
        Arguments.of(
            "{\"response\":{\"err-message\":\"\",\"data\":["
                + "{\"submit-date\":\"31/02/2026\",\"submit-time\":\"18:23:41\",\"ref-no\":\"R1\"}]}}"),
        Arguments.of(
            "{\"response\":{\"err-message\":\"\",\"data\":["
                + "{\"submit-date\":\"06/11/2026\",\"submit-time\":\"25:23:41\",\"ref-no\":\"R1\"}]}}"));
  }

  @ParameterizedTest
  @MethodSource("classifiedFailures")
  void classifiedExecutorFailuresMapToTheirOriginAndOutcome(
      Throwable failure, FailureOrigin origin, Class<? extends Throwable> expectedException) {
    when(executor.<TrpCIMReqApimRequest, TrpCIMReqApimDataItem>postEnvelope(any()))
        .thenReturn(Mono.error(failure));

    StepVerifier.create(adapter().submit(command()).contextWrite(requestContext()))
        .expectError(expectedException)
        .verify();

    assertSingleEvent(origin, null, REQUEST_ID);
  }

  static Stream<Arguments> classifiedFailures() {
    return Stream.of(
        Arguments.of(
            new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "bad"),
            FailureOrigin.DOWNSTREAM_CONTRACT,
            InvestmentChoiceSubmissionRejectedException.class),
        Arguments.of(
            new ApimResponseMappingException(new IllegalArgumentException("mapping")),
            FailureOrigin.DOWNSTREAM_INVALID_RESPONSE,
            InvestmentChoiceSubmissionUnknownOutcomeException.class),
        Arguments.of(
            new ConnectException("transport"),
            FailureOrigin.DOWNSTREAM_TRANSPORT,
            InvestmentChoiceSubmissionUnknownOutcomeException.class),
        Arguments.of(
            new IllegalStateException("local"),
            FailureOrigin.LOCAL_SYSTEM,
            InvestmentChoiceSubmissionUnknownOutcomeException.class));
  }

  @Test
  void publishesOnceWhenRequestIdIsAbsent() throws Exception {
    respond(envelope("err.account.invalid", null));

    StepVerifier.create(adapter().submit(command()))
        .expectError(InvestmentChoiceSubmissionRejectedException.class)
        .verify();

    assertSingleEvent(FailureOrigin.DOWNSTREAM_CONTRACT, "err.account.invalid", null);
  }

  @Test
  void eventsAndCapturedLogsDoNotContainSensitiveInstructionValues() throws Exception {
    respond(envelope("err.account.invalid", null));

    StepVerifier.create(adapter().submit(command()).contextWrite(requestContext()))
        .expectError(InvestmentChoiceSubmissionRejectedException.class)
        .verify();

    assertSingleEvent(FailureOrigin.DOWNSTREAM_CONTRACT, "err.account.invalid", REQUEST_ID);
    String event = events.getFirst().toString();
    String logs =
        appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
    assertThat(event)
        .doesNotContain(POLICY_NO, CERT_NO, USER_ID, "FBAL", "FHKM", "60", "40", "BO", "EE");
    assertThat(logs)
        .doesNotContain(POLICY_NO, CERT_NO, USER_ID, "FBAL", "FHKM", "60", "40", "BO", "EE");
  }

  private void assertSingleEvent(FailureOrigin origin, String internalCode, String requestId) {
    verify(failureEventPort, times(1)).publish(any(FailureEvent.class));
    assertThat(events).hasSize(1);
    var event = events.getFirst();
    assertThat(event.event()).isEqualTo("investment-choice.submission.downstream_failure");
    assertThat(event.requestId()).isEqualTo(requestId);
    assertThat(event.capability()).isEqualTo("INVESTMENT_CHOICE");
    assertThat(event.operation()).isEqualTo("TRPCIMReq");
    assertThat(event.origin()).isEqualTo(origin);
    assertThat(event.internalCode()).isEqualTo(internalCode);
    assertThat(event.publicCode()).isEqualTo(PUBLIC_CODE);
    assertThat(event.httpStatus()).isEqualTo(503);
    assertThat(event.retryable()).isFalse();
    assertThat(event.safeDimensions()).isEqualTo(Map.of("reason", origin.name()));
  }

  private void respond(ApimResponseEnvelope<TrpCIMReqApimDataItem> response) {
    when(executor.<TrpCIMReqApimRequest, TrpCIMReqApimDataItem>postEnvelope(any()))
        .thenReturn(Mono.just(response));
  }

  private ApimResponseEnvelope<TrpCIMReqApimDataItem> parseEnvelope(String json) throws Exception {
    return objectMapper.readValue(
        json,
        objectMapper
            .getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, TrpCIMReqApimDataItem.class));
  }

  private ApimResponseEnvelope<TrpCIMReqApimDataItem> envelope(
      String errMessage, List<TrpCIMReqApimDataItem> data) {
    var body = new ApimResponseBody<TrpCIMReqApimDataItem>();
    body.setErrMessage(errMessage);
    body.setData(data);
    var response = new ApimResponseEnvelope<TrpCIMReqApimDataItem>();
    response.setResponse(body);
    return response;
  }

  private ApimInvestmentChoiceInstructionAdapter adapter() {
    var properties = new InvestmentChoiceInstructionApimProperties();
    properties.setPath(PATH);
    return new ApimInvestmentChoiceInstructionAdapter(executor, failureEventPort, properties);
  }

  private InvestmentChoiceInstructionCommand command() {
    return new InvestmentChoiceInstructionCommand(
        USER_ID,
        ActorUserRole.ROLE_INPUT,
        "JP",
        POLICY_NO,
        CERT_NO,
        List.of(
            new InvestmentChoiceInstructionRow("BO", "FBAL", 60),
            new InvestmentChoiceInstructionRow("EE", "FHKM", 40)));
  }

  private static Context requestContext() {
    return Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, REQUEST_ID);
  }
}
