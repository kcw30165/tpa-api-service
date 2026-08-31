package com.bct.ngtpa.apiservice.application.dto.accountbalance.raw;

import java.math.BigDecimal;

public record RawAccountBalanceFundDetail(
    String fundCode,
    String fundName,
    String chineseFundName,
    Integer fundSequence,
    BigDecimal fundPrice,
    String fundCurrency,
    BigDecimal exchangeRate,
    String fundFactsheet) {}
