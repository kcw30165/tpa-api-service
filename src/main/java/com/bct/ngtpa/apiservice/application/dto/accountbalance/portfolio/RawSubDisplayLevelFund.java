package com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio;

import java.math.BigDecimal;

public record RawSubDisplayLevelFund(
    String fundCode, Integer fundSequence, BigDecimal unitBalance, BigDecimal accountBalance) {}
