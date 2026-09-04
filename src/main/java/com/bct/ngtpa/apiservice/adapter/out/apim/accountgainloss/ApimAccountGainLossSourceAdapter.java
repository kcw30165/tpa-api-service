package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimResponseValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.FetchAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawAccountGainLossData;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawFundGainLoss;
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
      var request =
          new TrpGetInvestGlApimRequest(
              command.actorUserId(),
              command.actorUserRole().toApimValue(),
              command.policyNo(),
              Integer.valueOf(command.certNo().trim()),
              command.referenceDate().format(DATE),
              AccountGainLossApimModeMapper.toWireValue(command.period()));
      var exchange =
          new ApimExchangeRequest<>(
              path,
              request,
              TrpGetInvestGlApimRequest.class,
              TrpGetInvestGlApimDataItem.class,
              ApimDatabaseScope.ACCOUNT_ENV,
              new ApimCredentialResolutionContext(
                  command.actorUserRole(), command.accountEnv()),
              ApimRetryEligibility.SAFE_BUSINESS_CALL);
      return executor
          .postEnvelope(exchange)
          .flatMap(
              envelope -> {
                var response = envelope == null ? null : envelope.getResponse();
                String error = response == null ? null : response.getErrMessage();
                if (SWITCHING.equals(error)) {
                  return Mono.error(
                      AccountGainLossRetrievalException.fundSwitchingInProgress());
                }
                if (error != null && !error.isEmpty()) {
                  LOG.warn(
                      "event=account-gain-loss.downstream-rejected "
                          + "operation=TRPGetInvestGL errorCode={}",
                      safeErrorCode(error));
                  return Mono.error(AccountGainLossRetrievalException.systemFailure());
                }
                try {
                  var item =
                      AccountGainLossApimContentValidator.requireSingleData(
                          ApimResponseValidator.requireSuccessData(envelope));
                  return Mono.just(
                      new RawAccountGainLossData(
                          item.priceDate(),
                          item.funds().stream()
                              .map(
                                  fund ->
                                      new RawFundGainLoss(
                                          fund.fundCode().trim(),
                                          fund.fundName(),
                                          fund.chineseFundName(),
                                          fund.fundSequence(),
                                          fund.gainLoss()))
                              .toList()));
                } catch (RuntimeException exception) {
                  return Mono.error(AccountGainLossRetrievalException.systemFailure());
                }
              })
          .onErrorMap(
              exception ->
                  exception instanceof AccountGainLossRetrievalException
                      ? exception
                      : AccountGainLossRetrievalException.systemFailure());
    } catch (RuntimeException exception) {
      return Mono.error(AccountGainLossRetrievalException.systemFailure());
    }
  }

  private static String safeErrorCode(String value) {
    if (value != null
        && value.length() <= 128
        && value.matches("[A-Za-z0-9._-]+")) {
      return value;
    }
    return "UNRECOGNIZED_APIM_ERROR";
  }
}
