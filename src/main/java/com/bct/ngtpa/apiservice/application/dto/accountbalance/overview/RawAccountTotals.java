package com.bct.ngtpa.apiservice.application.dto.accountbalance.overview;

import java.math.BigDecimal;

public record RawAccountTotals(BigDecimal totalBalance, BigDecimal pendingSwitchIn) {}
