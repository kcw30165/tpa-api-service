package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * bidUnitPrice/offerUnitPrice vs. unitPrice branching is driven by null-field omission
 * ({@code @JsonInclude(NON_NULL)}), mirroring the APIM contract's own bid/offer-vs-unit branching
 * one layer up -- not two distinct response types.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FundPriceRowResponse(
    String fundCode,
    String fundName,
    String priceDate,
    String fundCurrency,
    BigDecimal bidUnitPrice,
    BigDecimal offerUnitPrice,
    BigDecimal unitPrice,
    String riskLevel) {}
