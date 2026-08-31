package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto;

import com.fasterxml.jackson.annotation.*;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrpGetMemberAccBalApimDataItem(
    @JsonProperty("displayOptions") List<AccountBalanceDisplayOptions> displayOptions,
    @JsonProperty("curr") List<AccountBalanceCurrency> currencies,
    @JsonProperty("dispSrc") List<AccountBalanceDisplaySource> displaySources,
    @JsonProperty("fundDtl") List<AccountBalanceFundDetail> fundDetails,
    @JsonProperty("accBal") List<AccountBalanceHolding> accountBalances) {}
