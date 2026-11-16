package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import reactor.core.publisher.Mono;

public interface VestingScalePort {
  Mono<VestingScaleData> load(VestingScaleCommand command);
}
