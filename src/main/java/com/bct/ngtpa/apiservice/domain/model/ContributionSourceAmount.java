package com.bct.ngtpa.apiservice.domain.model;

import java.math.BigDecimal;

public record ContributionSourceAmount(
        ContributionSource source,
        BigDecimal amount
) {}