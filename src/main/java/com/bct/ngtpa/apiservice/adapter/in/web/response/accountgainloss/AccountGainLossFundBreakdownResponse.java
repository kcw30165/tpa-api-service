package com.bct.ngtpa.apiservice.adapter.in.web.response.accountgainloss;

import java.util.List;

public record AccountGainLossFundBreakdownResponse(
    int fundCount, List<AccountGainLossFundResponse> funds) {
  public AccountGainLossFundBreakdownResponse {
    funds = List.copyOf(funds);
  }
}
