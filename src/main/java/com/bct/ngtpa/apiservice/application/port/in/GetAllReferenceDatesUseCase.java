package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.GetAllReferenceDatesResult;
import reactor.core.publisher.Mono;

public interface GetAllReferenceDatesUseCase {
    Mono<GetAllReferenceDatesResult> execute();
}
