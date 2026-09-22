package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import java.util.List;

public record SubDisplayLevelSubDisplayAccountResponse(
    String subDisplayAccountName,
    String subDisplayAccountId,
    List<SubDisplayLevelFundResponse> funds) {
  public SubDisplayLevelSubDisplayAccountResponse {
    funds = funds == null ? List.of() : List.copyOf(funds);
  }
}
