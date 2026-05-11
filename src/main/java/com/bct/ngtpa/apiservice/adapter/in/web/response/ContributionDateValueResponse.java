package com.bct.ngtpa.apiservice.adapter.in.web.response;

/**
 * A value/text display pair for a date field.
 * {@code value} is always ISO {@code yyyy-MM-dd}; {@code text} is the locale-formatted display string.
 */
public record ContributionDateValueResponse(String value, String text) {}
