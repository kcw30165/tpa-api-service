package com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio;

import java.math.BigDecimal;

public record RawFundLevelSubDisplayAccount(
    String subDisplayAccountId,
    Integer displaySequence,
    BigDecimal unitBalance,
    BigDecimal accountBalance) {}
