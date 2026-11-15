package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureCategory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimFailureClassifier;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.InvestMandate;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.PendingReq;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.PendingReqDtl;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.SwitchFund;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem.SwitchSrc;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetails;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceDetailsCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceFund;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceMandate;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePending;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoicePendingDetail;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceScenario;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSource;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.InvestmentChoiceDetailsRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.InvestmentChoiceDetailsPort;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class ApimInvestmentChoiceDetailsAdapter implements InvestmentChoiceDetailsPort {

  private static final String OPERATION = "TRPGetCIMDtl";
  private static final String CAPABILITY = "INVESTMENT_CHOICE";
  private static final String EVENT = "investment-choice.retrieval.downstream_failure";
  private static final String PUBLIC_CODE = "err.investment-choice.retrieval.unavailable";

  private final ApimExchangeExecutor executor;
  private final FailureEventPort failureEventPort;
  private final String path;
  private final TrpGetCIMDtlApimScenarioMapper scenarioMapper =
      new TrpGetCIMDtlApimScenarioMapper();

  @Autowired
  public ApimInvestmentChoiceDetailsAdapter(
      ApimExchangeExecutor executor,
      FailureEventPort failureEventPort,
      InvestmentChoiceDetailsApimProperties properties) {
    this(executor, failureEventPort, properties.getPath());
  }

  ApimInvestmentChoiceDetailsAdapter(
      ApimExchangeExecutor executor, FailureEventPort failureEventPort, String path) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.failureEventPort =
        Objects.requireNonNull(failureEventPort, "failureEventPort must not be null");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    this.path = path;
  }

  @Override
  public Mono<InvestmentChoiceDetails> load(InvestmentChoiceDetailsCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () -> {
              var request =
                  new TrpGetCIMDtlApimRequest(
                      command.policyNo(),
                      command.certNo(),
                      TrpGetCIMDtlApimMapper.formatDate(command.refDate()));
              var exchange =
                  new ApimExchangeRequest<>(
                      path,
                      request,
                      TrpGetCIMDtlApimRequest.class,
                      JsonNode.class,
                      ApimDatabaseScope.ACCOUNT_ENV,
                      new ApimCredentialResolutionContext(
                          command.actorUserRole(), command.accountEnv()),
                      ApimRetryEligibility.SAFE_BUSINESS_CALL,
                      ApimResubmissionPolicy.ALLOW_RECOVERY,
                      ApimIncidentOwnership.TERMINAL_CALLER);
              return executor
                  .<TrpGetCIMDtlApimRequest, JsonNode>postEnvelope(exchange)
                  .map(this::toDetails);
            })
        .doOnEach(this::publishFailure)
        .onErrorMap(
            failure ->
                failure instanceof InvestmentChoiceDetailsRetrievalException
                    ? failure
                    : InvestmentChoiceDetailsRetrievalException.systemFailure());
  }

  private InvestmentChoiceDetails toDetails(ApimResponseEnvelope<JsonNode> envelope) {
    if (envelope == null || envelope.getResponse() == null) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    var response = envelope.getResponse();
    String errMessage = response.getErrMessage();
    if (hasText(errMessage)) {
      String reason =
          InvestmentChoiceEligibilityCodes.featureCode(errMessage).orElse("UNMAPPED_ERROR_CODE");
      throw new DownstreamErrorMessageException(errMessage, reason);
    }
    List<JsonNode> data = response.getData();
    if (data == null || data.size() != 1 || data.getFirst() == null) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    JsonNode item = data.getFirst();
    InvestmentChoiceScenario scenario = scenarioMapper.scenario(item);
    if (scenario == InvestmentChoiceScenario.NORMAL) {
      return mapNormalItem(scenarioMapper.normalItem(item));
    }
    return mapRestrictedItem(scenario, scenarioMapper.restrictedRows(item));
  }

  private InvestmentChoiceDetails.Normal mapNormalItem(TrpGetCIMDtlApimDataItem item) {
    if (item.intervalPcnt() == null || item.intervalPcnt() < 1 || item.intervalPcnt() > 100) {
      throw new InvestmentChoiceMalformedResponseException();
    }
    List<SwitchSrc> wireSources = item.switchSrc();
    List<SwitchFund> wireFunds = item.switchFund();
    List<InvestMandate> wireMandates = item.investMandate();
    List<PendingReq> wirePending = item.pendingReq() == null ? List.of() : item.pendingReq();
    List<PendingReqDtl> wirePendingDetails =
        item.pendingReqDtl() == null ? List.of() : item.pendingReqDtl();
    requireLists(wireSources, wireFunds, wireMandates);

    List<InvestmentChoiceSource> sources = mapSources(wireSources);
    List<InvestmentChoiceFund> funds = mapFunds(wireFunds);
    List<InvestmentChoiceMandate> mandates = mapMandates(wireMandates);
    List<InvestmentChoicePending> pending = mapPending(wirePending, wirePendingDetails);
    return new InvestmentChoiceDetails.Normal(
        item.intervalPcnt(), sources, funds, mandates, pending);
  }

  private InvestmentChoiceDetails.Restricted mapRestrictedItem(
      InvestmentChoiceScenario scenario, TrpGetCIMDtlApimScenarioMapper.RestrictedRows rows) {
    List<InvestmentChoiceSource> sources = mapSources(rows.sources());
    List<InvestmentChoiceFund> funds = mapFunds(rows.funds());
    List<InvestmentChoiceMandate> mandates = mapMandates(rows.mandates());
    return new InvestmentChoiceDetails.Restricted(scenario, sources, funds, mandates);
  }

  private List<InvestmentChoiceSource> mapSources(List<SwitchSrc> rows) {
    Set<String> codes = new HashSet<>();
    List<InvestmentChoiceSource> result = new ArrayList<>(rows.size());
    for (SwitchSrc row : rows) {
      if (row == null
          || !hasText(row.switchSrc())
          || row.seq() == null
          || row.availSw() == null
          || !codes.add(row.switchSrc())) {
        throw new InvestmentChoiceMalformedResponseException();
      }
      result.add(
          new InvestmentChoiceSource(row.switchSrc(), row.srcDesc(), row.seq(), row.availSw()));
    }
    return List.copyOf(result);
  }

  private List<InvestmentChoiceFund> mapFunds(List<SwitchFund> rows) {
    Set<String> codes = new HashSet<>();
    List<InvestmentChoiceFund> result = new ArrayList<>(rows.size());
    for (SwitchFund row : rows) {
      if (row == null
          || !hasText(row.fundCode())
          || !hasText(row.fundName())
          || row.availSw() == null
          || !codes.add(row.fundCode())) {
        throw new InvestmentChoiceMalformedResponseException();
      }
      String chineseName = row.chinFundName();
      if (chineseName != null && chineseName.isBlank()) {
        chineseName = row.fundName();
      }
      result.add(
          new InvestmentChoiceFund(
              row.fundCode(),
              row.fundName(),
              chineseName,
              row.fundSeq(),
              row.riskLevel(),
              row.availSw()));
    }
    return List.copyOf(result);
  }

  private List<InvestmentChoiceMandate> mapMandates(List<InvestMandate> rows) {
    List<InvestmentChoiceMandate> result = new ArrayList<>(rows.size());
    for (InvestMandate row : rows) {
      if (row == null
          || !hasText(row.switchSrc())
          || !hasText(row.fundCode())
          || !hasText(row.pcnt())) {
        throw new InvestmentChoiceMalformedResponseException();
      }
      result.add(
          new InvestmentChoiceMandate(
              TrpGetCIMDtlApimMapper.parseDate(row.effectDate()),
              row.switchSrc(),
              row.fundCode(),
              row.pcnt()));
    }
    return List.copyOf(result);
  }

  private List<InvestmentChoicePending> mapPending(
      List<PendingReq> requests, List<PendingReqDtl> details) {
    Set<String> referenceNumbers = new HashSet<>();
    Map<String, PendingRequestData> requestsByRef = new LinkedHashMap<>();
    for (PendingReq row : requests) {
      if (row == null || !hasText(row.refNo()) || !referenceNumbers.add(row.refNo())) {
        throw new InvestmentChoiceMalformedResponseException();
      }
      requestsByRef.put(
          row.refNo(),
          new PendingRequestData(
              TrpGetCIMDtlApimMapper.parseDate(row.submitDate()),
              TrpGetCIMDtlApimMapper.parseTime(row.submitTime())));
    }

    Map<String, List<InvestmentChoicePendingDetail>> detailsByRef = new LinkedHashMap<>();
    for (PendingReqDtl row : details) {
      if (row == null
          || !hasText(row.refNo())
          || !requestsByRef.containsKey(row.refNo())
          || !hasText(row.switchSrc())
          || !hasText(row.fundCode())
          || row.pcnt() == null
          || row.pcnt() < 1
          || row.pcnt() > 100) {
        throw new InvestmentChoiceMalformedResponseException();
      }
      detailsByRef
          .computeIfAbsent(row.refNo(), ignored -> new ArrayList<>())
          .add(new InvestmentChoicePendingDetail(row.switchSrc(), row.fundCode(), row.pcnt()));
    }

    List<InvestmentChoicePending> result = new ArrayList<>(requests.size());
    for (PendingReq row : requests) {
      var pendingDetails = detailsByRef.get(row.refNo());
      if (pendingDetails == null || pendingDetails.isEmpty()) {
        throw new InvestmentChoiceMalformedResponseException();
      }
      var requestData = requestsByRef.get(row.refNo());
      result.add(
          new InvestmentChoicePending(
              row.refNo(), requestData.submitDate(), requestData.submitTime(), pendingDetails));
    }
    return List.copyOf(result);
  }

  private void publishFailure(reactor.core.publisher.Signal<InvestmentChoiceDetails> signal) {
    if (!signal.isOnError()) {
      return;
    }
    Throwable failure = signal.getThrowable();
    FailureOrigin origin;
    String internalCode = null;
    String reason;
    if (failure instanceof DownstreamErrorMessageException error) {
      origin = FailureOrigin.DOWNSTREAM_CONTRACT;
      internalCode = error.code();
      reason = error.reason();
    } else if (failure instanceof InvestmentChoiceMalformedResponseException) {
      origin = FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      reason = "MALFORMED_RESPONSE";
    } else {
      ApimFailureCategory category = ApimFailureClassifier.classify(failure);
      origin = toFailureOrigin(category);
      reason = category.name();
    }
    String requestId =
        signal.getContextView().getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, null);
    failureEventPort.publish(
        new FailureEvent(
            EVENT,
            requestId,
            CAPABILITY,
            OPERATION,
            origin,
            internalCode,
            PUBLIC_CODE,
            503,
            true,
            Map.of("reason", reason)));
  }

  private static FailureOrigin toFailureOrigin(ApimFailureCategory category) {
    return switch (category) {
      case DOWNSTREAM_CONTRACT -> FailureOrigin.DOWNSTREAM_CONTRACT;
      case DOWNSTREAM_INVALID_RESPONSE -> FailureOrigin.DOWNSTREAM_INVALID_RESPONSE;
      case DOWNSTREAM_TRANSPORT -> FailureOrigin.DOWNSTREAM_TRANSPORT;
      case LOCAL_SYSTEM -> FailureOrigin.LOCAL_SYSTEM;
    };
  }

  private static void requireLists(List<?> sources, List<?> funds, List<?> mandates) {
    if (sources == null || funds == null || mandates == null) {
      throw new InvestmentChoiceMalformedResponseException();
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record PendingRequestData(
      java.time.LocalDate submitDate, java.time.LocalTime submitTime) {}

  private static final class DownstreamErrorMessageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String reason;

    private DownstreamErrorMessageException(String code, String reason) {
      super("APIM returned an error message");
      this.code = Objects.requireNonNull(code);
      this.reason = Objects.requireNonNull(reason);
    }

    private String code() {
      return code;
    }

    private String reason() {
      return reason;
    }
  }
}
