package com.bct.ngtpa.apiservice.application.dto.accountbalance.overview;
import java.math.BigDecimal;
public record RawDisplayAccountOverview(String displayAccountId, Integer displaySequence, BigDecimal accountBalance, BigDecimal vestedBalance) {}
