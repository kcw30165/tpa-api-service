package com.bct.ngtpa.apiservice.application.dto.accountbalance.raw;

import java.math.BigDecimal;

public record RawAccountBalanceHolding(
    String displaySource,
    String subDisplaySource,
    String fundCode,
    BigDecimal unit,
    BigDecimal netAssetValue,
    BigDecimal vestedBalance,
    BigDecimal transitAmount) {}
