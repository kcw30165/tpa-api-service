package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.FetchAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceSourcePort;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class ApimAccountBalanceSourceAdapter implements AccountBalanceSourcePort {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String NOT_FOUND = "err.tpa.account.invalid",
      MISMATCH = "err.tpa.member.not.found";
  private final ApimExchangeExecutor executor;
  private final String path;

  public ApimAccountBalanceSourceAdapter(
      ApimExchangeExecutor executor, AccountBalanceApimProperties properties) {
    this.executor = executor;
    this.path = properties.getPath();
  }

  public Mono<RawAccountBalanceData> fetchRawAccountBalance(FetchAccountBalanceCommand command) {
    var request =
        new TrpGetMemberAccBalApimRequest(
            command.loginUserId(),
            command.actorUserRole().toApimValue(),
            command.policyNo(),
            command.certNo(),
            command.referenceDate().format(DATE),
            command.currency());
    var exchange =
        new ApimExchangeRequest<>(
            path,
            request,
            TrpGetMemberAccBalApimRequest.class,
            TrpGetMemberAccBalApimDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            new ApimCredentialResolutionContext(command.actorUserRole(), command.accountEnv()),
            ApimRetryEligibility.SAFE_BUSINESS_CALL);
    return executor
        .postEnvelope(exchange)
        .flatMap(
            envelope -> {
              var response = envelope == null ? null : envelope.getResponse();
              String error = response == null ? null : response.getErrMessage();
              if (NOT_FOUND.equals(error))
                return Mono.error(AccountBalanceRetrievalException.accountNotFound());
              if (MISMATCH.equals(error))
                return Mono.error(AccountBalanceRetrievalException.accountMismatch());
              if (error != null && !error.isEmpty())
                return Mono.error(AccountBalanceRetrievalException.systemFailure());
              try {
                return Mono.just(
                    map(
                        AccountBalanceApimContentValidator.requireSingleData(
                            ApimResponseValidator.requireSuccessData(envelope))));
              } catch (RuntimeException e) {
                return Mono.error(AccountBalanceRetrievalException.systemFailure());
              }
            })
        .onErrorMap(
            e ->
                e instanceof AccountBalanceRetrievalException
                    ? e
                    : AccountBalanceRetrievalException.systemFailure());
  }

  private static RawAccountBalanceData map(TrpGetMemberAccBalApimDataItem x) {
    var displayOptions = AccountBalanceApimContentValidator.requireDisplayOptions(x);
    var currency = AccountBalanceApimContentValidator.requireCurrency(x);
    return new RawAccountBalanceData(
        new RawAccountBalanceDisplayOptions(
            displayOptions.vestedBalanceVisible(),
            displayOptions.pendingSwitchInVisible(),
            displayOptions.fxRateVisible()),
        new RawAccountBalanceCurrency(currency.available(), currency.selected()),
        x.displaySources().stream()
            .map(
                v ->
                    new RawAccountBalanceDisplaySource(
                        v.displaySource(),
                        v.sourceDescription(),
                        v.sourceChineseDescription(),
                        v.subDisplaySource(),
                        v.subSourceDescription(),
                        v.subSourceChineseDescription(),
                        v.sequence()))
            .toList(),
        x.fundDetails().stream()
            .map(
                v ->
                    new RawAccountBalanceFundDetail(
                        v.fundCode(),
                        v.fundName(),
                        v.chineseFundName(),
                        v.fundSequence(),
                        v.fundPrice(),
                        v.fundCurrency(),
                        v.exchangeRate(),
                        v.fundFactsheet()))
            .toList(),
        x.accountBalances().stream()
            .map(
                v ->
                    new RawAccountBalanceHolding(
                        v.displaySource(),
                        v.subDisplaySource(),
                        v.fundCode(),
                        v.unit(),
                        v.netAssetValue(),
                        v.vestedBalance(),
                        v.transitAmount()))
            .toList());
  }
}
