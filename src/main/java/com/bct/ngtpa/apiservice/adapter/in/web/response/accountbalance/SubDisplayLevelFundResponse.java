package com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SubDisplayLevelFundResponse(
    String fundCode,
    String fundName,
    String fundPrice,
    String unitBalance,
    String accountBalance,
    @JsonInclude(JsonInclude.Include.NON_NULL) String factsheetUrl,
    @JsonInclude(JsonInclude.Include.NON_NULL) String fxRate) {}
