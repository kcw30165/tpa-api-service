package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.GetVestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleResult;
import reactor.core.publisher.Mono;

public interface GetVestingScaleUseCase {
  Mono<VestingScaleResult> get(GetVestingScaleCommand command);
}
