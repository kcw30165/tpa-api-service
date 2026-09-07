package com.bct.ngtpa.apiservice.adapter.in.web.accountgainloss;

import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.util.List;
import org.springframework.http.*;

public final class AccountGainLossRetrievalResponsePolicy {
  private static final String SOURCE_SERVER = "SERVER";

  private AccountGainLossRetrievalResponsePolicy() {}

  public static ResponseEntity<MutationResponse<Void>> map(
      AccountGainLossRetrievalException exception) {
    return switch (exception.reason()) {
      case FUND_SWITCHING_IN_PROGRESS ->
          new ResponseEntity<>(
              MutationResponse.failure(
                  ApiStatus.BUSINESS_REJECTED,
                  List.of(
                      ApiError.business(
                          "err.fund.switching.in.progress", null, List.of(), SOURCE_SERVER))),
              HttpStatus.UNPROCESSABLE_ENTITY);
      case SYSTEM_FAILURE ->
          new ResponseEntity<>(
              MutationResponse.failure(
                  ApiStatus.SYSTEM_ERROR,
                  List.of(ApiError.system("err.system.unavailable", null, SOURCE_SERVER))),
              HttpStatus.SERVICE_UNAVAILABLE);
    };
  }
}
