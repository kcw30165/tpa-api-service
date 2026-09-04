package com.bct.ngtpa.apiservice.adapter.in.web.response.accountgainloss;

import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;

public record AccountGainLossDataResponse(
    AccountGainLossPeriod period,
    String asOfDate,
    AccountGainLossOverviewResponse overview,
    AccountGainLossFundBreakdownResponse fundBreakdown) {}
