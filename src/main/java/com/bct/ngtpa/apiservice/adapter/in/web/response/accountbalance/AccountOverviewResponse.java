package com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AccountOverviewResponse(
    String displayAccountLabel,
    @JsonInclude(JsonInclude.Include.ALWAYS) String vestedBalance,
    @JsonInclude(JsonInclude.Include.ALWAYS) String pendingSwitchIn) {}
