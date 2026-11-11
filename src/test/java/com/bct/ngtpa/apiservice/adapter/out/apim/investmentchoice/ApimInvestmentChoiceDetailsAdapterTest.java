package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.InvestMandate;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.PendingReq;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.PendingReqDtl;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.SwitchFund;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.SwitchSrc;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class ApimInvestmentChoiceDetailsAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPGetCIMDtl";
  private static final String POLICY_NO = "policy-secret-991";
  private static final String CERT_NO = "cert-secret-422";
  private static final String ACCOUNT_REF = "account-ref-secret";
  private static final String FUND_CODE = "ZZF1";
  private static final String PERCENTAGE = "60";
  private static final String REQUEST_ID = "request-id-123";
  private static final String LIVE_RESPONSE_WITHOUT_PENDING =
      """
      {
        "response": {
          "err-message": "",
          "data": [{
            "interval-pcnt": 5,
            "SwitchSrc": [{
              "switch-src": "EE",
              "seq": 10,
              "avail-sw": true
            }],
            "SwitchFund": [
              {
                "fund-code": "ZZF1",
                "fund-name": "Fund One",
                "avail-sw": true
              },
              {
                "fund-code": "ZZF2",
                "fund-name": "Fund Two",
                "avail-sw": true
              }
            ],
            "InvestMandate": [
              {
                "effect-date": "05/11/2026",
                "switch-src": "EE",
                "fund-code": "ZZF1",
                "pcnt": "25"
              },
              {
                "effect-date": "05/11/2026",
                "switch-src": "EE",
                "fund-code": "ZZF2",
                "pcnt": "25"
              },
              {
                "effect-date": "05/11/2026",
                "switch-src": "EE",
                "fund-code": "ZZF1",
                "pcnt": "25"
              },
              {
                "effect-date": "05/11/2026",
                "switch-src": "EE",
                "fund-code": "ZZF2",
                "pcnt": "25"
              }
            ]
          }]
        }
      }
      """;

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

    logger = (Logger) LoggerFactory.getLogger(ApimInvestmentChoiceDetailsAdapter.class);
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
  void mapsEveryFieldAndBuildsTheExactAccountEnvironmentRequest() throws Exception {
    respond(envelope("", List.of(validItem())));

    StepVerifier.create(adapter().load(command()).contextWrite(requestContext()))
        .assertNext(
            result ->
                assertThat(result)
                    .isEqualTo(
                        new InvestmentChoiceDetails.Normal(
                            5,
                            List.of(new InvestmentChoiceSource("EE", 10, true)),
                            List.of(
                                new InvestmentChoiceFund(
                                    FUND_CODE, "Fund One", "Fund Chinese", "3", false),
                                new InvestmentChoiceFund(
                                    "ZZF2", "Fund Two", "Fund Two", null, true)),
                            List.of(
                                new InvestmentChoiceMandate(
                                    LocalDate.of(2026, 11, 5), "EE", FUND_CODE, PERCENTAGE)),
                            List.of(
                                new InvestmentChoicePending(
                                    "REF-1",
                                    LocalDate.of(2026, 11, 5),
                                    LocalTime.of(17, 4, 14),
                                    List.of(
                                        new InvestmentChoicePendingDetail("EE", FUND_CODE, 40)))))))
        .verifyComplete();

    ArgumentCaptor<ApimExchangeRequest> captured =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    verify(executor, times(1)).postEnvelope(captured.capture());
    var request = captured.getValue();
    assertThat(request.path()).isEqualTo(PATH);
    assertThat(request.request())
        .isEqualTo(new TrpGetCIMDtlApimRequest(POLICY_NO, CERT_NO, "05/11/2026"));
    assertThat(request.requestType()).isEqualTo(TrpGetCIMDtlApimRequest.class);
    assertThat(request.responseDataType()).isEqualTo(com.fasterxml.jackson.databind.JsonNode.class);
    assertThat(request.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(request.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_INPUT);
    assertThat(request.credentialContext().accountEnv()).isEqualTo("JP");
    assertThat(request.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(
            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request.request()))
        .isEqualTo(
            "{\"policy-no\":\"policy-secret-991\",\"cert-no\":\"cert-secret-422\","
                + "\"ref-date\":\"05/11/2026\"}");
    verifyNoInteractions(failureEventPort);
  }

  @Test
  void carriesSourceDescriptionAndEachFundSequenceIntoApplicationDetails() {
    var valid = validItem();
    var item =
        new TrpGetCIMDtlApimDataItem(
            5,
            List.of(
                new SwitchSrc("BO", "Employer and Member Voluntary Contributions", null, 10, true)),
            List.of(
                new SwitchFund(
                    "FAEU",
                    "Fidelity Advantage Portfolio Fund - Asia Pacific Equity Fund",
                    null,
                    60,
                    "",
                    true),
                new SwitchFund("FBAL", "Fidelity Balanced Fund", null, 20, "1", true)),
            valid.investMandate(),
            List.of(),
            List.of());
    respond(envelope("", List.of(item)));

    StepVerifier.create(adapter().load(command()))
        .assertNext(
            details -> {
              var normal = (InvestmentChoiceDetails.Normal) details;
              assertThat(normal.sources().getFirst().description())
                  .isEqualTo("Employer and Member Voluntary Contributions");
              assertThat(normal.funds())
                  .extracting(InvestmentChoiceFund::sequence)
                  .containsExactly(60, 20);
            })
        .verifyComplete();

    verifyNoInteractions(failureEventPort);
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"err.account.invalid", "err.account.no.invest.mandate", "err.future.unknown"})
  void downstreamErrorCodesPublishOneContractEventAndReturnTheRetrievalFailure(String code) {
    respond(envelope(code, null));

    expectRetrievalFailure(adapter().load(command()).contextWrite(requestContext()));

    assertSingleEvent(FailureOrigin.DOWNSTREAM_CONTRACT, code);
    assertThat(events.getFirst().requestId()).isEqualTo(REQUEST_ID);
  }

  @Test
  void eligibilityCodeSlotIsEmptyForKnownAndUnknownErrorMessages() {
    assertThat(InvestmentChoiceEligibilityCodes.featureCode("err.account.invalid")).isEmpty();
    assertThat(InvestmentChoiceEligibilityCodes.featureCode("err.account.no.invest.mandate"))
        .isEmpty();
    assertThat(InvestmentChoiceEligibilityCodes.featureCode("err.future.unknown")).isEmpty();
  }

  @Test
  void missingRequestIdFollowsRegistrationAndPublishesNullRequestId() {
    respond(envelope("err.account.invalid", null));

    expectRetrievalFailure(adapter().load(command()));

    assertSingleEvent(FailureOrigin.DOWNSTREAM_CONTRACT, "err.account.invalid");
    assertThat(events.getFirst().requestId()).isNull();
  }

  @Test
  void emptyMultipleAndNullItemsAreMalformedResponses() {
    assertMalformed(envelope("", List.of()));
    assertMalformed(envelope("", List.of(validItem(), validItem())));
    assertMalformed(envelope("", Arrays.asList((TrpGetCIMDtlApimDataItem) null)));
  }

  @Test
  void missingRequiredTopLevelFieldsAndListsAreMalformedResponses() {
    var valid = validItem();
    assertMalformed(envelope("", List.of(item(null, valid))));
    assertMalformed(envelope("", List.of(item(0, valid))));
    assertMalformed(envelope("", List.of(item(101, valid))));
    assertMalformed(envelope("", List.of(item(valid, null, null, null, null, null))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    null,
                    valid.switchFund(),
                    valid.investMandate(),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    null,
                    valid.investMandate(),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "", List.of(item(valid, valid.switchSrc(), valid.switchFund(), null, null, null))));
  }

  @Test
  void omittedAndEmptyPendingListsAreMappedToAnEmptyPendingList() {
    var valid = validItem();
    List<TrpGetCIMDtlApimDataItem> cases =
        List.of(
            item(valid, valid.switchSrc(), valid.switchFund(), valid.investMandate(), null, null),
            item(
                valid,
                valid.switchSrc(),
                valid.switchFund(),
                valid.investMandate(),
                null,
                List.of()),
            item(
                valid,
                valid.switchSrc(),
                valid.switchFund(),
                valid.investMandate(),
                List.of(),
                null),
            item(
                valid,
                valid.switchSrc(),
                valid.switchFund(),
                valid.investMandate(),
                List.of(),
                List.of()));

    for (TrpGetCIMDtlApimDataItem item : cases) {
      respond(envelope("", List.of(item)));

      StepVerifier.create(adapter().load(command()))
          .expectNextMatches(
              details -> ((InvestmentChoiceDetails.Normal) details).pending().isEmpty())
          .verifyComplete();
    }

    assertThat(events).isEmpty();
    verifyNoInteractions(failureEventPort);
  }

  @Test
  void mapsLiveResponseWithoutPendingPropertiesToEmptyPendingList() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JavaType envelopeType =
        mapper
            .getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, TrpGetCIMDtlApimDataItem.class);
    ApimResponseEnvelope<TrpGetCIMDtlApimDataItem> response =
        mapper.readValue(LIVE_RESPONSE_WITHOUT_PENDING, envelopeType);
    respond(response);

    StepVerifier.create(adapter().load(command()))
        .assertNext(
            details -> {
              var normal = (InvestmentChoiceDetails.Normal) details;
              assertThat(normal.intervalPcnt()).isEqualTo(5);
              assertThat(normal.sources())
                  .containsExactly(new InvestmentChoiceSource("EE", 10, true));
              assertThat(normal.funds())
                  .hasSize(2)
                  .allSatisfy(fund -> assertThat(fund.avail()).isTrue());
              assertThat(details.mandates()).hasSize(4);
              assertThat(normal.pending()).isEmpty();
            })
        .verifyComplete();

    assertThat(events).isEmpty();
    verifyNoInteractions(failureEventPort);
  }

  @Test
  void absentScenarioUsesTheTemporaryNormalFallback() throws Exception {
    respondRaw(liveResponseItem());

    StepVerifier.create(adapter().load(command()))
        .assertNext(details -> assertThat(details.scenario().name()).isEqualTo("NORMAL"))
        .verifyComplete();

    verifyNoInteractions(failureEventPort);
  }

  @Test
  void restrictedScenarioIsResolvedBeforeIrrelevantNormalFieldsAreDecoded() throws Exception {
    for (String scenario : List.of("FUNCTION_UNAVAILABLE", "MEMBER_INELIGIBLE")) {
      ObjectNode item = (ObjectNode) liveResponseItem();
      item.put("scenario", scenario);
      item.put("interval-pcnt", "not-an-integer");
      item.put("PendingReq", "not-an-array");
      item.put("PendingReqDtl", "not-an-array");
      respondRaw(item);

      StepVerifier.create(adapter().load(command()))
          .assertNext(details -> assertThat(details.scenario().name()).isEqualTo(scenario))
          .verifyComplete();
    }

    verifyNoInteractions(failureEventPort);
  }

  @Test
  void presentUnknownScenarioIsOneMalformedDownstreamResponse() throws Exception {
    ObjectNode item = (ObjectNode) liveResponseItem();
    item.put("scenario", "FUTURE_SCENARIO");
    assertMalformedRaw(item);
  }

  @Test
  void presentNullScenarioIsMalformed() throws Exception {
    ObjectNode item = (ObjectNode) liveResponseItem();
    item.putNull("scenario");

    assertMalformedRaw(item);
  }

  @Test
  void presentNonStringScenarioIsMalformed() throws Exception {
    ObjectNode item = (ObjectNode) liveResponseItem();
    item.put("scenario", 1);

    assertMalformedRaw(item);
  }

  @Test
  void presentBlankScenarioIsMalformed() throws Exception {
    ObjectNode item = (ObjectNode) liveResponseItem();
    item.put("scenario", " ");

    assertMalformedRaw(item);
  }

  @Test
  void restrictedScenarioStillRequiresFundsAndHistory() throws Exception {
    ObjectNode item = restrictedResponseItem("MEMBER_INELIGIBLE");
    item.remove("SwitchFund");
    assertMalformedRaw(item);

    item = restrictedResponseItem("MEMBER_INELIGIBLE");
    item.remove("InvestMandate");
    assertMalformedRaw(item);
  }

  @Test
  void rejectsMalformedSwitchSourcesAndDuplicateCodes() {
    var valid = validItem();
    assertMalformed(envelope("", List.of(item(valid, Arrays.asList((SwitchSrc) null), valid))));
    assertMalformed(
        envelope(
            "", List.of(item(valid, List.of(new SwitchSrc(" ", null, null, 1, true)), valid))));
    assertMalformed(
        envelope(
            "", List.of(item(valid, List.of(new SwitchSrc("EE", null, null, null, true)), valid))));
    assertMalformed(
        envelope(
            "", List.of(item(valid, List.of(new SwitchSrc("EE", null, null, 1, null)), valid))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    List.of(valid.switchSrc().getFirst(), valid.switchSrc().getFirst()),
                    valid))));
  }

  @Test
  void rejectsMalformedFundsAndDuplicateCodes() {
    var valid = validItem();
    assertMalformed(
        envelope(
            "", List.of(item(valid, valid.switchSrc(), Arrays.asList((SwitchFund) null), valid))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    List.of(new SwitchFund(" ", "Name", null, null, null, true)),
                    valid))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    List.of(new SwitchFund("F", " ", null, null, null, true)),
                    valid))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    List.of(new SwitchFund(FUND_CODE, "Name", null, null, null, null)),
                    valid))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    List.of(valid.switchFund().getFirst(), valid.switchFund().getFirst()),
                    valid))));
  }

  @Test
  void rejectsMalformedMandates() {
    var valid = validItem();
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    Arrays.asList((InvestMandate) null),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    List.of(new InvestMandate(null, "EE", FUND_CODE, "1")),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    List.of(new InvestMandate("31/02/2026", "EE", FUND_CODE, "1")),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    List.of(new InvestMandate("05/11/2026", " ", FUND_CODE, "1")),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    List.of(new InvestMandate("05/11/2026", "EE", " ", "1")),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    List.of(new InvestMandate("05/11/2026", "EE", FUND_CODE, " ")),
                    valid.pendingReq(),
                    valid.pendingReqDtl()))));
  }

  @Test
  void rejectsMalformedPendingRequestsAndTheirIntegrityRules() {
    var valid = validItem();
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    Arrays.asList((PendingReq) null),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    List.of(new PendingReq(" ", "05/11/2026", "17:04:14")),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    List.of(new PendingReq("REF-1", null, "17:04:14")),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    List.of(new PendingReq("REF-1", "2026-11-05", "17:04:14")),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    List.of(new PendingReq("REF-1", "05/11/2026", null)),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    List.of(new PendingReq("REF-1", "05/11/2026", "24:00:00")),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    List.of(valid.pendingReq().getFirst(), valid.pendingReq().getFirst()),
                    valid.pendingReqDtl()))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.switchSrc(),
                    valid.switchFund(),
                    valid.investMandate(),
                    valid.pendingReq(),
                    List.of()))));
  }

  @Test
  void rejectsMalformedPendingDetailsAndMissingReferences() {
    var valid = validItem();
    assertMalformed(
        envelope(
            "", List.of(item(valid, valid.pendingReq(), Arrays.asList((PendingReqDtl) null)))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.pendingReq(),
                    List.of(new PendingReqDtl(" ", "EE", FUND_CODE, 40))))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.pendingReq(),
                    List.of(new PendingReqDtl("REF-2", "EE", FUND_CODE, 40))))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.pendingReq(),
                    List.of(new PendingReqDtl("REF-1", " ", FUND_CODE, 40))))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.pendingReq(),
                    List.of(new PendingReqDtl("REF-1", "EE", " ", 40))))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.pendingReq(),
                    List.of(new PendingReqDtl("REF-1", "EE", FUND_CODE, null))))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.pendingReq(),
                    List.of(new PendingReqDtl("REF-1", "EE", FUND_CODE, 0))))));
    assertMalformed(
        envelope(
            "",
            List.of(
                item(
                    valid,
                    valid.pendingReq(),
                    List.of(new PendingReqDtl("REF-1", "EE", FUND_CODE, 101))))));
  }

  @Test
  void historyAndPendingFundCodesAbsentFromFundListRemainSuccessful() {
    var valid = validItem();
    var item =
        item(
            valid,
            valid.switchSrc(),
            valid.switchFund(),
            List.of(new InvestMandate("05/11/2026", "EE", "MISS1", "60")),
            valid.pendingReq(),
            List.of(new PendingReqDtl("REF-1", "EE", "MISS2", 40)));
    respond(envelope("", List.of(item)));

    StepVerifier.create(adapter().load(command())).expectNextCount(1).verifyComplete();

    assertThat(events).isEmpty();
    verifyNoInteractions(failureEventPort);
  }

  @ParameterizedTest
  @MethodSource("executorFailures")
  void classifiesEachExecutorFailureAndPublishesOneMatchingEvent(
      Throwable failure, FailureOrigin origin) {
    when(executor.<TrpGetCIMDtlApimRequest, TrpGetCIMDtlApimDataItem>postEnvelope(any()))
        .thenReturn(Mono.error(failure));

    expectRetrievalFailure(adapter().load(command()).contextWrite(requestContext()));

    assertSingleEvent(origin, null);
    assertThat(events.getFirst().requestId()).isEqualTo(REQUEST_ID);
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> executorFailures() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "bad"),
            FailureOrigin.DOWNSTREAM_CONTRACT),
        org.junit.jupiter.params.provider.Arguments.of(
            new ApimResponseMappingException(new IllegalArgumentException("mapping")),
            FailureOrigin.DOWNSTREAM_INVALID_RESPONSE),
        org.junit.jupiter.params.provider.Arguments.of(
            new ApimException(HttpStatus.SERVICE_UNAVAILABLE, "err.apim.unavailable", "down"),
            FailureOrigin.DOWNSTREAM_TRANSPORT),
        org.junit.jupiter.params.provider.Arguments.of(
            new IllegalStateException("local"), FailureOrigin.LOCAL_SYSTEM));
  }

  @Test
  void capturedEventsAndAdapterLogsDoNotContainSensitiveRequestOrResponseValues() {
    respond(envelope("err.account.invalid", null));

    expectRetrievalFailure(adapter().load(command()).contextWrite(requestContext()));

    assertSingleEvent(FailureOrigin.DOWNSTREAM_CONTRACT, "err.account.invalid");
    String event = events.getFirst().toString();
    String logs =
        appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
    assertThat(event).doesNotContain(POLICY_NO, CERT_NO, ACCOUNT_REF, FUND_CODE, PERCENTAGE);
    assertThat(logs).doesNotContain(POLICY_NO, CERT_NO, ACCOUNT_REF, FUND_CODE, PERCENTAGE);
  }

  private void assertMalformed(ApimResponseEnvelope<TrpGetCIMDtlApimDataItem> response) {
    clearInvocations(executor, failureEventPort);
    events.clear();
    respond(response);

    expectRetrievalFailure(adapter().load(command()));

    assertSingleEvent(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE, null);
  }

  private void assertMalformedRaw(JsonNode item) {
    clearInvocations(executor, failureEventPort);
    events.clear();
    respondRaw(item);

    expectRetrievalFailure(adapter().load(command()));

    assertSingleEvent(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE, null);
  }

  private void assertSingleEvent(FailureOrigin origin, String internalCode) {
    verify(failureEventPort, times(1)).publish(any(FailureEvent.class));
    assertThat(events).hasSize(1);
    var event = events.getFirst();
    assertThat(event.event()).isEqualTo("investment-choice.retrieval.downstream_failure");
    assertThat(event.capability()).isEqualTo("INVESTMENT_CHOICE");
    assertThat(event.operation()).isEqualTo("TRPGetCIMDtl");
    assertThat(event.origin()).isEqualTo(origin);
    assertThat(event.internalCode()).isEqualTo(internalCode);
    assertThat(event.publicCode()).isEqualTo("err.investment-choice.retrieval.unavailable");
    assertThat(event.httpStatus()).isEqualTo(503);
    assertThat(event.retryable()).isTrue();
    assertThat(event.safeDimensions()).containsOnlyKeys("reason");
  }

  private void respond(ApimResponseEnvelope<TrpGetCIMDtlApimDataItem> response) {
    var rawBody = new ApimResponseBody<JsonNode>();
    rawBody.setErrMessage(response.getResponse().getErrMessage());
    List<TrpGetCIMDtlApimDataItem> data = response.getResponse().getData();
    rawBody.setData(
        data == null
            ? null
            : data.stream()
                .map(item -> item == null ? null : (JsonNode) new ObjectMapper().valueToTree(item))
                .toList());
    var rawEnvelope = new ApimResponseEnvelope<JsonNode>();
    rawEnvelope.setResponse(rawBody);
    doReturn(Mono.just(rawEnvelope)).when(executor).postEnvelope(any());
  }

  private void respondRaw(JsonNode item) {
    var response = new ApimResponseBody<JsonNode>();
    response.setErrMessage("");
    response.setData(List.of(item));
    var envelope = new ApimResponseEnvelope<JsonNode>();
    envelope.setResponse(response);
    doReturn(Mono.just(envelope)).when(executor).postEnvelope(any());
  }

  private static JsonNode liveResponseItem() throws Exception {
    return new ObjectMapper()
        .readTree(LIVE_RESPONSE_WITHOUT_PENDING)
        .path("response")
        .path("data")
        .get(0);
  }

  private static ObjectNode restrictedResponseItem(String scenario) throws Exception {
    ObjectNode item = new ObjectMapper().createObjectNode();
    item.put("scenario", scenario);
    item.set(
        "SwitchFund",
        new ObjectMapper()
            .readTree(
                """
                [{"fund-code":"FUND_A","fund-name":"Fund A","avail-sw":true}]
                """));
    item.set(
        "InvestMandate",
        new ObjectMapper()
            .readTree(
                """
                [{"effect-date":"05/11/2026","switch-src":"EE","fund-code":"FUND_A","pcnt":"100"}]
                """));
    return item;
  }

  private ApimInvestmentChoiceDetailsAdapter adapter() {
    return new ApimInvestmentChoiceDetailsAdapter(executor, failureEventPort, PATH);
  }

  private static InvestmentChoiceDetailsCommand command() {
    return new InvestmentChoiceDetailsCommand(
        ActorUserRole.ROLE_INPUT, "JP", POLICY_NO, CERT_NO, LocalDate.of(2026, 11, 5));
  }

  private static Context requestContext() {
    return Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, REQUEST_ID);
  }

  private static void expectRetrievalFailure(Mono<InvestmentChoiceDetails> result) {
    StepVerifier.create(result)
        .expectError(InvestmentChoiceDetailsRetrievalException.class)
        .verify();
  }

  private static TrpGetCIMDtlApimDataItem validItem() {
    return new TrpGetCIMDtlApimDataItem(
        5,
        List.of(new SwitchSrc("EE", null, null, 10, true)),
        List.of(
            new SwitchFund(FUND_CODE, "Fund One", "Fund Chinese", null, "3", false),
            new SwitchFund("ZZF2", "Fund Two", "", null, null, true)),
        List.of(new InvestMandate("05/11/2026", "EE", FUND_CODE, PERCENTAGE)),
        List.of(new PendingReq("REF-1", "05/11/2026", "17:04:14")),
        List.of(new PendingReqDtl("REF-1", "EE", FUND_CODE, 40)));
  }

  private static ApimResponseEnvelope<TrpGetCIMDtlApimDataItem> envelope(
      String errMessage, List<TrpGetCIMDtlApimDataItem> data) {
    var response = new ApimResponseBody<TrpGetCIMDtlApimDataItem>();
    response.setErrMessage(errMessage);
    response.setData(data);
    var envelope = new ApimResponseEnvelope<TrpGetCIMDtlApimDataItem>();
    envelope.setResponse(response);
    return envelope;
  }

  private static TrpGetCIMDtlApimDataItem item(
      Integer interval,
      List<SwitchSrc> sources,
      List<SwitchFund> funds,
      List<InvestMandate> mandates,
      List<PendingReq> pending,
      List<PendingReqDtl> details) {
    return new TrpGetCIMDtlApimDataItem(interval, sources, funds, mandates, pending, details);
  }

  private static TrpGetCIMDtlApimDataItem item(Integer interval, TrpGetCIMDtlApimDataItem valid) {
    return item(
        interval,
        valid.switchSrc(),
        valid.switchFund(),
        valid.investMandate(),
        valid.pendingReq(),
        valid.pendingReqDtl());
  }

  private static TrpGetCIMDtlApimDataItem item(
      TrpGetCIMDtlApimDataItem valid,
      List<SwitchSrc> sources,
      List<SwitchFund> funds,
      List<InvestMandate> mandates,
      List<PendingReq> pending,
      List<PendingReqDtl> details) {
    return item(valid.intervalPcnt(), sources, funds, mandates, pending, details);
  }

  private static TrpGetCIMDtlApimDataItem item(
      TrpGetCIMDtlApimDataItem valid, List<SwitchSrc> sources, TrpGetCIMDtlApimDataItem ignored) {
    return item(
        valid,
        sources,
        valid.switchFund(),
        valid.investMandate(),
        valid.pendingReq(),
        valid.pendingReqDtl());
  }

  private static TrpGetCIMDtlApimDataItem item(
      TrpGetCIMDtlApimDataItem valid,
      List<SwitchSrc> sources,
      List<SwitchFund> funds,
      TrpGetCIMDtlApimDataItem ignored) {
    return item(
        valid, sources, funds, valid.investMandate(), valid.pendingReq(), valid.pendingReqDtl());
  }

  private static TrpGetCIMDtlApimDataItem item(
      TrpGetCIMDtlApimDataItem valid, List<PendingReq> pending, List<PendingReqDtl> details) {
    return item(
        valid, valid.switchSrc(), valid.switchFund(), valid.investMandate(), pending, details);
  }
}
