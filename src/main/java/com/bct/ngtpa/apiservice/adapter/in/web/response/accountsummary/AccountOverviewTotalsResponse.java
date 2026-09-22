package com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AccountOverviewTotalsResponse(
    String totalBalance, @JsonInclude(JsonInclude.Include.ALWAYS) String pendingSwitchIn) {}
