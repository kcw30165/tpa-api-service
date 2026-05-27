package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.ReferenceDateCacheUpdateCommand;
import reactor.core.publisher.Mono;

public interface ReferenceDateCacheUpdatePort {

    Mono<Void> updateReferenceDate(ReferenceDateCacheUpdateCommand command);
}