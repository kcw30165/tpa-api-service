package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;

public record AccountBalanceDisplayOptions(
    boolean vestedBalanceVisible, boolean pendingSwitchInVisible, boolean fxRateVisible) {}
