package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * A value/text display pair for a currency field.
 * {@code value} is the raw ISO currency code; {@code text} is the locale-mapped display name.
 */
public record ContributionCurrencyValueResponse(String value, String text) {}
