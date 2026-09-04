package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.accountgainloss.FetchAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawAccountGainLossData;
import reactor.core.publisher.Mono;

public interface AccountGainLossSourcePort {
  Mono<RawAccountGainLossData> fetch(FetchAccountGainLossCommand command);
}
