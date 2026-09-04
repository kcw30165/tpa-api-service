package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

public record AccountGainLossFund(
    String fundCode, String fundName, Integer fundSequence, String gainLoss) {}
