package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.math.BigDecimal;

/**
 * A numeric/text display pair for an amount field.
 * {@code value} is the raw numeric amount; {@code text} is the locale-formatted display string.
 */
public record ContributionAmountValueResponse(BigDecimal value, String text) {}
