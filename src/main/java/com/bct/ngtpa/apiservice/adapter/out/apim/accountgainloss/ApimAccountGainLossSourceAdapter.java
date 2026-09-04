package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.*;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class ApimAccountGainLossSourceAdapter implements AccountGainLossSourcePort {
  private static final Logger LOG = LoggerFactory.getLogger(ApimAccountGainLossSourceAdapter.class);
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final String SWITCHING = "err.tpa.fund.swithing.in.progress";
  private final ApimExchangeExecutor executor;
  private final String path;

  public ApimAccountGainLossSourceAdapter(
      ApimExchangeExecutor executor, AccountGainLossApimProperties properties) {
    this.executor = executor;
    this.path = properties.getPath();
  }

  @Override
  public Mono<RawAccountGainLossData> fetch(FetchAccountGainLossCommand command) {
    try {
      var request = new TrpGetInvestGlApimRequest(
          command.actorUserId(), command.actorUserRole().toApimValue(), command.policyNo(),
          Integer.valueOf(command.certNo().trim()), command.referenceDate().format(DATE),
          AccountGainLossApimModeMapper.toWireValue(command.period()));
      var exchange = new ApimExchangeRequest<>(
          path, request, TrpGetInvestGlApimRequest.class, TrpGetInvestGlApimDataItem.class,
          ApimDatabaseScope.ACCOUNT_ENV,
          new ApimCredentialResolutionContext(command.actorUserRole(), command.accountEnv()),
          ApimRetryEligibility.SAFE_BUSINESS_CALL);
      return executor.postEnvelope(exchange)
          .flatMap(envelope -> {
            var response = envelope == null ? null : envelope.getResponse();
            String error = response == null ? null : response.getErrMessage();
            if (SWITCHING.equals(error))
              return Mono.error(AccountGainLossRetrievalException.fundSwitchingInProgress());
            if (error != null && !error.isEmpty()) {
              LOG.warn("event=account-gain-loss.downstream-rejected operation=TRPGetInvestGL errorCode={}", error);
              return Mono.error(AccountGainLossRetrievalException.systemFailure());
            }
            try {
              var item = AccountGainLossApimContentValidator.requireSingleData(
                  ApimResponseValidator.requireSuccessData(envelope));
              return Mono.just(new RawAccountGainLossData(
                  item.priceDate(), item.funds().stream()
                      .map(f -> new RawFundGainLoss(
                          f.fundCode().trim(), f.fundName(), f.chineseFundName(),
                          f.fundSequence(), f.gainLoss()))
                      .toList()));
            } catch (RuntimeException ex) {
              return Mono.error(AccountGainLossRetrievalException.systemFailure());
            }
          })
          .onErrorMap(ex -> ex instanceof AccountGainLossRetrievalException
              ? ex : AccountGainLossRetrievalException.systemFailure());
    } catch (RuntimeException ex) {
      return Mono.error(AccountGainLossRetrievalException.systemFailure());
    }
  }
}
