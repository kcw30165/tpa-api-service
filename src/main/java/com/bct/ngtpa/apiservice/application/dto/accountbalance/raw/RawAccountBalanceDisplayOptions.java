package com.bct.ngtpa.apiservice.application.dto.accountbalance.raw;

public record RawAccountBalanceDisplayOptions(
    boolean vestedBalanceVisible, boolean pendingSwitchInVisible, boolean fxRateVisible) {}
