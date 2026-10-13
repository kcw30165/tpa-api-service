package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountBalanceDisplayOptions(
    boolean vestedBalanceVisible, boolean pendingSwitchInVisible, boolean fxRateVisible) {}
