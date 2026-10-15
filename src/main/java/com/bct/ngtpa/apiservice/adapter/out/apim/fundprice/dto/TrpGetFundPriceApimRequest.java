package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Downstream wire request for TRPGetFundPrice. Raw downstream field names confined here.
 *
 * <p>{@code fundList} is a comma-joined string of fund codes, added to support a later
 * fund-comparison feature. For all current (non-comparison) usages it is always sent as an empty
 * string {@code ""}; it is populated solely by {@code ApimFundPriceSourceAdapter}
 * (docs/design/9-fund-price/fund-price-decisions-open-questions.md, confirmed 2026-10-15).
 */
@JsonPropertyOrder({"policy-no", "from-date", "to-date", "fund-list"})
public record TrpGetFundPriceApimRequest(
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("from-date") String fromDate,
    @JsonProperty("to-date") String toDate,
    @JsonProperty("fund-list") String fundList) {}
