package com.bct.ngtpa.apiservice.application.port.in;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.NormalizedAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.overview.RawAccountOverviewAggregation;
@FunctionalInterface
public interface AccountOverviewAggregator { RawAccountOverviewAggregation aggregate(NormalizedAccountBalanceData normalizedData); }
