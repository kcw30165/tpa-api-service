package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.GetAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import java.time.LocalDate;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AccountBalanceAssembler {
  Mono<AccountBalanceResult> assemble(
      GetAccountBalanceCommand command,
      LocalDate referenceDate,
      ResolvedGeneralMetadataSet metadata,
      RawAccountBalanceData rawData);
}
