package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.CleanUpAllReferenceDatesResult;
import reactor.core.publisher.Mono;

public interface CleanUpAllReferenceDatesUseCase {
    Mono<CleanUpAllReferenceDatesResult> execute();
}
