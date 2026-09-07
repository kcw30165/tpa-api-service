package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import java.math.BigDecimal;

public record RawFundGainLoss(
    String fundCode,
    String fundName,
    String chineseFundName,
    Integer fundSequence,
    BigDecimal gainLoss) {}
