package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import java.util.List;

public record SubDisplayLevelDisplayAccountResponse(
    String displayAccountName,
    String displayAccountId,
    Integer displaySequence,
    List<SubDisplayLevelSubDisplayAccountResponse> subDisplayAccounts) {
  public SubDisplayLevelDisplayAccountResponse {
    subDisplayAccounts = subDisplayAccounts == null ? List.of() : List.copyOf(subDisplayAccounts);
  }
}
