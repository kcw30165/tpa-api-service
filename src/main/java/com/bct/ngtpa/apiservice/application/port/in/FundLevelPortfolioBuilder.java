package com.bct.ngtpa.apiservice.application.port.in;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.NormalizedAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio.RawFundLevelView;
@FunctionalInterface public interface FundLevelPortfolioBuilder { RawFundLevelView build(NormalizedAccountBalanceData normalizedData); }
