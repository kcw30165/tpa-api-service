package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.NormalizedAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;

@FunctionalInterface
public interface AccountBalanceNormalizer {
  NormalizedAccountBalanceData normalize(RawAccountBalanceData rawData);
}
