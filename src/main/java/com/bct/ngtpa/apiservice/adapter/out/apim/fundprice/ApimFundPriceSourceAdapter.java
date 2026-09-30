package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceRangeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * TRPGetFundPrice outbound adapter.
 *
 * <p>Mirrors {@link ApimFundPriceModeSourceAdapter}'s exact structure: build a typed {@link
 * ApimExchangeRequest} and call {@link ApimExchangeExecutor#postEnvelope(ApimExchangeRequest)}.
 * Per docs/design/9-fund-price/fund-price-apim-contract.md: {@link ApimDatabaseScope#ACCOUNT_ENV},
 * caller-supplied actor role and account environment, {@link
 * ApimRetryEligibility#SAFE_BUSINESS_CALL}. Every nonblank downstream {@code err-message} maps to
 * the single generic {@link FundPriceRetrievalException}.
 *
 * <p>The command carries ONE FE-validated date, unmanipulated. Both DAILY and MONTHLY modes
 * send that exact date as both the downstream request's from-date and to-date -- mode
 * has no effect on this adapter's downstream request shape (corrected, Nick: an earlier
 * version of this adapter incorrectly expanded MONTHLY into a first/last-calendar-day
 * range; there is no such expansion). Mode exists solely so FE can choose the correct
 * picker UI.
 */
@Component
public final class ApimFundPriceSourceAdapter implements FundPriceSourcePort {

  
  private final ApimExchangeExecutor executor;
  private final String path;
  private final DateTimeFormatter formatter;

  @Autowired
  public ApimFundPriceSourceAdapter(
      ApimExchangeExecutor executor,
      FundPriceApimProperties properties,
      DateTimeFormatter fundPriceFullDateFormatter) {
    this(executor, properties.getPath(), fundPriceFullDateFormatter);
  }

  ApimFundPriceSourceAdapter(ApimExchangeExecutor executor, String path, DateTimeFormatter formatter) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    this.path = path;
    this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
  }

  @Override
  public Mono<RawFundPriceData> fetchPrice(FetchFundPriceCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    LocalDate fromDate;
    LocalDate toDate;
    // MONTHLY does not differ from DAILY in downstream range derivation (corrected,
    // Nick): FE always submits one exact validated date regardless of mode; Java sends
    // that same date as both from-date and to-date. mode has no effect on this
    // adapter's downstream request shape and exists only so FE can choose the correct
    // picker UI.
    fromDate = command.date();
    toDate = command.date();

    return exchangeAndReconcile(
        command.policyNo(), fromDate, toDate, command.accountEnv(), command.actorUserRole());
  }

  @Override
  public Mono<RawFundPriceData> fetchPriceRange(FetchFundPriceRangeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    // NO mode logic at all -- the explicit fromDate/toDate range is passed straight through,
    // confirmed design (export has no mode concept).
    return exchangeAndReconcile(
        command.policyNo(),
        command.fromDate(),
        command.toDate(),
        command.accountEnv(),
        command.actorUserRole());
  }

  private Mono<RawFundPriceData> exchangeAndReconcile(
      String policyNo,
      LocalDate fromDate,
      LocalDate toDate,
      String accountEnv,
      com.bct.ngtpa.apiservice.application.dto.ActorUserRole actorUserRole) {
    var request =
        new TrpGetFundPriceApimRequest(
            policyNo, fromDate.format(formatter), toDate.format(formatter));
    var exchange =
        new ApimExchangeRequest<>(
            path,
            request,
            TrpGetFundPriceApimRequest.class,
            TrpGetFundPriceApimDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(actorUserRole, accountEnv),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);

    return executor
        .postEnvelope(exchange)
        .flatMap(
            envelope -> {
              // A Reactor Mono can never emit a null value through flatMap (the Reactive
              // Streams contract forbids null onNext signals) -- so `envelope` here is
              // guaranteed non-null, and an `envelope == null` check would be unreachable
              // dead code. Removed here (coverage-closure discipline, mirroring script
              // 095's identical finding in FundPriceApimContentValidator) rather than
              // left permanently uncovered. A null RESPONSE inside a non-null envelope
              // remains a real, reachable failure mode and is still checked below.
              var response = envelope.getResponse();
              if (response == null) {
                return Mono.error(FundPriceRetrievalException.systemFailure());
              }
              String errMessage = response.getErrMessage();
              if (errMessage != null && !errMessage.isEmpty()) {
                return Mono.error(FundPriceRetrievalException.systemFailure());
              }
              try {
                var items = response.getData();
                if (items == null || items.size() != 1 || items.get(0) == null) {
                  return Mono.error(FundPriceRetrievalException.systemFailure());
                }
                return Mono.just(FundPriceApimContentValidator.reconcile(items.get(0)));
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
