package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceModeApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceModeApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceModeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceModeSourcePort;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * TRPGetFundPriceMode outbound adapter.
 *
 * <p>Mirrors the model {@code ApimAccountBalanceSourceAdapter} / {@code
 * ApimGeneralMetadataSourceAdapter} pattern: build a typed {@link ApimExchangeRequest} and call
 * {@link ApimExchangeExecutor#postEnvelope(ApimExchangeRequest)}. Per
 * docs/design/9-fund-price/fund-price-apim-contract.md: {@code ApimDatabaseScope.ACCOUNT_ENV},
 * caller-supplied actor role and account environment, {@code
 * ApimRetryEligibility.SAFE_BUSINESS_CALL}. Every nonblank downstream {@code err-message} maps to
 * the single generic {@link FundPriceRetrievalException}; there is no branch on specific error text
 * (contrast with {@code ApimAccountBalanceSourceAdapter}, which does branch).
 */
@Component
public final class ApimFundPriceModeSourceAdapter implements FundPriceModeSourcePort {

  private final ApimExchangeExecutor executor;
  private final String path;
  private final DateTimeFormatter formatter;

  public ApimFundPriceModeSourceAdapter(
      ApimExchangeExecutor executor,
      FundPriceModeApimProperties properties,
      DateTimeFormatter fundPriceFullDateFormatter) {
    this(executor, properties.getPath(), fundPriceFullDateFormatter);
  }

  ApimFundPriceModeSourceAdapter(
      ApimExchangeExecutor executor, String path, DateTimeFormatter formatter) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    this.path = path;
    this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
  }

  public Mono<RawFundPriceModeData> fetchMode(FetchFundPriceModeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var request =
        new TrpGetFundPriceModeApimRequest(
            command.policyNo(), command.referenceDate().format(formatter));
    var exchange =
        new ApimExchangeRequest<>(
            path,
            request,
            TrpGetFundPriceModeApimRequest.class,
            TrpGetFundPriceModeApimDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(command.actorUserRole(), command.accountEnv()),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);

    return executor
        .postEnvelope(exchange)
        .flatMap(
            envelope -> {
              var response = envelope == null ? null : envelope.getResponse();
              if (response == null) {
                return Mono.error(FundPriceRetrievalException.systemFailure());
              }
              String errMessage = response.getErrMessage();
              if (errMessage != null && !errMessage.isEmpty()) {
                return Mono.error(FundPriceRetrievalException.systemFailure());
              }
              try {
                var item = FundPriceModeApimContentValidator.requireSingleItem(response.getData());
                return Mono.just(FundPriceModeApimContentValidator.normalize(item, formatter));
              } catch (RuntimeException exception) {
                return Mono.error(FundPriceRetrievalException.systemFailure());
              }
            })
        .onErrorMap(
            exception ->
                exception instanceof FundPriceRetrievalException
                    ? exception
                    : FundPriceRetrievalException.systemFailure());
  }
}
