package com.bct.ngtpa.apiservice.adapter.in.web.response.accountgainloss;

public record AccountGainLossFundResponse(
    String fundCode, String fundName, Integer fundSequence, String gainLoss) {}
