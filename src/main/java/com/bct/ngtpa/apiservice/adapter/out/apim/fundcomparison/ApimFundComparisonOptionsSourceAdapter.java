package com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimFundItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundcomparison.dto.TrpGetFundCmpOptApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * TRPGetFundCmpOpt outbound adapter. Mirrors {@code ApimFundPriceModeSourceAdapter}'s exact pattern
 * (script 028 evidence): build a typed {@link ApimExchangeRequest} and call {@link
 * ApimExchangeExecutor#postEnvelope(ApimExchangeRequest)}. Per
 * docs/design/13-fund-comparison/03-downstream-integration.md section 3.1: {@code
 * ApimDatabaseScope.ACCOUNT_ENV}, caller-supplied actor role and account environment, {@code
 * ApimRetryEligibility.SAFE_BUSINESS_CALL}. Every nonblank downstream {@code err-message}, or any
 * structurally unusable envelope/data shape, maps to the single generic {@link
 * FundComparisonRetrievalException}; there is no branch on specific error text.
 *
 * <p>This adapter returns the RAW, unparsed time-range string; grammar validation and normalization
 * of that string is {@code FundComparisonTimeRangeParser}'s responsibility (Milestone 1), invoked
 * later by the options use case (sub-task 2.3) -- not duplicated here.
 */
@Component
public final class ApimFundComparisonOptionsSourceAdapter
    implements FundComparisonOptionsSourcePort {

  private final ApimExchangeExecutor executor;
  private final String path;

  @Autowired
  public ApimFundComparisonOptionsSourceAdapter(
      ApimExchangeExecutor executor, FundComparisonOptionsApimProperties properties) {
    this(executor, properties.getPath());
  }

  ApimFundComparisonOptionsSourceAdapter(ApimExchangeExecutor executor, String path) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    this.path = path;
  }

  @Override
  public Mono<RawFundComparisonOptionsData> fetchOptions(
      FetchFundComparisonOptionsCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    var request = new TrpGetFundCmpOptApimRequest(command.policyNo());
    var exchange =
        new ApimExchangeRequest<>(
            path,
            request,
            TrpGetFundCmpOptApimRequest.class,
            TrpGetFundCmpOptApimDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(command.actorUserRole(), command.accountEnv()),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);

    return executor
        .postEnvelope(exchange)
        .flatMap(
            envelope -> {
              var response = envelope == null ? null : envelope.getResponse();
              if (response == null) {
                return Mono.error(FundComparisonRetrievalException.systemFailure());
              }
              String errMessage = response.getErrMessage();
              if (errMessage != null && !errMessage.isEmpty()) {
                return Mono.error(FundComparisonRetrievalException.systemFailure());
              }
              try {
                return Mono.just(normalize(response.getData()));
              } catch (RuntimeException exception) {
                return Mono.error(FundComparisonRetrievalException.systemFailure());
              }
            })
        .onErrorMap(
            exception ->
                exception instanceof FundComparisonRetrievalException
                    ? exception
                    : FundComparisonRetrievalException.systemFailure());
  }

  private static RawFundComparisonOptionsData normalize(List<TrpGetFundCmpOptApimDataItem> data) {
    if (data == null || data.size() != 1) {
      throw new IllegalStateException("expected exactly one TRPGetFundCmpOpt data item");
    }
    TrpGetFundCmpOptApimDataItem item = data.get(0);

    List<TrpGetFundCmpOptApimFundItem> rawFunds = item.fundList();
    if (rawFunds == null || rawFunds.isEmpty()) {
      throw new IllegalStateException("fund-list must not be null or empty");
    }

    List<RawFundComparisonOption> options =
        rawFunds.stream()
            .map(f -> new RawFundComparisonOption(f.fundCode(), f.fundName(), f.chinFundName()))
            .toList();

    return new RawFundComparisonOptionsData(item.timeRange(), options);
  }
}
