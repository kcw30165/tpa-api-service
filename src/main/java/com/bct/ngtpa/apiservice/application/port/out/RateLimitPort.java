package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitResult;
import reactor.core.publisher.Mono;

public interface RateLimitPort {

  Mono<RateLimitResult> checkAndConsume(RateLimitCommand command);
}
