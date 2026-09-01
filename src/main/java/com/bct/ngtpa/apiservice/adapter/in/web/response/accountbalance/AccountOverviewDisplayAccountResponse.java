package com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AccountOverviewDisplayAccountResponse(
    String displayAccountId,
    String displayAccountLabel,
    Integer displaySequence,
    String accountBalance,
    @JsonInclude(JsonInclude.Include.ALWAYS) String vestedBalance) {}
