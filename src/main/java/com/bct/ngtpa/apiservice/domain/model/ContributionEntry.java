package com.bct.ngtpa.apiservice.domain.model;

import java.math.BigDecimal;

public record ContributionEntry(
        String sourceCode,
        String coverFrom,
        String coverTo,
        String dealingDate,
        BigDecimal amount
) {}