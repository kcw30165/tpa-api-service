package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FetchFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetData;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetRow;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetSourcePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * TRPGetFundFactSheet outbound adapter.
 *
 * <p>Builds a typed exchange request and calls the shared exchange executor. The executor already
 * logs contract-invalid, business-rejected and exchange-failed outcomes, so this adapter logs only
 * a null data item, a null row list and a null row, without any value. Every non-empty downstream
 * error message and every malformed response map to the single generic {@link
 * FundFactSheetRetrievalException}. No funds is a non-empty data list whose items carry empty row
 * lists; it yields zero rows.
 */
@Component
public final class ApimFundFactSheetSourceAdapter implements FundFactSheetSourcePort {

  private static final Logger LOG = LoggerFactory.getLogger(ApimFundFactSheetSourceAdapter.class);
  private static final String EVENT_NULL_ITEM =
      "event=fund-fact-sheet.downstream-null-item operation=TRPGetFundFactSheet";
  private static final String EVENT_NULL_LIST =
      "event=fund-fact-sheet.downstream-null-list operation=TRPGetFundFactSheet";
  private static final String EVENT_NULL_ROW =
      "event=fund-fact-sheet.downstream-null-row operation=TRPGetFundFactSheet";

  private final ApimExchangeExecutor executor;
  private final String path;

  @Autowired
  public ApimFundFactSheetSourceAdapter(
      ApimExchangeExecutor executor, FundFactSheetApimProperties properties) {
    this(executor, properties.getPath());
  }

  ApimFundFactSheetSourceAdapter(ApimExchangeExecutor executor, String path) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be blank");
    }
    this.path = path;
  }

  @Override
  public Mono<RawFundFactSheetData> fetch(FetchFundFactSheetCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return Mono.defer(
            () -> {
              var request =
                  new TrpGetFundFactSheetApimRequest(
                      command.policyNo(), command.certNo(), command.schemeType());
              var exchange =
                  new ApimExchangeRequest<>(
                      path,
                      request,
                      TrpGetFundFactSheetApimRequest.class,
                      TrpGetFundFactSheetApimDataItem.class,
                      ApimDatabaseScope.ACCOUNT_ENV,
                      new ApimCredentialResolutionContext(
                          command.actorUserRole(), command.accountEnv()),
                      ApimRetryEligibility.SAFE_BUSINESS_CALL);
              return executor
                  .postEnvelope(exchange)
                  .flatMap(ApimFundFactSheetSourceAdapter::toRawData);
            })
        .onErrorMap(
            exception ->
                exception instanceof FundFactSheetRetrievalException
                    ? exception
                    : FundFactSheetRetrievalException.systemFailure());
  }

  private static Mono<RawFundFactSheetData> toRawData(
      ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> envelope) {
    var response = envelope.getResponse();
    if (response == null) {
      return Mono.error(FundFactSheetRetrievalException.systemFailure());
    }
    String errMessage = response.getErrMessage();
    if (errMessage == null || !errMessage.isEmpty()) {
      return Mono.error(FundFactSheetRetrievalException.systemFailure());
    }
    var items = response.getData();
    if (items == null || items.isEmpty()) {
      return Mono.error(FundFactSheetRetrievalException.systemFailure());
    }
    List<RawFundFactSheetRow> rows = new ArrayList<>();
    for (var item : items) {
      if (item == null) {
        LOG.error(EVENT_NULL_ITEM);
        return Mono.error(FundFactSheetRetrievalException.systemFailure());
      }
      var wireRows = item.fundFactSheet();
      if (wireRows == null) {
        LOG.error(EVENT_NULL_LIST);
        return Mono.error(FundFactSheetRetrievalException.systemFailure());
      }
      for (var row : wireRows) {
        if (row == null) {
          LOG.error(EVENT_NULL_ROW);
          return Mono.error(FundFactSheetRetrievalException.systemFailure());
        }
        rows.add(
            new RawFundFactSheetRow(
                row.seq(), row.nameEng(), row.nameChi(), row.linkEng(), row.linkChi()));
      }
    }
    return Mono.just(new RawFundFactSheetData(rows));
  }
}
