package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import java.math.BigDecimal;

public record VestingScaleRowResponse(int completedYears, BigDecimal vestingPercentage) {}
